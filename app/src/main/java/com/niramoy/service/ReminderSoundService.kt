package com.niramoy.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.niramoy.alarm.NotificationChannels
import com.niramoy.alarm.ReminderNotifier
import com.niramoy.alarm.ReminderScheduler
import com.niramoy.alarm.SoundHelper
import com.niramoy.data.AppRepository
import com.niramoy.model.DoseGroup
import com.niramoy.model.DoseSchedule
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that shows the dose notification and plays the tone on the ALARM stream
 * (audible even when ringer is on vibrate), repeating the phrase a few times, for at most 40 s.
 * Started from the exact alarm broadcast, which Android allows from the background.
 */
class ReminderSoundService : Service() {

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopEverything(removeNotification = false) }
    private var plays = 0
    private var notificationId = 0
    private var lastNotification: Notification? = null

    companion object {
        private const val TAG = "ReminderSound"
        private const val ACTION_START = "com.niramoy.SOUND_START"
        private const val ACTION_STOP = "com.niramoy.SOUND_STOP"
        private const val MAX_MILLIS = 40_000L
        private const val MAX_PLAYS = 4

        fun start(context: Context, groupKey: String) {
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, ReminderSoundService::class.java).setAction(ACTION_START).putExtra(ReminderScheduler.EXTRA_GROUP, groupKey))
            } catch (e: Exception) {
                Log.e(TAG, "could not start service", e) // the receiver already posted the notification
            }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, ReminderSoundService::class.java).setAction(ACTION_STOP)) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Niramoy:Reminder")?.apply { acquire(MAX_MILLIS + 5_000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopEverything(removeNotification = true); return START_NOT_STICKY }
        val key = intent?.getStringExtra(ReminderScheduler.EXTRA_GROUP)
        val group = key?.let { DoseGroup.parse(it) }
        if (group == null) { stopEverything(removeNotification = true); return START_NOT_STICKY }

        // Small, bounded DB read (a few rows) to build the same notification the receiver posted.
        val (notification, uri) = runBlocking {
            val repo = AppRepository.getInstance(applicationContext)
            val s = repo.getSettings()
            val meds = DoseSchedule.groupsOn(group.date, repo.getMedicines())[group].orEmpty()
            val pending = DoseSchedule.pending(group, meds, repo.doseLogsFor(group.date))
            ReminderNotifier.build(applicationContext, group, pending, s.language) to
                SoundHelper.uriFor(applicationContext, s.tone, s.voiceLanguage, group.slot, group.timing)
        }
        notificationId = ReminderScheduler.notificationId(group.key)
        lastNotification = notification
        startInForeground(notificationId, notification)
        if (NotificationChannels.isChannelAudible(this, NotificationChannels.DOSE_CHANNEL_ID)) play(uri)
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, MAX_MILLIS)
        return START_NOT_STICKY
    }

    private fun startInForeground(id: Int, n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, id, n, type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun play(uri: Uri?) {
        releasePlayer()
        if (uri == null) return
        plays = 0
        try {
            player = MediaPlayer().apply {
                setDataSource(applicationContext, uri)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setOnCompletionListener { mp ->
                    plays++
                    // Repeat the phrase with a short pause, like someone calling again.
                    if (plays < MAX_PLAYS) handler.postDelayed({ runCatching { mp.seekTo(0); mp.start() } }, 1_500L)
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "play failed", e)
        }
    }

    private fun releasePlayer() {
        runCatching { player?.let { if (it.isPlaying) it.stop(); it.release() } }
        player = null
    }

    /**
     * Stops the sound. After the time-out the dose is still unanswered, so the notification is
     * re-posted as a plain app notification (a "detached" service notification can't be cancelled
     * on some phones, e.g. Samsung). When the dose was answered or snoozed it is removed.
     */
    private fun stopEverything(removeNotification: Boolean) {
        releasePlayer()
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val n = lastNotification
        if (!removeNotification && n != null && notificationId != 0) runCatching { nm?.notify(notificationId, n) }
        else if (notificationId != 0) runCatching { nm?.cancel(notificationId) }
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        handler.removeCallbacksAndMessages(null)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }
}
