package com.nexo.smartnotes.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexo.smartnotes.MainActivity
import com.nexo.smartnotes.R
import com.nexo.smartnotes.SmartNotesApp
import com.nexo.smartnotes.data.local.AppDatabase
import com.nexo.smartnotes.data.local.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 笔记提醒：AlarmManager 精确闹钟 + 通知。 */
object ReminderScheduler {

    private const val CHANNEL_ID = "note_reminders"
    private const val REQUEST_BASE = 7_000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_reminder),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "日程与待办的提醒通知"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context, noteId: String, title: String, body: String, at: Long) {
        if (at <= System.currentTimeMillis()) return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("noteId", noteId)
            putExtra("title", title)
            putExtra("body", body)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(noteId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            alarm.set(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun cancel(context: Context, noteId: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(noteId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) alarm.cancel(pending)
    }

    /** 启动时按数据库重新排定所有未来提醒（应对重启 / 时区变化）。 */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext as? SmartNotesApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val session = app.repository.session.value
            val dao = AppDatabase.get(context).noteDao()
            dao.upcomingReminders(session.accountId, System.currentTimeMillis())
                .forEach { entity ->
                    val note = entity.toDomain()
                    val at = note.reminderAt ?: return@forEach
                    schedule(context, note.id, note.displayTitle, note.preview.take(60), at)
                }
        }
    }

    fun notifyNow(context: Context, noteId: String, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("noteId", noteId)
        }
        val content = PendingIntent.getActivity(
            context,
            requestCode(noteId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode(noteId), notification)
        }
    }

    private fun requestCode(noteId: String): Int = REQUEST_BASE + (noteId.hashCode() and 0xFFFF)
}
