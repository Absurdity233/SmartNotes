package com.nexo.smartnotes.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.nexo.smartnotes.ui.components.Lucide
import com.nexo.smartnotes.ui.components.LucideIcon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.smartnotes.ui.AppViewModel
import com.nexo.smartnotes.ui.components.UserAvatar
import com.nexo.smartnotes.util.DateFormats

@Composable
fun AccountScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    val session by appVm.session.collectAsStateWithLifecycle()
    val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
    val autoSync by appVm.autoSync.collectAsStateWithLifecycle()
    val grid by appVm.gridLayout.collectAsStateWithLifecycle()

    var showServerDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("我的", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        LucideIcon(Lucide.ArrowLeft, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 账号卡片
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            name = session.user?.displayName ?: "游客",
                            color = session.user?.let { Color(it.avatarColor) } ?: MaterialTheme.colorScheme.primary,
                            size = 56,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.user?.displayName ?: "游客模式",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (session.isGuest) "登录后可跨设备同步" else "@${session.user?.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (session.isGuest) {
                            TextButton(onClick = onOpenAuth) { Text("登录") }
                        }
                    }

                    if (!session.isGuest) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LucideIcon(if (session.user?.cloudToken != null) Lucide.CloudCheck else Lucide.CloudOff,
                                null,
                                tint = if (session.user?.cloudToken != null) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (session.user?.cloudToken != null) "云端同步已开启" else "离线账号",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    if (session.lastSyncAt > 0) {
                                        "上次同步 " + DateFormats.relative(session.lastSyncAt)
                                    } else {
                                        "尚未同步"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { appVm.sync() }, enabled = !session.syncing) {
                                LucideIcon(Lucide.Refresh,
                                    "同步",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        session.lastSyncError?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsGroup("外观") {
                SettingRow(
                    icon = { LucideIcon(if (themeMode == "dark") Lucide.Moon else Lucide.Sun, null) },
                    title = "主题",
                    subtitle = when (themeMode) {
                        "light" -> "浅色"
                        "dark" -> "深色"
                        else -> "跟随系统"
                    },
                    onClick = {
                        appVm.setThemeMode(
                            when (themeMode) {
                                "system" -> "light"
                                "light" -> "dark"
                                else -> "system"
                            }
                        )
                    },
                )
                SettingRow(
                    icon = { LucideIcon(Lucide.Settings, null) },
                    title = "卡片布局",
                    subtitle = if (grid) "卡片视图" else "列表视图",
                    trailing = {
                        Switch(checked = grid, onCheckedChange = { appVm.setGridLayout(it) })
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup("数据") {
                SettingRow(
                    icon = { LucideIcon(Lucide.CloudUpload, null) },
                    title = "自动同步",
                    subtitle = "启动应用时自动与云端同步",
                    trailing = { Switch(checked = autoSync, onCheckedChange = { appVm.setAutoSync(it) }) },
                )
                SettingRow(
                    icon = { LucideIcon(Lucide.Settings, null) },
                    title = "云服务地址",
                    subtitle = session.serverUrl,
                    onClick = { showServerDialog = true },
                )
                SettingRow(
                    icon = { LucideIcon(Lucide.Archive, null) },
                    title = "归档",
                    subtitle = "收纳不常用但想保留的笔记",
                    onClick = onOpenArchive,
                )
                SettingRow(
                    icon = { LucideIcon(Lucide.Trash, null) },
                    title = "回收站",
                    subtitle = "恢复或彻底删除笔记",
                    onClick = onOpenTrash,
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup("关于") {
                SettingRow(
                    icon = { LucideIcon(Lucide.Info, null) },
                    title = "智趣笔记",
                    subtitle = "版本 1.0.0 · 课程项目",
                    onClick = {},
                )
            }

            if (!session.isGuest) {
                Spacer(Modifier.height(20.dp))
                Surface(
                    onClick = { showLogoutConfirm = true },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LucideIcon(Lucide.Logout,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "退出登录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showServerDialog) {
        var url by remember { mutableStateOf(session.serverUrl) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("云服务地址") },
            text = {
                Column {
                    Text(
                        "模拟器使用 http://10.0.2.2:8787\n真机请填写电脑的局域网 IP，例如 http://192.168.1.10:8787",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    appVm.setServerUrl(url)
                    appVm.checkServer()
                    showServerDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { appVm.checkServer() }) { Text("测试连接") }
                    TextButton(onClick = { showServerDialog = false }) { Text("取消") }
                }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录？") },
            text = { Text("本地笔记仍会保留在本机，下次登录后可继续同步。") },
            confirmButton = {
                TextButton(onClick = {
                    appVm.logout()
                    showLogoutConfirm = false
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        trailing?.invoke()
    }
}
