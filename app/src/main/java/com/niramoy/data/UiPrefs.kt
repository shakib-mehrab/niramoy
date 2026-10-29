package com.niramoy.data

import android.content.Context

/** Small device-only preferences (not synced). Language is mirrored here so pre-sign-in screens know it. */
class UiPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("niramoy_ui_prefs", Context.MODE_PRIVATE)

    /** User chose "use without account" on the sign-in screen. */
    var guestMode: Boolean
        get() = prefs.getBoolean("guest_mode", false)
        set(v) = prefs.edit().putBoolean("guest_mode", v).apply()

    var language: String
        get() = prefs.getString("language", "bn") ?: "bn"
        set(v) = prefs.edit().putString("language", v).apply()

    /** The picture-based permission guide was finished or skipped once. */
    var permissionGuideSeen: Boolean
        get() = prefs.getBoolean("permission_guide_seen", false)
        set(v) = prefs.edit().putBoolean("permission_guide_seen", v).apply()

    /** Attach a map link to emergency WhatsApp messages. */
    var shareLocation: Boolean
        get() = prefs.getBoolean("share_location", true)
        set(v) = prefs.edit().putBoolean("share_location", v).apply()
}
