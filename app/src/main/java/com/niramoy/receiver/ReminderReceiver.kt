package com.niramoy.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.niramoy.MainActivity
import com.niramoy.R
import com.niramoy.alarm.NotificationChannels
import com.niramoy.alarm.ReminderNotifier
import com.niramoy.alarm.ReminderScheduler
import com.niramoy.data.AppRepository
import com.niramoy.i18n.Strings
import com.niramoy.model.DoseGroup
import com.niramoy.model.DoseSchedule
import com.niramoy.model.DoseStatus
import com.niramoy.service.ReminderSoundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

/**
 * Dose alarm sequence for one group:
 *   attempt 0..count-1 -> notify + sound, then schedule the next attempt `interval` minutes later;
 *   attempt == count   -> still unanswered: log every pending medicine as MISSED.
 * Every step re-reads the data, so a dose taken from the app (or another device) stops the sequence.
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"

        /** Shared by the notification action, the full-screen alarm and the Home button. */
        suspend fun markGroup(context: Context, key: String, status: DoseStatus) {
            val group = DoseGroup.parse(key) ?: return
            val repo = AppRepository.getInstance(context)
            val meds = DoseSchedule.groupsOn(group.date, repo.getMedicines())[group].orEmpty()
            DoseSchedule.pending(group, meds, repo.doseLogsFor(group.date)).forEach {
                repo.logDose(group.date, it.id, group.slot, status)
            }
            finishGroup(context, key)
        }

        fun finishGroup(context: Context, key: String) {
            ReminderScheduler.cancelGroup(context, key)
            ReminderSoundService.stop(context)
            ReminderNotifier.cancel(context, key)
            // Again shortly after, in case the sound service was still re-posting it.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ ReminderNotifier.cancel(context, key) }, 1_500L)
        }

        suspend fun snooze(context: Context, key: String) {
            val s = AppRepository.getInstance(context).getSettings()
            val state = ReminderScheduler.getState(context, key)
            ReminderSoundService.stop(context)
            ReminderNotifier.cancel(context, key)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ ReminderNotifier.cancel(context, key) }, 1_500L)
            // Snoozing doesn't use up a reminder; the sequence continues from the same step.
            ReminderScheduler.scheduleAttempt(context, key, state?.attempt ?: 1, System.currentTimeMillis() + s.snoozeMinutes * 60_000L)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationChannels.ensureCreated(context)
                val key = intent.getStringExtra(ReminderScheduler.EXTRA_GROUP)
                when (intent.action) {
                    ReminderScheduler.ACTION_DOSE -> if (key != null) onDose(context, key, intent.getIntExtra(ReminderScheduler.EXTRA_ATTEMPT, 0))
                    ReminderScheduler.ACTION_TAKEN -> if (key != null) markGroup(context, key, DoseStatus.TAKEN)
                    ReminderScheduler.ACTION_SNOOZE -> if (key != null) snooze(context, key)
                    ReminderScheduler.ACTION_VISIT -> onVisit(context)
                    ReminderScheduler.ACTION_REFRESH -> ReminderScheduler.rescheduleAll(context)
                    ReminderScheduler.ACTION_TEST -> onTest(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onDose(context: Context, key: String, attempt: Int) {
        val group = DoseGroup.parse(key) ?: return
        val repo = AppRepository.getInstance(context)
        val s = repo.getSettings()
        val meds = DoseSchedule.groupsOn(group.date, repo.getMedicines())[group].orEmpty()
        val waiting = DoseSchedule.pending(group, meds, repo.doseLogsFor(group.date))
        if (waiting.isEmpty()) { finishGroup(context, key); return }

        if (attempt >= s.remindCount.coerceAtLeast(1)) {
            waiting.forEach { repo.logDose(group.date, it.id, group.slot, DoseStatus.MISSED, auto = true) }
            finishGroup(context, key)
            return
        }

        // Re-arm the next step FIRST so a slow/failed delivery never ends the sequence.
        ReminderScheduler.scheduleAttempt(context, key, attempt + 1, System.currentTimeMillis() + s.remindInterval.coerceAtLeast(1) * 60_000L)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { nm?.notify(ReminderScheduler.notificationId(key), ReminderNotifier.build(context, group, waiting, s.language)) }
        ReminderSoundService.start(context, key)
    }

    /** Setup-screen test: same channel and tone as a real dose, without touching any data. */
    private suspend fun onTest(context: Context) {
        val s = AppRepository.getInstance(context).getSettings()
        val n = NotificationCompat.Builder(context, NotificationChannels.DOSE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(Strings.t("test_title", s.language))
            .setContentText(Strings.t("test_body", s.language))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(9_002, n) }
        val uri = com.niramoy.alarm.SoundHelper.uriFor(context, s.tone, s.language, null, null)
        kotlinx.coroutines.withTimeoutOrNull(8_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                val p = com.niramoy.alarm.SoundHelper.preview(context, uri) { if (cont.isActive) cont.resume(Unit) }
                if (p == null && cont.isActive) cont.resume(Unit)
                cont.invokeOnCancellation { runCatching { p?.release() } }
            }
        }
    }

    private suspend fun onVisit(context: Context) {
        val lang = AppRepository.getInstance(context).getSettings().language
        val open = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, NotificationChannels.VISIT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(Strings.t("visit_today_title", lang))
            .setContentText(Strings.t("visit_today_body", lang))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(9_001, n) }
    }
}
