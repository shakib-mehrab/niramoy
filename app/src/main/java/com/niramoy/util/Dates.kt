package com.niramoy.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * All dates are local "yyyy-MM-dd" strings and all clock times are minutes since local midnight,
 * in the device time zone (Asia/Dhaka for the intended user; it has no DST, but Calendar
 * handles DST anyway because we always build instants from wall-clock fields).
 */
object Dates {
    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }

    fun today(nowMillis: Long = System.currentTimeMillis()): String = fmt().format(Date(nowMillis))

    fun format(millis: Long): String = fmt().format(Date(millis))

    fun parse(date: String): Calendar? = runCatching {
        Calendar.getInstance(TimeZone.getDefault()).apply {
            time = fmt().parse(date)!!
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }.getOrNull()

    fun addDays(date: String, days: Int): String {
        val cal = parse(date) ?: return date
        cal.add(Calendar.DAY_OF_YEAR, days)
        return fmt().format(cal.time)
    }

    /** Whole days from [from] to [to] (negative if [to] is earlier). */
    fun daysBetween(from: String, to: String): Int {
        val a = parse(from) ?: return 0
        val b = parse(to) ?: return 0
        return (epochDay(b) - epochDay(a)).toInt()
    }

    /** Local calendar day number (days since 1970-01-01 in the calendar's zone). */
    fun epochDay(cal: Calendar): Long {
        val offset = cal.timeZone.getOffset(cal.timeInMillis)
        return Math.floorDiv(cal.timeInMillis + offset, 86_400_000L)
    }

    /** Epoch millis of [minuteOfDay] on local [date]. */
    fun atMinute(date: String, minuteOfDay: Int): Long {
        val cal = parse(date) ?: Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        cal.set(Calendar.MINUTE, minuteOfDay % 60)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun minuteOfDay(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** Last [n] dates ending today, oldest first. */
    fun lastDays(n: Int, today: String = today()): List<String> = (n - 1 downTo 0).map { addDays(today, -it) }

    fun dayOfWeek(date: String): Int = parse(date)?.get(Calendar.DAY_OF_WEEK) ?: Calendar.SUNDAY
    fun dayOfMonth(date: String): Int = parse(date)?.get(Calendar.DAY_OF_MONTH) ?: 1
    fun month(date: String): Int = parse(date)?.get(Calendar.MONTH) ?: 0
    fun year(date: String): Int = parse(date)?.get(Calendar.YEAR) ?: 2026
}
