package com.nexo.smartnotes.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nexo.smartnotes.ui.components.Lucide
import com.nexo.smartnotes.ui.components.LucideIcon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.smartnotes.ui.AppViewModel
import com.nexo.smartnotes.ui.theme.Violet
import com.nexo.smartnotes.ui.theme.VioletLight

@Composable
fun AuthScreen(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val session by appVm.session.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) } // 0 登录，1 注册

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.user) {
        if (session.user != null) onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        Violet.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
                LucideIcon(Lucide.ArrowLeft, "返回")
            }

            Spacer(Modifier.height(26.dp))

            Box(
                Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Violet, VioletLight))),
                contentAlignment = Alignment.Center,
            ) {
                LucideIcon(Lucide.Notebook, null, Modifier.size(32.dp), Color.White)
            }

            Spacer(Modifier.height(18.dp))
            Text(
                if (tab == 0) "欢迎回来" else "创建账号",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (tab == 0) "登录后笔记将自动同步到云端" else "注册即可在多台设备间同步笔记",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {},
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        listOf("登录", "注册").forEachIndexed { index, label ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index; error = null },
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (tab == index) MaterialTheme.colorScheme.surface else Color.Transparent
                                    ),
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    AnimatedVisibility(tab == 1) {
                        Column {
                            AuthField(
                                value = displayName,
                                onValue = { displayName = it },
                                label = "昵称（可选）",
                                leading = { LucideIcon(Lucide.User, null, Modifier.size(19.dp)) },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    AuthField(
                        value = username,
                        onValue = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase() },
                        label = "用户名",
                        leading = { LucideIcon(Lucide.User, null, Modifier.size(19.dp)) },
                        hint = "3-20 位字母、数字或下划线",
                    )
                    Spacer(Modifier.height(12.dp))
                    AuthField(
                        value = password,
                        onValue = { password = it },
                        label = "密码",
                        leading = { LucideIcon(Lucide.Lock, null, Modifier.size(19.dp)) },
                        hint = "至少 6 位",
                        isPassword = true,
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                    )

                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            error = null
                            busy = true
                            // 校验在前端先做一次，避免网络往返
                            when {
                                username.length < 3 -> {
                                    error = "用户名至少 3 位"
                                    busy = false
                                }
                                password.length < 6 -> {
                                    error = "密码至少 6 位"
                                    busy = false
                                }
                                else -> {
                                    if (tab == 0) {
                                        appVm.login(username, password)
                                    } else {
                                        appVm.register(username, password, displayName)
                                    }
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(if (tab == 0) "登录" else "注册并登录", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { tab = if (tab == 0) 1 else 0 }) {
                            Text(if (tab == 0) "还没有账号？注册" else "已有账号？登录")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = { appVm.continueAsGuest(); onDone() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("继续以游客身份使用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "演示账号：absurdity / absurdity123 · alice / alice123\n" +
                    "提示：云服务需先在电脑上运行 server/server.js",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    leading: @Composable () -> Unit,
    hint: String? = null,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        leadingIcon = leading,
        supportingText = hint?.let {
            { Text(it, style = MaterialTheme.typography.labelSmall) }
        },
        visualTransformation = if (isPassword && !showPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onTogglePassword) {
                    LucideIcon(if (showPassword) Lucide.EyeOff else Lucide.Eye,
                        contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Violet,
            focusedLabelColor = Violet,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
