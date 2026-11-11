package com.nexo.smartnotes.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 日期时间格式化工具。 */
object DateFormats {

    private val hm = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val mdHm = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
    private val ymd = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    private val ymdHm = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)
    private val weekday = SimpleDateFormat("EEEE", Locale.CHINA)

    fun time(ts: Long): String = hm.format(Date(ts))
    fun monthDayTime(ts: Long): String = mdHm.format(Date(ts))
    fun date(ts: Long): String = ymd.format(Date(ts))
    fun full(ts: Long): String = ymdHm.format(Date(ts))

    /** 列表里的相对时间：刚刚 / 5 分钟前 / 昨天 18:20 / 3月2日 / 2024年3月2日 */
    fun relative(ts: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - ts
        return when {
            diff in 0 until 60_000 -> "刚刚"
            diff in 0 until 3_600_000 -> "${diff / 60_000} 分钟前"
            isSameDay(ts, now) -> "今天 ${time(ts)}"
            isYesterday(ts, now) -> "昨天 ${time(ts)}"
            isSameYear(ts, now) -> mdHm.format(Date(ts))
            else -> ymd.format(Date(ts))
        }
    }

    /** 日程展示：今天 14:00 / 明天 09:30 / 3月5日 14:00 */
    fun scheduleLabel(ts: Long, now: Long = System.currentTimeMillis()): String = when {
        isSameDay(ts, now) -> "今天 ${time(ts)}"
        isTomorrow(ts, now) -> "明天 ${time(ts)}"
        isSameYear(ts, now) -> mdHm.format(Date(ts))
        else -> ymdHm.format(Date(ts))
    }

    fun scheduleRange(start: Long, end: Long?): String {
        if (end == null || end <= start) return scheduleLabel(start)
        return if (isSameDay(start, end)) {
            "${scheduleLabel(start)} – ${time(end)}"
        } else {
            "${scheduleLabel(start)} – ${scheduleLabel(end)}"
        }
    }

    fun weekdayOf(ts: Long): String = weekday.format(Date(ts))

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun isYesterday(ts: Long, now: Long): Boolean =
        isSameDay(ts, now - 86_400_000L)

    fun isTomorrow(ts: Long, now: Long): Boolean =
        isSameDay(ts, now + 86_400_000L)

    fun isSameYear(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
    }

    fun isOverdue(ts: Long?, now: Long = System.currentTimeMillis()): Boolean =
        ts != null && ts < now

    /** 把时间戳对齐到当天 09:00（新建提醒时的默认值）。 */
    fun todayAt(hour: Int, minute: Int = 0): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis < System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}
