package com.nexo.smartnotes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexo.smartnotes.domain.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smartnotes_prefs")

/** 轻量偏好：当前登录用户、服务器地址、主题、布局。 */
class Prefs(private val context: Context) {

    private object Keys {
        val currentUserId = stringPreferencesKey("current_user_id")
        val lastUserId = stringPreferencesKey("last_user_id")
        val serverUrl = stringPreferencesKey("server_url")
        val themeMode = stringPreferencesKey("theme_mode")
        val autoSync = booleanPreferencesKey("auto_sync")
        val gridLayout = booleanPreferencesKey("grid_layout")
    }

    /**
     * 上一个登录过的账号 id。
     *
     * 用于判断「游客模式下新建的笔记该归谁」：退出登录后进入游客模式创建的笔记，
     * 语义上属于刚退出的那个人，而不是下一个登录的人。
     */
    val lastUserId: Flow<String?> = context.dataStore.data.map { it[Keys.lastUserId] }

    suspend fun lastUserIdOnce(): String? = lastUserId.first()

    suspend fun setLastUser(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.lastUserId) else prefs[Keys.lastUserId] = id
        }
    }

    val currentUserId: Flow<String?> = context.dataStore.data.map { it[Keys.currentUserId] }
    val serverUrl: Flow<String> = context.dataStore.data.map { it[Keys.serverUrl] ?: Session.DEFAULT_SERVER }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.themeMode] ?: "system" }
    val autoSync: Flow<Boolean> = context.dataStore.data.map { it[Keys.autoSync] ?: true }
    val gridLayout: Flow<Boolean> = context.dataStore.data.map { it[Keys.gridLayout] ?: true }

    suspend fun currentUserIdOnce(): String? = currentUserId.first()
    suspend fun serverUrlOnce(): String = serverUrl.first()
    suspend fun autoSyncOnce(): Boolean = autoSync.first()

    /** 供 OkHttp 拦截器在非挂起上下文读取。 */
    fun serverUrlBlocking(): String = runBlocking { serverUrlOnce() }

    suspend fun setCurrentUser(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.currentUserId) else prefs[Keys.currentUserId] = id
        }
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.serverUrl] = url.trim().trimEnd('/') }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.themeMode] = mode }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoSync] = enabled }
    }

    suspend fun setGridLayout(enabled: Boolean) {
        context.dataStore.edit { it[Keys.gridLayout] = enabled }
    }
}
