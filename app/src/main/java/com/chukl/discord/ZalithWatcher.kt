package com.chukl.discord

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Works out which app is in front, using Android's "Usage access" permission. */
class ZalithWatcher(private val context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var lastPoll = 0L
    private var foreground: String? = null

    companion object {
        // Zalith Launcher (1 and 2) and its forks all start with this.
        private const val ZALITH_PREFIX = "com.movtery.zalithlauncher"

        fun isZalith(pkg: String?) = pkg != null && pkg.startsWith(ZALITH_PREFIX)

        fun hasAccess(context: Context): Boolean {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val mode = ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Returns the package that is currently in front, or null if unknown. */
    @Suppress("DEPRECATION")
    fun poll(): String? {
        val now = System.currentTimeMillis()
        val from = if (lastPoll == 0L) now - 30 * 60 * 1000L else lastPoll - 1000L
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foreground = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    if (e.packageName == foreground) foreground = null
            }
        }
        lastPoll = now
        return foreground
    }
}
