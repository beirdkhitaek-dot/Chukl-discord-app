package com.chukl.discord

import android.content.Context

/** Remembers the Discord login between app launches. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("chukl_discord", Context.MODE_PRIVATE)

    var refreshToken: String?
        get() = prefs.getString("refresh", null)
        set(v) {
            prefs.edit().apply { if (v == null) remove("refresh") else putString("refresh", v) }.apply()
        }

    /** Whether the background watcher should be running. */
    var watching: Boolean
        get() = prefs.getBoolean("watching", true)
        set(v) {
            prefs.edit().putBoolean("watching", v).apply()
        }
}
