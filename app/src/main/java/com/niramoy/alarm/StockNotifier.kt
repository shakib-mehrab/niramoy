package com.niramoy.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.niramoy.MainActivity
import com.niramoy.R
import com.niramoy.data.UiPrefs
import com.niramoy.data.local.MedicineEntity
import com.niramoy.i18n.Strings
import com.niramoy.util.Num

/**
 * "Running low" and family-message notifications. Text only (no emoji); the small icon and
 * the channel carry the meaning.
 */
object StockNotifier {

    private fun open(context: Context, code: Int) = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun post(context: Context, channel: String, id: Int, title: String, body: String) {
        NotificationChannels.ensureCreated(context)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(0xFF47748C.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open(context, id))
            .setAutoCancel(true)
            .build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(id, n) }
    }

    private fun lang(context: Context) = UiPrefs(context).language

    private fun left(m: MedicineEntity, lang: String) = Num.loc(m.stock ?: 0.0, lang)

    /** On the person's own phone: one of their medicines is running low. */
    fun notifySelf(context: Context, m: MedicineEntity) {
        val l = lang(context)
        post(context, NotificationChannels.STOCK_CHANNEL_ID, 20_000 + (m.id.hashCode() and 0xFFFF),
            Strings.f("stock_low_title", l, m.name), Strings.f("stock_low_self", l, left(m, l)))
    }

    /** On a family member's phone: someone they care for is running low. */
    fun notifyFamily(context: Context, personName: String, m: MedicineEntity) {
        val l = lang(context)
        post(context, NotificationChannels.STOCK_CHANNEL_ID, 30_000 + ((personName + m.id).hashCode() and 0xFFFF),
            Strings.f("stock_low_family_title", l, personName, m.name), Strings.f("stock_low_family", l, personName, left(m, l)))
    }

    /** A family member sent a message about a medicine ("please buy" / "I'll bring it"). */
    fun notifyNudge(context: Context, id: String, fromName: String, medicineName: String, type: String) {
        val l = lang(context)
        val key = if (type == "buying") "nudge_buying" else "nudge_buy"
        post(context, NotificationChannels.FAMILY_CHANNEL_ID, 40_000 + (id.hashCode() and 0xFFFF),
            Strings.f("${key}_title", l, fromName), Strings.f(key, l, fromName, medicineName))
    }
}
