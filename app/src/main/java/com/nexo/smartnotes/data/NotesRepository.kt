package com.nexo.smartnotes.data

import android.content.Context
import android.net.Uri
import com.nexo.smartnotes.data.local.AppDatabase
import com.nexo.smartnotes.data.local.CommentEntity
import com.nexo.smartnotes.data.local.NoteEntity
import com.nexo.smartnotes.data.local.UserEntity
import com.nexo.smartnotes.data.local.decodeAttachments
import com.nexo.smartnotes.data.local.toDomain
import com.nexo.smartnotes.data.local.toEntity
import com.nexo.smartnotes.data.remote.ApiComment
import com.nexo.smartnotes.data.remote.ApiNote
import com.nexo.smartnotes.data.remote.ApiShare
import com.nexo.smartnotes.data.remote.ApiUser
import com.nexo.smartnotes.data.remote.CloudApi
import com.nexo.smartnotes.data.remote.SyncRequest
import com.nexo.smartnotes.domain.Attachment
import com.nexo.smartnotes.domain.ChecklistItem
import com.nexo.smartnotes.domain.Comment
import com.nexo.smartnotes.domain.LocalUser
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import com.nexo.smartnotes.domain.Session
import com.nexo.smartnotes.domain.ShareInfo
import com.nexo.smartnotes.util.Passwords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 数据仓库：本地 Room 存储 + 云端同步的唯一入口。
 * UI 层只依赖本类，不直接访问数据库或网络。
 */
class NotesRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: Prefs,
    private val api: CloudApi,
) {

    private val noteDao = db.noteDao()
    private val commentDao = db.commentDao()
    private val userDao = db.userDao()

    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    /** 后台同步用的作用域（本地写入后自动触发，无需 UI 关心）。 */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSync: Job? = null

    /**
     * 同步互斥锁。
     *
     * 前台轮询（20s）、本地写入后的防抖同步、用户手动点「同步」三者可能同时触发。
     * 没有这把锁时，同一批 dirty 数据会被并发重复提交，
     * 服务端虽然按 updatedAt 做了幂等，但会浪费流量并让 lastSyncSeq 来回跳。
     */
    private val syncMutex = Mutex()

    /** 本地数据变更后调用：合并短时间内的多次写入，只同步一次。 */
    fun requestSync(delayMillis: Long = 1_500) {
        if (_session.value.user?.cloudToken == null) return
        pendingSync?.cancel()
        pendingSync = syncScope.launch {
            delay(delayMillis)
            syncNow()
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val checklistSerializer = ListSerializer(ChecklistItem.serializer())
    private val attachmentSerializer = ListSerializer(Attachment.serializer())

    /* ------------------------------------------------------------ 数据流 */

    /** 当前账号下所有可见笔记（含他人分享给我的）。 */
    val notes: Flow<List<Note>> = _session.flatMapLatest { session ->
        noteDao.observeActive(session.accountId).map { list -> list.map(NoteEntity::toDomain) }
    }

    val trash: Flow<List<Note>> = _session.flatMapLatest { session ->
        noteDao.observeTrash(session.accountId).map { list -> list.map(NoteEntity::toDomain) }
    }

    val noteCount: Flow<Int> = _session.flatMapLatest { session ->
        noteDao.observeCount(session.accountId)
    }

    fun observeNote(id: String): Flow<Note?> = noteDao.observeById(id).map { it?.toDomain() }

    fun observeComments(noteId: String): Flow<List<Comment>> =
        commentDao.observeForNote(noteId).map { list -> list.map(CommentEntity::toDomain) }

    /* ------------------------------------------------------------ 会话 */

    suspend fun restoreSession() = withContext(Dispatchers.IO) {
        val serverUrl = prefs.serverUrlOnce()
        val userId = prefs.currentUserIdOnce()
        val user = userId?.let { userDao.findById(it) }?.toDomain()
        _session.value = Session(
            user = user,
            serverUrl = user?.serverUrl ?: serverUrl,
            lastSyncAt = user?.lastSyncAt ?: 0,
        )
    }

    suspend fun setServerUrl(url: String) = withContext(Dispatchers.IO) {
        val clean = url.trim().trimEnd('/')
        prefs.setServerUrl(clean)
        val user = _session.value.user
        if (user != null) {
            userDao.findById(user.id)?.let { userDao.upsert(it.copy(serverUrl = clean)) }
        }
        _session.value = _session.value.copy(serverUrl = clean)
    }

    /** 游客模式：不登录也能使用全部本地功能。 */
    suspend fun continueAsGuest() = withContext(Dispatchers.IO) {
        prefs.setCurrentUser(null)
        _session.value = Session(serverUrl = _session.value.serverUrl)
    }

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
    ): Result<LocalUser> = withContext(Dispatchers.IO) {
        runCatching {
            val name = username.trim().lowercase()
            require(name.matches(Regex("^[a-z0-9_]{3,20}$"))) { "用户名需为 3-20 位字母、数字或下划线" }
            require(password.length >= 6) { "密码至少 6 位" }
            require(userDao.findByUsername(name) == null) { "本机已存在该用户名" }

            // 优先注册云端；云端不可用时降级为纯本地账号，保证离线可用
            var remote: ApiUser? = null
            var token: String? = null
            val cloudError = runCatching {
                val res = api.register(name, password, displayName.ifBlank { name })
                if (!res.ok) error(res.message ?: "注册失败")
                remote = res.user
                token = res.token
            }.exceptionOrNull()

            if (cloudError != null && cloudError.message?.contains("已被注册") == true) {
                throw IllegalStateException("该用户名已被注册，请换一个")
            }

            val salt = Passwords.newSalt()
            val entity = UserEntity(
                id = remote?.id ?: UUID.randomUUID().toString(),
                username = name,
                displayName = displayName.ifBlank { name },
                avatarColor = AVATAR_COLORS.random(),
                passwordHash = Passwords.hash(password, salt),
                salt = salt,
                createdAt = System.currentTimeMillis(),
                remoteId = remote?.id,
                token = token,
                serverUrl = _session.value.serverUrl,
            )
            userDao.upsert(entity)
            // 游客期间创建的笔记并入新账号（仅当没有被别人占用时，见 adoptGuestNotes）
            adoptGuestNotes(entity.id)
            prefs.setCurrentUser(entity.id)
            prefs.setLastUser(entity.id)
            _session.value = Session(
                user = entity.toDomain(),
                serverUrl = entity.serverUrl ?: _session.value.serverUrl,
            )
            entity.toDomain()
        }
    }

    suspend fun login(username: String, password: String): Result<LocalUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = username.trim().lowercase()
                val local = userDao.findByUsername(name)

                // 先尝试云端登录（账号可能在别的设备注册）
                val cloud = runCatching { api.login(name, password) }.getOrNull()
                if (cloud != null && cloud.ok && cloud.user != null) {
                    val remoteUser = cloud.user!!
                    val existing = userDao.findByRemoteId(remoteUser.id) ?: local
                    val salt = existing?.salt ?: Passwords.newSalt()
                    val entity = UserEntity(
                        id = remoteUser.id,
                        username = remoteUser.username,
                        displayName = remoteUser.displayName.ifBlank { name },
                        avatarColor = remoteUser.avatarColor,
                        passwordHash = existing?.passwordHash ?: Passwords.hash(password, salt),
                        salt = salt,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        remoteId = remoteUser.id,
                        token = cloud.token,
                        serverUrl = _session.value.serverUrl,
                        lastSyncSeq = existing?.lastSyncSeq ?: 0,
                        lastSyncAt = existing?.lastSyncAt ?: 0,
                    )
                    // 若本机曾用本地账号（离线注册）使用过，则把数据迁移到云端账号下
                    if (local != null && local.id != entity.id) {
                        noteDao.moveAccount(local.id, entity.id)
                        userDao.delete(local.id)
                    }
                    userDao.upsert(entity)
                    adoptGuestNotes(entity.id)
                    prefs.setCurrentUser(entity.id)
                    prefs.setLastUser(entity.id)
                    _session.value = Session(
                        user = entity.toDomain(),
                        serverUrl = entity.serverUrl ?: _session.value.serverUrl,
                    )
                    return@runCatching entity.toDomain()
                }

                // 回退本地账号
                val localUser = local ?: error(cloud?.message ?: "用户名或密码错误")
                require(Passwords.verify(password, localUser.passwordHash, localUser.salt)) {
                    "用户名或密码错误"
                }
                noteDao.adoptNotes(Session.GUEST, localUser.id)
                prefs.setCurrentUser(localUser.id)
                prefs.setLastUser(localUser.id)
                _session.value = Session(
                    user = localUser.toDomain(),
                    serverUrl = localUser.serverUrl ?: _session.value.serverUrl,
                )
                localUser.toDomain()
            }
        }

    /**
     * 把游客区（accountId = "local"）的笔记并入 [targetUserId]。
     *
     * 之前是无条件并入「下一个登录的人」，在多用户场景下是真实的数据越界：
     * A 退出后以游客身份写了几条笔记，B 登录会把它们带走。
     *
     * 现在的判定：
     * - 没有历史登录记录（首次使用就写了游客笔记）→ 视为本机新用户的笔记，允许并入；
     * - 有历史登录记录且与 [targetUserId] 相同 → 是本人退出后的补充记录，允许并入；
     * - 有历史登录记录但属于另一个人 → 不动，留在游客区等本人回来认领。
     */
    private suspend fun adoptGuestNotes(targetUserId: String) {
        val lastUser = prefs.lastUserIdOnce()
        val allowed = lastUser == null || lastUser == targetUserId
        if (allowed) noteDao.adoptNotes(Session.GUEST, targetUserId)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        // 记住是谁退出的，供 adoptGuestNotes 判断游客笔记的归属
        _session.value.user?.id?.let { prefs.setLastUser(it) }
        prefs.setCurrentUser(null)
        _session.value = Session(serverUrl = _session.value.serverUrl)
    }

    /* ------------------------------------------------------------ 笔记 */

    /** 新建笔记（仅构造对象，保存时才落库，避免用户放弃编辑后留下空笔记）。 */
    suspend fun createNote(type: NoteType): Note = withContext(Dispatchers.IO) {
        val session = _session.value
        val ownerId = session.user?.id ?: Session.GUEST
        Note.blank(type, ownerId, session.user?.displayName)
    }

    suspend fun saveNote(note: Note): Unit = withContext(Dispatchers.IO) {
        val session = _session.value
        val existing = noteDao.findById(note.id)
        val accountId = existing?.accountId ?: session.accountId
        val ownerId = existing?.ownerId ?: note.ownerId
        val ownerName = existing?.ownerName ?: note.ownerName
        noteDao.upsert(
            note.copy(ownerId = ownerId, ownerName = ownerName)
                .touched()
                .toEntity(accountId)
        )
        requestSync()
    }

    suspend fun getNote(id: String): Note? = withContext(Dispatchers.IO) {
        noteDao.findById(id)?.toDomain()
    }

    suspend fun togglePin(id: String) = withContext(Dispatchers.IO) {
        noteDao.findById(id)?.let { entity ->
            val note = entity.toDomain()
            noteDao.upsert(note.copy(pinned = !note.pinned).touched().toEntity(entity.accountId))
            requestSync()
        }
    }

    suspend fun setArchived(id: String, archived: Boolean) = withContext(Dispatchers.IO) {
        noteDao.findById(id)?.let { entity ->
            val note = entity.toDomain()
            noteDao.upsert(note.copy(archived = archived).touched().toEntity(entity.accountId))
            requestSync()
        }
    }

    suspend fun setReminder(id: String, at: Long?) = withContext(Dispatchers.IO) {
        noteDao.findById(id)?.let { entity ->
            val note = entity.toDomain()
            noteDao.upsert(note.copy(reminderAt = at).touched().toEntity(entity.accountId))
            requestSync()
        }
    }

    suspend fun moveToTrash(id: String) = withContext(Dispatchers.IO) {
        noteDao.findById(id)?.let { entity ->
            val note = entity.toDomain()
            noteDao.upsert(note.copy(trashed = true).touched().toEntity(entity.accountId))
            requestSync()
        }
    }

    suspend fun restoreFromTrash(id: String) = withContext(Dispatchers.IO) {
        noteDao.findById(id)?.let { entity ->
            val note = entity.toDomain()
            noteDao.upsert(note.copy(trashed = false).touched().toEntity(entity.accountId))
            requestSync()
        }
    }

    suspend fun deleteForever(id: String) = withContext(Dispatchers.IO) {
        noteDao.findById(id)?.let { entity ->
            noteDao.upsert(entity.toDomain().copy(trashed = true).touched().toEntity(entity.accountId))
            // 尽力同步一次，让云端也删除；离线时不影响本地删除
            runCatching { syncNow() }
            noteDao.deleteById(id)
        }
    }

    suspend fun duplicate(id: String): Note? = withContext(Dispatchers.IO) {
        val entity = noteDao.findById(id) ?: return@withContext null
        val now = System.currentTimeMillis()
        val copy = entity.toDomain().copy(
            id = UUID.randomUUID().toString(),
            title = entity.title + " 副本",
            createdAt = now,
            updatedAt = now,
            pinned = false,
            shares = emptyList(),
            remoteSeq = 0,
            dirty = true,
        )
        noteDao.upsert(copy.toEntity(entity.accountId))
        requestSync()
        copy
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val session = _session.value
        val trashed = noteDao.allNotes(session.accountId).filter { it.trashed }
        // 先尽力同步删除标记，再清理本地
        if (trashed.isNotEmpty() && session.user?.cloudToken != null) {
            runCatching { syncNow() }
        }
        trashed.forEach { noteDao.deleteById(it.id) }
    }

    suspend fun toggleChecklistItem(noteId: String, itemId: String) = withContext(Dispatchers.IO) {
        noteDao.findById(noteId)?.let { entity ->
            val note = entity.toDomain()
            val updated = note.copy(
                checklist = note.checklist.map {
                    if (it.id == itemId) it.copy(done = !it.done) else it
                }
            )
            noteDao.upsert(updated.touched().toEntity(entity.accountId))
            requestSync()
        }
    }

    /* ------------------------------------------------------------ 评论 */

    suspend fun addComment(noteId: String, text: String) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        val user = _session.value.user
        commentDao.upsert(
            CommentEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                authorId = user?.id ?: Session.GUEST,
                authorName = user?.displayName ?: "游客",
                text = text.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                dirty = true,
                serverSeq = 0,
            )
        )
        requestSync()
    }

    suspend fun deleteComment(id: String) = withContext(Dispatchers.IO) {
        commentDao.findById(id)?.let {
            commentDao.upsert(it.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis()))
            requestSync()
        }
    }

    /* ------------------------------------------------------------ 图片 */

    /** 将选中的图片复制到应用私有目录，返回附件对象。 */
    suspend fun importImage(uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取图片")
            val ext = when (sniffMime(bytes)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val file = File(dir, "${UUID.randomUUID()}.$ext")
            file.writeBytes(bytes)
            Attachment(localPath = file.absolutePath)
        }.getOrNull()
    }

    /* ------------------------------------------------------------ 分享 */

    suspend fun shareNote(noteId: String, username: String, permission: String): Result<List<ShareInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = _session.value.user?.cloudToken ?: error("请先登录云端账号后再分享")
                val shares = api.share(token, noteId, username, permission)
                noteDao.findById(noteId)?.let { entity ->
                    val note = entity.toDomain()
                    noteDao.upsert(note.copy(shares = shares.map(::toShareInfo)).toEntity(entity.accountId))
                }
                shares.map(::toShareInfo)
            }
        }

    suspend fun unshareNote(noteId: String, userId: String): Result<List<ShareInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = _session.value.user?.cloudToken ?: error("请先登录云端账号")
                val shares = api.unshare(token, noteId, userId)
                noteDao.findById(noteId)?.let { entity ->
                    val note = entity.toDomain()
                    noteDao.upsert(note.copy(shares = shares.map(::toShareInfo)).toEntity(entity.accountId))
                }
                shares.map(::toShareInfo)
            }
        }

    suspend fun searchUsers(query: String): Result<List<ApiUser>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = _session.value.user?.cloudToken ?: error("请先登录云端账号")
            api.searchUsers(token, query)
        }
    }

    suspend fun checkServer(): Boolean = api.health()

    /* ------------------------------------------------------------ 同步 */

    suspend fun syncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = _session.value.user
        val token = user?.cloudToken
        if (token == null) {
            return@withContext Result.failure(IllegalStateException("请先登录云端账号"))
        }
        // 串行化：前台轮询 / 写入防抖 / 手动同步可能同时触发，
        // 并发执行会把同一批 dirty 数据重复提交，这里统一排队。
        syncMutex.withLock { performSync(user, token) }
    }

    private suspend fun performSync(
        user: com.nexo.smartnotes.domain.LocalUser,
        token: String,
    ): Result<Unit> {
        _session.value = _session.value.copy(syncing = true, lastSyncError = null)

        return runCatching {
            val accountId = user.id
            val dirty = noteDao.dirtyNotes(accountId)
            val dirtyComments = commentDao.dirtyComments()

            // 上传本地图片（仅首次）
            val outgoing = dirty.map { entity ->
                val note = entity.toDomain()
                val attachments = note.attachments.map { att ->
                    if (att.remoteUrl != null) att else uploadAttachment(token, att)
                }
                if (attachments != note.attachments) {
                    noteDao.upsert(
                        note.copy(attachments = attachments, dirty = true).toEntity(entity.accountId)
                    )
                }
                note.copy(attachments = attachments).toApiNote()
            }

            val response = api.sync(
                token,
                SyncRequest(
                    since = user.lastSyncSeq,
                    notes = outgoing,
                    comments = dirtyComments.map { it.toApiComment() },
                ),
            )
            if (!response.ok) error(response.message ?: "同步失败")

            applyRemoteNotes(response, accountId)
            response.removed.forEach { noteDao.deleteById(it) }
            response.comments.forEach { remote ->
                val existing = commentDao.findById(remote.id)
                if (existing == null || !existing.dirty) {
                    commentDao.upsert(remote.toEntity())
                }
            }
            response.accepted.forEach { id -> noteDao.markSynced(id, response.maxSeq) }

            userDao.updateSyncState(accountId, response.maxSeq, response.serverTime)
            val refreshed = userDao.findById(accountId)?.toDomain()
            _session.value = _session.value.copy(
                user = refreshed ?: user.copy(lastSyncSeq = response.maxSeq, lastSyncAt = response.serverTime),
                syncing = false,
                lastSyncAt = response.serverTime,
                lastSyncError = null,
            )
        }.onFailure { err ->
            _session.value = _session.value.copy(
                syncing = false,
                lastSyncError = err.message ?: "同步失败",
            )
        }
    }
    private suspend fun uploadAttachment(token: String, att: Attachment): Attachment {
        val file = File(att.localPath)
        if (!file.exists()) return att
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return att
        val mime = sniffMime(bytes)
        val path = runCatching { api.uploadImage(token, bytes, mime) }.getOrNull() ?: return att
        return att.copy(remoteUrl = path)
    }

    /** 依据文件头判断图片类型（服务端按 mime 决定扩展名）。 */
    private fun sniffMime(bytes: ByteArray): String = when {
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
        bytes.size > 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "image/webp"
        bytes.size > 6 && bytes[0] == 'G'.code.toByte() -> "image/gif"
        else -> "image/jpeg"
    }

    private suspend fun applyRemoteNotes(response: com.nexo.smartnotes.data.remote.SyncResponse, accountId: String) {
        for (remote in response.notes) {
            // 单条数据异常不影响整体同步
            runCatching {
                val existing = noteDao.findById(remote.id)
                // 本地有未同步的更新且更新时间更晚 → 保留本地，等下一轮再推
                val keepLocal = existing != null && existing.dirty && existing.updatedAt > remote.updatedAt
                if (keepLocal) return@runCatching

                val shareRole = when {
                    remote.isOwner -> null
                    remote.canEdit -> ShareInfo.EDIT
                    else -> ShareInfo.VIEW
                }
                val entity = remote.toNote(accountId, shareRole)
                // 图片路径处理：本地已有的优先用本地文件，否则把相对路径拼成可访问的完整地址
                val localPaths = existing
                    ?.let { decodeAttachments(it.attachmentsJson) }
                    .orEmpty()
                    .associateBy { it.id }
                val base = _session.value.serverUrl.trimEnd('/')
                val attachments = decodeAttachments(entity.attachmentsJson).map { att ->
                    val local = localPaths[att.id]
                    val absoluteUrl = att.remoteUrl?.takeIf { it.startsWith("/") }?.let { base + it }
                        ?: att.remoteUrl
                    when {
                        local != null && File(local.localPath).exists() ->
                            att.copy(localPath = local.localPath, remoteUrl = absoluteUrl)
                        else -> att.copy(localPath = "", remoteUrl = absoluteUrl)
                    }
                }
                noteDao.upsert(
                    entity.copy(attachmentsJson = json.encodeToString(attachmentSerializer, attachments))
                )
            }
        }
    }

    /* ------------------------------------------------------------ 转换 */

    private fun Note.toApiNote(): ApiNote = ApiNote(
        id = id,
        ownerId = ownerId,
        ownerName = ownerName.orEmpty(),
        type = type.id,
        title = title,
        content = content,
        color = colorIndex.toLong(),
        tags = tags.joinToString(","),
        pinned = pinned,
        archived = archived,
        deleted = trashed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        remindAt = reminderAt,
        dueAt = dueAt,
        startAt = startAt,
        endAt = endAt,
        location = location,
        mood = mood,
        weather = weather,
        priority = priority,
        done = done,
        checklist = json.encodeToString(checklistSerializer, checklist),
        attachments = json.encodeToString(attachmentSerializer, attachments),
    )

    private fun ApiNote.toNote(accountId: String, shareRole: String?): NoteEntity =
        Note(
            id = id,
            type = NoteType.from(type),
            title = title,
            content = content,
            colorIndex = color.toInt().let { if (it in 0..8) it else 0 },
            tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            pinned = pinned,
            archived = archived,
            trashed = deleted,
            done = done,
            priority = priority,
            mood = mood,
            weather = weather,
            location = location,
            startAt = startAt,
            endAt = endAt,
            dueAt = dueAt,
            reminderAt = remindAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            checklist = runCatching {
                json.decodeFromString(checklistSerializer, checklist)
            }.getOrDefault(emptyList()),
            attachments = runCatching {
                json.decodeFromString(attachmentSerializer, attachments)
            }.getOrDefault(emptyList()),
            shares = shares.map(::toShareInfo),
            ownerId = ownerId,
            ownerName = ownerName,
            shareRole = shareRole,
            remoteSeq = seq,
            dirty = false,
        ).toEntity(accountId)

    private fun CommentEntity.toApiComment(): ApiComment = ApiComment(
        id = id,
        noteId = noteId,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
    )

    private fun ApiComment.toEntity(): CommentEntity = CommentEntity(
        id = id,
        noteId = noteId,
        authorId = userId,
        authorName = authorName,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        dirty = false,
        serverSeq = seq,
    )

    private fun toShareInfo(s: ApiShare): ShareInfo =
        ShareInfo(s.userId, s.username, s.displayName, s.permission)

    companion object {
        private val AVATAR_COLORS = listOf(
            0xFF6C5CE7, 0xFF00B894, 0xFFE17055, 0xFF0984E3,
            0xFFE84393, 0xFFFDCB6E, 0xFF00CEC9, 0xFFD63031,
        )
    }
}

private fun CommentEntity.toDomain() = Comment(
    id = id,
    noteId = noteId,
    authorId = authorId,
    authorName = authorName,
    text = text,
    createdAt = createdAt,
    updatedAt = updatedAt,
    dirty = dirty,
)
