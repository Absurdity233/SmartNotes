package com.nexo.smartnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.smartnotes.ui.AppRoot
import com.nexo.smartnotes.ui.AppViewModel
import com.nexo.smartnotes.ui.theme.SmartNotesTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appVm: AppViewModel = viewModel()
            val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            // 回到前台时拉取一次云端改动：协作方在别处（或另一台设备）的修改
            // 无需手动点「同步」即可看到，同时重新排定提醒。
            val lifecycleOwner = LocalLifecycleOwner.current
            val observer = remember(lifecycleOwner) {
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> appVm.onEnterForeground()
                        // 退到后台即停轮询，避免无谓的网络与耗电
                        Lifecycle.Event.ON_STOP -> appVm.onLeaveForeground()
                        else -> Unit
                    }
                }
            }
            DisposableEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            SmartNotesTheme(darkTheme = dark) {
                AppRoot(appVm)
            }
        }
    }
}
