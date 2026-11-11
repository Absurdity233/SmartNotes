package com.nexo.smartnotes.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后重新排定提醒。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
