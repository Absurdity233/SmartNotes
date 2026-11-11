package com.nexo.smartnotes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import java.util.Calendar

/**
 * 日期 + 时间选择对话框。
 * 分两步：先选日期，再选时间，返回毫秒时间戳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    var step by remember { mutableIntStateOf(0) }
    val calendar = remember { Calendar.getInstance().apply { timeInMillis = initial } }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
            override fun isSelectableYear(year: Int): Boolean = true
        },
    )
    val timeState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = true,
    )

    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { step = 1 }) { Text("下一步") }
            },
            dismissButton = {
                Row {
                    if (onClear != null) {
                        TextButton(onClick = { onClear(); onDismiss() }) { Text("清除") }
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            },
        ) {
            DatePicker(state = dateState, title = {
                Text("选择日期", Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("选择时间", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(20.dp))
                    TimePicker(state = timeState)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { step = 0 }) { Text("上一步") }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = {
                            val dateMillis = dateState.selectedDateMillis ?: initial
                            val result = Calendar.getInstance().apply {
                                timeInMillis = dateMillis
                                set(Calendar.HOUR_OF_DAY, timeState.hour)
                                set(Calendar.MINUTE, timeState.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onConfirm(result.timeInMillis)
                        }) { Text("确定") }
                    }
                }
            }
        }
    }
}
