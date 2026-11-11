package com.nexo.smartnotes.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 云端用户。 */
@Serializable
data class ApiUser(
    val id: String,
    val username: String,
    val displayName: String = "",
    val avatarColor: Long = 0xFF7C5CFF,
)

@Serializable
data class ApiShare(
    val userId: String,
    val username: String,
    val displayName: String = "",
    val permission: String = "view",
)

/** 云端笔记。checklist / attachments 为原始 JSON 字符串，便于透传。 */
@Serializable
data class ApiNote(
    val id: String,
    val ownerId: String = "",
    val ownerName: String = "",
    val type: String = "TEXT",
    val title: String = "",
    val content: String = "",
    /** 卡片底色索引（0..8）；服务端历史数据可能是 ARGB，读取时统一兜底 */
    val color: Long = 0,
    val tags: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val remindAt: Long? = null,
    val dueAt: Long? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val location: String? = null,
    val mood: String? = null,
    val weather: String? = null,
    val priority: Int = 0,
    val done: Boolean = false,
    val checklist: String = "[]",
    val attachments: String = "[]",
    val seq: Long = 0,
    val canEdit: Boolean = true,
    val isOwner: Boolean = true,
    val shares: List<ApiShare> = emptyList(),
)

@Serializable
data class ApiComment(
    val id: String,
    val noteId: String,
    val userId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val seq: Long = 0,
)

@Serializable
data class AuthResponse(
    val ok: Boolean = false,
    val token: String = "",
    val user: ApiUser? = null,
    val message: String? = null,
)

@Serializable
data class MeResponse(
    val ok: Boolean = false,
    val user: ApiUser? = null,
    val message: String? = null,
)

@Serializable
data class UsersResponse(
    val ok: Boolean = false,
    val users: List<ApiUser> = emptyList(),
    val message: String? = null,
)

@Serializable
data class ShareResponse(
    val ok: Boolean = false,
    val shares: List<ApiShare> = emptyList(),
    val message: String? = null,
)

@Serializable
data class UploadResponse(
    val ok: Boolean = false,
    val path: String = "",
    val message: String? = null,
)

@Serializable
data class SyncResponse(
    val ok: Boolean = false,
    val serverTime: Long = 0,
    val maxSeq: Long = 0,
    val accepted: List<String> = emptyList(),
    val rejected: List<String> = emptyList(),
    val notes: List<ApiNote> = emptyList(),
    val comments: List<ApiComment> = emptyList(),
    val removed: List<String> = emptyList(),
    val user: ApiUser? = null,
    val message: String? = null,
)

@Serializable
data class SyncRequest(
    val since: Long,
    val notes: List<ApiNote> = emptyList(),
    val comments: List<ApiComment> = emptyList(),
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val displayName: String = "",
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class ShareRequest(
    val noteId: String,
    val username: String,
    val permission: String = "view",
)

@Serializable
data class UploadRequest(
    @SerialName("dataBase64") val dataBase64: String,
)

object ApiJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }
}
