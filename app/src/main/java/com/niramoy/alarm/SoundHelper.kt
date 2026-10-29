package com.niramoy.alarm

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.niramoy.data.local.SettingsEntity
import com.niramoy.model.MealTiming
import com.niramoy.model.Slot

/**
 * Resolves the reminder tone. Voice files are generated at build time by tools/tts/generate_tones.py
 * into res/raw as <lang>_<slot>_<timing>.ogg, <lang>_generic.ogg and beep.ogg. They are looked up by
 * name so the app still builds (and falls back to beep / system alarm) before they are generated.
 */
object SoundHelper {
    private const val TAG = "SoundHelper"

    fun rawName(tone: String, lang: String, slot: Slot?, timing: MealTiming?): String? {
        val l = if (lang == "en") "en" else "bn"
        return when (tone) {
            SettingsEntity.TONE_VOICE -> if (slot != null && timing != null) "${l}_${slot.id}_${timing.id}" else "${l}_generic"
            SettingsEntity.TONE_GENERIC -> "${l}_generic"
            SettingsEntity.TONE_BEEP -> "beep"
            else -> null
        }
    }

    private fun rawUri(context: Context, name: String?): Uri? {
        if (name == null) return null
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        return if (id != 0) Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$id") else null
    }

    /** Tone -> bundled file, then beep, then the phone's alarm sound. */
    fun uriFor(context: Context, tone: String, lang: String, slot: Slot?, timing: MealTiming?): Uri? =
        rawUri(context, rawName(tone, lang, slot, timing))
            ?: (if (tone != SettingsEntity.TONE_SYSTEM) rawUri(context, "beep") else null)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun hasVoiceFiles(context: Context): Boolean = rawUri(context, "bn_generic") != null

    /** One-shot preview on the alarm stream (Settings > tone). */
    fun preview(context: Context, uri: Uri?, onDone: (() -> Unit)? = null): MediaPlayer? {
        if (uri == null) return null
        return try {
            MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setOnCompletionListener { it.release(); onDone?.invoke() }
                setOnErrorListener { mp, _, _ -> mp.release(); onDone?.invoke(); true }
                prepare(); start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "preview failed", e); onDone?.invoke(); null
        }
    }
}
