package com.nexo.smartnotes.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.smartnotes.domain.LocalUser

/**
 * 本机账号。支持离线注册（[remoteId] 为空），之后可绑定云端账号开启同步。
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)],
)
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val avatarColor: Long,
    val passwordHash: String,
    val salt: String,
    val createdAt: Long,
    val remoteId: String? = null,
    val token: String? = null,
    val serverUrl: String? = null,
    val lastSyncSeq: Long = 0,
    val lastSyncAt: Long = 0,
) {
    fun toDomain(): LocalUser = LocalUser(
        id = id,
        username = username,
        displayName = displayName,
        avatarColor = avatarColor,
        cloudUserId = remoteId,
        cloudToken = token,
        serverUrl = serverUrl,
        lastSyncSeq = lastSyncSeq,
        lastSyncAt = lastSyncAt,
    )
}
