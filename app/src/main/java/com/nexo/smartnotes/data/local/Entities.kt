package com.nexo.smartnotes.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.smartnotes.domain.Attachment
import com.nexo.smartnotes.domain.ChecklistItem
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteType
import com.nexo.smartnotes.domain.ShareInfo
import kotlinx.serialization.json.Json

internal val noteJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

internal fun decodeChecklist(raw: String?): List<ChecklistItem> =
    if (raw.isNullOrBlank()) emptyList()
    else runCatching { noteJson.decodeFromString<List<ChecklistItem>>(raw) }.getOrDefault(emptyList())

internal fun decodeAttachments(raw: String?): List<Attachment> =
    if (raw.isNullOrBlank()) emptyList()
    else runCatching { noteJson.decodeFromString<List<Attachment>>(raw) }.getOrDefault(emptyList())

internal fun decodeShares(raw: String?): List<ShareInfo> =
    if (raw.isNullOrBlank()) emptyList()
    else runCatching { noteJson.decodeFromString<List<ShareInfo>>(raw) }.getOrDefault(emptyList())

internal fun encodeChecklist(items: List<ChecklistItem>): String =
    runCatching { noteJson.encodeToString(items) }.getOrDefault("[]")

internal fun encodeAttachments(items: List<Attachment>): String =
    runCatching { noteJson.encodeToString(items) }.getOrDefault("[]")

internal fun encodeShares(items: List<ShareInfo>): String =
    runCatching { noteJson.encodeToString(items) }.getOrDefault("[]")

/**
 * 笔记表。
 * [accountId] 数据分区：游客为 "local"，登录后为本地用户 id。
 * [shareRole] 非空表示他人分享给我的笔记（[ownerId] 为原作者）。
 * [serverSeq] 服务器版本号，用于增量同步。
 */
@Entity(
    tableName = "notes",
    indices = [
        Index("accountId"),
        Index("type"),
        Index("updatedAt"),
        Index("dirty"),
        // v2 新增：首页/归档/回收站最常用的组合条件
        Index(value = ["accountId", "trashed", "archived"]),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val ownerId: String,
    val ownerName: String?,
    val type: String,
    val title: String,
    val content: String,
    /**
     * 小写的全字段检索串（标题+正文+标签+地点+心情+天气+清单条目）。
     *
     * 之前搜索是「把整个账号的笔记读进内存再过滤」，几千条就明显卡顿。
     * 现在把它落库，用 SQL LIKE 直接在数据库里筛，只把命中结果带回来。
     */
    val searchBlob: String,
    val colorIndex: Int,
    val tags: String,
    val pinned: Boolean,
    val archived: Boolean,
    val trashed: Boolean,
    val done: Boolean,
    val priority: Int,
    val mood: String?,
    val weather: String?,
    val location: String?,
    val startAt: Long?,
    val endAt: Long?,
    val dueAt: Long?,
    val reminderAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val checklistJson: String,
    val attachmentsJson: String,
    val sharesJson: String,
    val shareRole: String?,
    val dirty: Boolean,
    val serverSeq: Long,
)

/** 笔记评论（协作）。 */
@Entity(
    tableName = "comments",
    indices = [Index("noteId"), Index("dirty")],
)
data class CommentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val dirty: Boolean,
    val serverSeq: Long,
)

/* ------------------------------------------------------------------ 映射 */

fun NoteEntity.toDomain(): Note = Note(    id = id,
    type = NoteType.from(type),
    title = title,
    content = content,
    colorIndex = colorIndex,
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    pinned = pinned,
    archived = archived,
    trashed = trashed,
    done = done,
    priority = priority,
    mood = mood,
    weather = weather,
    location = location,
    startAt = startAt,
    endAt = endAt,
    dueAt = dueAt,
    reminderAt = reminderAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    checklist = decodeChecklist(checklistJson),
    attachments = decodeAttachments(attachmentsJson),
    shares = decodeShares(sharesJson),
    ownerId = ownerId,
    ownerName = ownerName,
    shareRole = shareRole,
    remoteSeq = serverSeq,
    dirty = dirty,
)

fun Note.toEntity(accountId: String): NoteEntity = NoteEntity(
    id = id,
    accountId = accountId,
    ownerId = ownerId,
    ownerName = ownerName,
    type = type.id,
    title = title,
    content = content,
    searchBlob = searchBlob,
    colorIndex = colorIndex,
    tags = tags.joinToString(","),
    pinned = pinned,
    archived = archived,
    trashed = trashed,
    done = done,
    priority = priority,
    mood = mood,
    weather = weather,
    location = location,
    startAt = startAt,
    endAt = endAt,
    dueAt = dueAt,
    reminderAt = reminderAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    checklistJson = encodeChecklist(checklist),
    attachmentsJson = encodeAttachments(attachments),
    sharesJson = encodeShares(shares),
    shareRole = shareRole,
    dirty = dirty,
    serverSeq = remoteSeq,
)
