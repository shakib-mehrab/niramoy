package com.niramoy.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.niramoy.MainActivity
import com.niramoy.R
import com.niramoy.data.local.MedicineEntity
import com.niramoy.i18n.Strings
import com.niramoy.model.DoseGroup
import com.niramoy.receiver.ReminderReceiver
import com.niramoy.ui.ReminderActivity

/** Builds the dose notification: simple Bangla title/body, big "খেয়েছি" + "পরে বলো" actions, full-screen alarm. */
object ReminderNotifier {

    fun title(group: DoseGroup, lang: String) = Strings.t("phrase_${group.slot.id}_${group.timing.id}", lang)

    fun body(meds: List<MedicineEntity>) = meds.joinToString("\n") { m -> if (m.dose.isBlank()) "• ${m.name}" else "• ${m.name} — ${m.dose}" }

    private fun actionPi(context: Context, action: String, key: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("niramoy://$action/" + Uri.encode(key))
                putExtra(ReminderScheduler.EXTRA_GROUP, key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun fullScreenPi(context: Context, key: String): PendingIntent =
        PendingIntent.getActivity(
            context, ReminderScheduler.notificationId(key),
            Intent(context, ReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(ReminderScheduler.EXTRA_GROUP, key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun build(context: Context, group: DoseGroup, meds: List<MedicineEntity>, lang: String): Notification {
        val key = group.key
        val open = PendingIntent.getActivity(
            context, ReminderScheduler.notificationId(key),
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = body(meds)
        return NotificationCompat.Builder(context, NotificationChannels.DOSE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(0xFF47748C.toInt())
            .setContentTitle(title(group, lang))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setFullScreenIntent(fullScreenPi(context, key), true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_stat_pill, "✅ " + Strings.t("act_taken", lang), actionPi(context, ReminderScheduler.ACTION_TAKEN, key))
            .addAction(R.drawable.ic_stat_pill, "⏰ " + Strings.t("act_snooze", lang), actionPi(context, ReminderScheduler.ACTION_SNOOZE, key))
            .build()
    }

    fun cancel(context: Context, key: String) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(ReminderScheduler.notificationId(key))
    }
}
