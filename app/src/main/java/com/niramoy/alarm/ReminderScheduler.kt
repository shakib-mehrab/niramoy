package com.niramoy.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.niramoy.MainActivity
import com.niramoy.data.AppRepository
import com.niramoy.data.local.SettingsEntity
import com.niramoy.model.DoseGroup
import com.niramoy.model.DoseSchedule
import com.niramoy.model.DoseStatus
import com.niramoy.receiver.ReminderReceiver
import com.niramoy.util.Dates
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/**
 * Rebuilds every alarm from the CURRENT data each time it runs (app start, any medicine/settings
 * change, boot, app update, time/zone change, the daily refresh and after every reminder).
 *
 * One alarm per dose group, identified by its data URI (niramoy://group/<key>), so rescheduling
 * replaces instead of duplicating. A group's progress through "repeat until acknowledged"
 * (next attempt + when) is kept in SharedPreferences, so a reboot or app update resumes the
 * sequence where it was instead of restarting or dropping it.
 *
 * Dose alarms use setAlarmClock(): exempt from Doze and app-standby limits, the most reliable
 * exact alarm Android offers (also shows the alarm icon in the status bar).
 */
object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val PREFS = "niramoy_alarm_state"
    private const val KEY_SCHEDULED = "scheduled_keys"
    private val mutex = Mutex()

    const val ACTION_DOSE = "com.niramoy.ACTION_DOSE"
    const val ACTION_TAKEN = "com.niramoy.ACTION_TAKEN"
    const val ACTION_SNOOZE = "com.niramoy.ACTION_SNOOZE"
    const val ACTION_VISIT = "com.niramoy.ACTION_VISIT"
    const val ACTION_REFRESH = "com.niramoy.ACTION_REFRESH"
    const val ACTION_TEST = "com.niramoy.ACTION_TEST"
    const val EXTRA_GROUP = "group"
    const val EXTRA_ATTEMPT = "attempt"
    const val EXTRA_VISIT_ID = "visit_id"

    data class GroupState(val attempt: Int, val at: Long)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getState(context: Context, key: String): GroupState? =
        prefs(context).getString("g_$key", null)?.split(';')?.takeIf { it.size == 2 }?.let {
            GroupState(it[0].toIntOrNull() ?: 0, it[1].toLongOrNull() ?: 0L)
        }

    private fun putState(context: Context, key: String, s: GroupState) {
        val p = prefs(context)
        val keys = p.getStringSet(KEY_SCHEDULED, emptySet())!!.toMutableSet().apply { add(key) }
        p.edit().putString("g_$key", "${s.attempt};${s.at}").putStringSet(KEY_SCHEDULED, keys).apply()
    }

    fun clearState(context: Context, key: String) {
        val p = prefs(context)
        val keys = p.getStringSet(KEY_SCHEDULED, emptySet())!!.toMutableSet().apply { remove(key) }
        p.edit().remove("g_$key").putStringSet(KEY_SCHEDULED, keys).apply()
    }

    fun notificationId(key: String): Int = 10_000 + (key.hashCode() and 0x0FFFFFFF) % 1_000_000

    private fun groupIntent(context: Context, key: String) = Intent(context, ReminderReceiver::class.java).apply {
        action = ACTION_DOSE
        data = Uri.parse("niramoy://group/" + Uri.encode(key))
    }

    private fun groupPi(context: Context, key: String, attempt: Int): PendingIntent =
        PendingIntent.getBroadcast(context, 0, groupIntent(context, key).putExtra(EXTRA_GROUP, key).putExtra(EXTRA_ATTEMPT, attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Schedules attempt [attempt] of group [key] at [at] and remembers it. */
    fun scheduleAttempt(context: Context, key: String, attempt: Int, at: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        putState(context, key, GroupState(attempt, at))
        setAlarmClock(context, am, at, groupPi(context, key, attempt))
    }

    fun cancelGroup(context: Context, key: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        PendingIntent.getBroadcast(context, 0, groupIntent(context, key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { am.cancel(it); it.cancel() }
        clearState(context, key)
    }

    fun isGroupScheduled(context: Context, key: String): Boolean =
        PendingIntent.getBroadcast(context, 0, groupIntent(context, key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun setAlarmClock(context: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact(context)) {
                val show = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                // Without the exact-alarm permission: best effort (may be a few minutes late).
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "exact alarm refused, using inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Full rebuild. Also logs doses whose reminder sequence ended unanswered as missed. */
    suspend fun rescheduleAll(context: Context, now: Long = System.currentTimeMillis()) = mutex.withLock {
        val repo = AppRepository.getInstance(context)
        val s = repo.getSettings()
        val meds = repo.getMedicines()
        val today = Dates.today(now)
        val dates = listOf(Dates.addDays(today, -1), today, Dates.addDays(today, 1))
        val logs = dates.flatMap { repo.doseLogsFor(it) }

        // 1) Missed doses (phone was off, app killed, alarms blocked...). Explicit marks are never overwritten.
        DoseSchedule.overdue(dates, meds, logs, s, now).forEach { (g, m) ->
            repo.logDose(g.date, m.id, g.slot, DoseStatus.MISSED, auto = true)
        }
        val freshLogs = dates.flatMap { repo.doseLogsFor(it) }

        // 2) Dose groups for today and tomorrow (the daily refresh rolls the window forward).
        val wanted = mutableSetOf<String>()
        for (date in dates.drop(1)) {
            for ((g, list) in DoseSchedule.groupsOn(date, meds)) {
                if (DoseSchedule.pending(g, list, freshLogs).isEmpty()) continue
                val base = DoseSchedule.timeOf(g, s)
                val state = getState(context, g.key)
                when {
                    // Mid-sequence (or resumed after reboot): keep its attempt; fire soon if the time already passed.
                    state != null && now < DoseSchedule.deadline(g, s) + 60_000L -> {
                        scheduleAttempt(context, g.key, state.attempt, maxOf(state.at, now + 3_000L)); wanted += g.key
                    }
                    base > now -> { scheduleAttempt(context, g.key, 0, base); wanted += g.key }
                }
            }
        }
        // 3) Cancel alarms of groups that no longer exist (medicine stopped/edited/deleted, all taken...).
        val known = prefs(context).getStringSet(KEY_SCHEDULED, emptySet())!!.toSet()
        (known - wanted).forEach { cancelGroup(context, it) }

        scheduleVisits(context, repo, s, today, now)
        scheduleDailyRefresh(context, now)
    }

    /** "Doctor visit today" reminder, one hour after wake-up on the visit day. */
    private suspend fun scheduleVisits(context: Context, repo: AppRepository, s: SettingsEntity, today: String, now: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (v in repo.activeVisits()) {
            val date = v.nextDate ?: continue
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_VISIT
                data = Uri.parse("niramoy://visit/${v.id}")
                putExtra(EXTRA_VISIT_ID, v.id)
            }
            val at = Dates.atMinute(date, (s.wakeTime + 60).coerceAtMost(1439))
            if (v.remindNext && date >= today && at > now) {
                val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setAlarmClock(context, am, at, pi)
            } else {
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                    ?.let { am.cancel(it); it.cancel() }
            }
        }
    }

    /** Re-runs [rescheduleAll] just after midnight even if the app is never opened. */
    private fun scheduleDailyRefresh(context: Context, now: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 5); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val pi = PendingIntent.getBroadcast(context, 2,
            Intent(context, ReminderReceiver::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    /** A one-off alarm through the exact same path (exact alarm -> receiver -> sound), for the setup screen. */
    fun scheduleTest(context: Context, at: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(context, 4,
            Intent(context, ReminderReceiver::class.java).apply { action = ACTION_TEST },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarmClock(context, am, at, pi)
    }

    /** The next pending group today/tomorrow (Home "next dose" card). */
    fun nextGroupTime(group: DoseGroup, s: SettingsEntity) = DoseSchedule.timeOf(group, s)
}
