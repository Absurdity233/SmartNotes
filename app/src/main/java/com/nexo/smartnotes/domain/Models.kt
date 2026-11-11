package com.nexo.smartnotes.domain

import com.nexo.smartnotes.R
import kotlinx.serialization.Serializable
import java.util.UUID

/** 笔记类型 —— 每种类型都有独立的编辑与展示形态与 Lucide 图标。 */
enum class NoteType(val id: String, val label: String, val icon: Int) {
    TEXT("TEXT", "笔记", R.drawable.ic_lucide_notebook),
    DIARY("DIARY", "日记", R.drawable.ic_lucide_cloud_sun),
    CHECKLIST("CHECKLIST", "清单", R.drawable.ic_lucide_list_checks),
    TODO("TODO", "待办", R.drawable.ic_lucide_list_todo),
    SCHEDULE("SCHEDULE", "日程", R.drawable.ic_lucide_calendar),
    IMAGE("IMAGE", "图文", R.drawable.ic_lucide_image);

    companion object {
        fun from(id: String?): NoteType = entries.firstOrNull { it.id == id } ?: TEXT
        val creatable: List<NoteType> get() = entries.toList()
    }
}

/** 清单条目（清单 / 待办共用）。 */
@Serializable
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val done: Boolean = false,
    /** 0 普通 / 1 重要 / 2 紧急 */
    val priority: Int = 0,
    val dueAt: Long? = null,
)

/** 图片附件。 */
@Serializable
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    /** 本地缓存文件路径 */
    val localPath: String = "",
    /** 云端相对路径，例如 /uploads/xxx.jpg */
    val remoteUrl: String? = null,
)

/** 分享关系。 */
@Serializable
data class ShareInfo(
    val userId: String,
    val username: String,
    val displayName: String,
    val permission: String = VIEW,
) {
    companion object {
        const val VIEW = "view"
        const val EDIT = "edit"
    }
}

/** 一条评论。 */
data class Comment(
    val id: String,
    val noteId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val dirty: Boolean = false,
)

/** 笔记领域模型。 */
data class Note(
    val id: String,
    val type: NoteType,
    val title: String = "",
    val content: String = "",
    /** 卡片底色索引，对应主题调色板 */
    val colorIndex: Int = 0,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val done: Boolean = false,
    val priority: Int = 0,
    val mood: String? = null,
    val weather: String? = null,
    val location: String? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val checklist: List<ChecklistItem> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val shares: List<ShareInfo> = emptyList(),
    val ownerId: String = "local",
    val ownerName: String? = null,
    /** null 表示自己创建；"edit" / "view" 表示他人分享给我。 */
    val shareRole: String? = null,
    val remoteSeq: Long = 0,
    val dirty: Boolean = true,
) {
    val isOwner: Boolean get() = shareRole == null
    val isSharedWithMe: Boolean get() = shareRole != null
    val canEdit: Boolean get() = shareRole != ShareInfo.VIEW
    val hasChecklist: Boolean get() = type == NoteType.CHECKLIST || type == NoteType.TODO

    val progress: Float?
        get() = if (hasChecklist && checklist.isNotEmpty()) {
            checklist.count { it.done }.toFloat() / checklist.size
        } else null

    val completedCount: Int get() = checklist.count { it.done }

    val displayTitle: String
        get() = title.ifBlank {
            when (type) {
                NoteType.DIARY -> "无题日记"
                NoteType.CHECKLIST -> "待办清单"
                NoteType.TODO -> "待办事项"
                NoteType.SCHEDULE -> "日程安排"
                NoteType.IMAGE -> "图文笔记"
                NoteType.TEXT -> "无标题笔记"
            }
        }

    /** 列表中展示的摘要。 */
    val preview: String
        get() = when (type) {
            NoteType.CHECKLIST, NoteType.TODO -> checklist.take(4)
                .joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.text }
                .ifBlank { content }
            NoteType.IMAGE -> content.ifBlank { "共 ${attachments.size} 张图片" }
            else -> content
        }

    /** 全文检索用的聚合文本（已小写）。 */
    val searchBlob: String
        get() = buildString {
            append(title).append(' ')
            append(content).append(' ')
            append(tags.joinToString(" ")).append(' ')
            append(location.orEmpty()).append(' ')
            append(mood.orEmpty()).append(' ')
            append(weather.orEmpty()).append(' ')
            checklist.forEach { append(it.text).append(' ') }
        }.lowercase()

    fun touched(now: Long = System.currentTimeMillis()): Note = copy(updatedAt = now, dirty = true)

    companion object {
        fun blank(type: NoteType, ownerId: String = "local", ownerName: String? = null): Note {
            val now = System.currentTimeMillis()
            return Note(
                id = UUID.randomUUID().toString(),
                type = type,
                ownerId = ownerId,
                ownerName = ownerName,
                createdAt = now,
                updatedAt = now,
                checklist = if (type == NoteType.CHECKLIST || type == NoteType.TODO) {
                    listOf(ChecklistItem())
                } else emptyList(),
                startAt = if (type == NoteType.SCHEDULE) now else null,
                endAt = if (type == NoteType.SCHEDULE) now + 3_600_000L else null,
            )
        }
    }
}

/** 搜索 / 筛选条件。 */
data class NoteFilter(
    val query: String = "",
    val types: Set<NoteType> = emptySet(),
    val onlyPinned: Boolean = false,
    val onlyShared: Boolean = false,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && types.isEmpty() && !onlyPinned && !onlyShared

    fun matches(note: Note): Boolean {
        if (onlyPinned && !note.pinned) return false
        if (onlyShared && !note.isSharedWithMe) return false
        if (types.isNotEmpty() && note.type !in types) return false
        if (query.isNotBlank()) {
            val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.any { it !in note.searchBlob }) return false
        }
        return true
    }
}
