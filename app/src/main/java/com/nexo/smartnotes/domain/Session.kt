package com.nexo.smartnotes.domain

import com.nexo.smartnotes.BuildConfig

/** 登录账号。游客模式下 [Session.user] 为 null。 */
data class LocalUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarColor: Long,
    val cloudUserId: String? = id,
    val cloudToken: String? = null,
    val serverUrl: String? = null,
    val lastSyncSeq: Long = 0,
    val lastSyncAt: Long = 0,
) {
    val cloudLinked: Boolean get() = cloudToken != null
}

/** 当前会话状态。 */
data class Session(
    val user: LocalUser? = null,
    val serverUrl: String = DEFAULT_SERVER,
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastSyncError: String? = null,
) {
    val accountId: String get() = user?.id ?: GUEST
    val isGuest: Boolean get() = user == null

    companion object {
        const val GUEST = "local"

        /**
         * 默认云服务地址。
         *
         * 构建时可用 `-Psmartnotes.server=https://xxx` 覆盖（见 app/build.gradle.kts 的
         * SMARTSERVER 字段），这样公网演示包不必每次手填地址；
         * 未指定时回落到模拟器访问宿主机的固定地址。
         */
        val DEFAULT_SERVER: String = BuildConfig.SMARTNOTES_SERVER.ifBlank { "http://10.0.2.2:8787" }
    }
}
