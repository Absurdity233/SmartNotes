package com.nexo.smartnotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.smartnotes.SmartNotesApp
import com.nexo.smartnotes.domain.LocalUser
import com.nexo.smartnotes.domain.Session
import com.nexo.smartnotes.util.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 应用级状态：登录会话、同步、主题。 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmartNotesApp.instance.repository
    private val prefs = SmartNotesApp.instance.prefs

    val session: StateFlow<Session> = repository.session

    private val _booting = MutableStateFlow(true)
    val booting: StateFlow<Boolean> = _booting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val themeMode: StateFlow<String> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    val gridLayout: StateFlow<Boolean> = prefs.gridLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoSync: StateFlow<Boolean> = prefs.autoSync
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            repository.restoreSession()
            // 重新排定未来提醒（应用重启后 AlarmManager 中的闹钟可能已丢失）
            ReminderScheduler.rescheduleAll(getApplication())
            _booting.value = false
            // 已登录云端账号则自动同步一次
            val session = repository.session.value
            if (session.user?.cloudToken != null && prefs.autoSyncOnce()) {
                repository.syncNow()
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            repository.login(username, password)
                .onSuccess { user ->
                    _message.value = "欢迎回来，${user.displayName}"
                    if (user.cloudToken != null) repository.syncNow()
                }
                .onFailure { _message.value = it.message ?: "登录失败" }
        }
    }

    fun register(username: String, password: String, displayName: String) {
        viewModelScope.launch {
            repository.register(username, password, displayName)
                .onSuccess { user ->
                    _message.value = "账号创建成功，欢迎 ${user.displayName}"
                    if (user.cloudToken != null) repository.syncNow()
                }
                .onFailure { _message.value = it.message ?: "注册失败" }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            repository.continueAsGuest()
            _message.value = "已进入游客模式，数据仅保存在本机"
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _message.value = "已退出登录"
        }
    }

    fun sync() {
        viewModelScope.launch {
            repository.syncNow()
                .onSuccess { _message.value = "同步完成" }
                .onFailure { _message.value = it.message ?: "同步失败" }
        }
    }

    /**
     * 前台保活时的增量拉取。
     *
     * 之前只有「启动应用」与「手动点同步」会拉云端，导致协作者在别处改完内容后，
     * 本机界面一直停在旧数据上。这里在应用可见期间按固定间隔静默拉取，
     * 失败不打扰用户（云端不可用时应静默降级，不能弹「同步失败」刷屏）。
     */
    private var foregroundJob: Job? = null
    private var lastForegroundSyncAt = 0L

    fun onEnterForeground() {
        val user = repository.session.value.user ?: return
        if (user.cloudToken == null) return
        // 距上次拉取不足 3 秒则跳过，避免 ON_START 与 init 重复请求
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncAt < 3_000) return
        lastForegroundSyncAt = now

        viewModelScope.launch {
            if (!prefs.autoSyncOnce()) return@launch
            repository.syncNow()
        }

        if (foregroundJob?.isActive == true) return
        foregroundJob = viewModelScope.launch {
            while (true) {
                delay(FOREGROUND_SYNC_INTERVAL)
                // 会话已退出或关闭自动同步时停止轮询
                val current = repository.session.value.user
                if (current?.cloudToken == null || !prefs.autoSyncOnce()) break
                repository.syncNow()
            }
        }
    }

    fun onLeaveForeground() {
        foregroundJob?.cancel()
        foregroundJob = null
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            repository.setServerUrl(url)
            _message.value = "云服务地址已更新"
        }
    }

    fun checkServer() {
        viewModelScope.launch {
            val ok = repository.checkServer()
            _message.value = if (ok) "云服务连接正常 ✓" else "无法连接云服务，请检查地址与网络"
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setGridLayout(enabled: Boolean) {
        viewModelScope.launch { prefs.setGridLayout(enabled) }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoSync(enabled) }
    }

    fun currentUser(): LocalUser? = repository.session.value.user

    companion object {
        /** 前台轮询间隔：兼顾协作实时性与耗电。 */
        private const val FOREGROUND_SYNC_INTERVAL = 20_000L
    }
}
