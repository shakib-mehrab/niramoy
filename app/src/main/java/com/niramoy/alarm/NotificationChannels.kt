package com.niramoy.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Channels. The dose channel is silent because the app plays the chosen tone itself (voice phrase
 * for the exact slot, generic phrase, beep or system alarm) on the ALARM stream, which a channel
 * sound cannot do and which would otherwise be locked once the channel exists.
 */
object NotificationChannels {
    const val DOSE_CHANNEL_ID = "niramoy_doses_v1"
    const val VISIT_CHANNEL_ID = "niramoy_visits_v1"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(DOSE_CHANNEL_ID, "ওষুধের সময় / Medicine time", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "ওষুধ খাওয়ার এলার্ম"
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                },
                NotificationChannel(VISIT_CHANNEL_ID, "ডাক্তার দেখানো / Doctor visit", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "ডাক্তারের কাছে যাওয়ার দিন"
                }
            )
        )
    }

    /** False when the user blocked the channel or made it silent in system settings. */
    fun isChannelAudible(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return true
        if (!nm.areNotificationsEnabled()) return false
        val ch = nm.getNotificationChannel(channelId) ?: return true
        return ch.importance >= NotificationManager.IMPORTANCE_DEFAULT
    }
}
