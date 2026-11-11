package com.nexo.smartnotes.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 提醒时间到达时的广播接收器。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra("noteId").orEmpty()
        val title = intent.getStringExtra("title") ?: "笔记提醒"
        val body = intent.getStringExtra("body") ?: "你有一条笔记到时间了"
        ReminderScheduler.notifyNow(context, noteId, title, body)
    }
}
