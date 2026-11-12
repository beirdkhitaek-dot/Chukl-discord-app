package com.chukl.discord

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

/**
 * Keeps running in the background. Sets "Playing ChuklAddon" on Discord while Zalith is open
 * and clears it a little while after you leave.
 */
class PresenceService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private var watcher: ZalithWatcher? = null
    private var lastZalithSeen = 0L
    private var playing = false
    private var tickCount = 0
    private var triedSavedLogin = false
    private var lastRetry = 0L

    companion object {
        /** Keep the status for this long after Zalith leaves the screen. */
        const val GRACE_MS = 5 * 60_000L
        private const val POLL_EVERY_TICKS = 8 // 8 x 250 ms = 2 seconds

        @Volatile var zalithInFront = false
        @Volatile var forceUntil = 0L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PresenceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PresenceService::class.java))
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("status", "Discord status", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, "status")
            .setContentTitle("Chukl Discord")
            .setContentText("Shows your Discord status while you play")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        Native.init(Native.APP_ID, filesDir.absolutePath)
        handler.post(loop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        Native.setPlaying(false)
        super.onDestroy()
    }

    private fun tick() {
        Native.tick()

        // Sign in again automatically with the saved login.
        if (!triedSavedLogin && Native.state() == "SIGNED_OUT") {
            val saved = store.refreshToken
            triedSavedLogin = true
            if (saved != null) Native.loginWithRefresh(saved)
        }

        // Save any new login the SDK hands back.
        val tokens = Native.takeTokens()
        if (tokens == "EXPIRED") {
            store.refreshToken = null
        } else if (tokens.contains('\n')) {
            store.refreshToken = tokens.substringAfter('\n')
        }

        val now = System.currentTimeMillis()

        // Lost connection (for example school wifi dropping): try again every 30 seconds.
        if (Native.state() == "ERROR" && now - lastRetry > 30_000L) {
            val saved = store.refreshToken
            lastRetry = now
            if (saved != null) Native.loginWithRefresh(saved)
        }

        if (++tickCount % POLL_EVERY_TICKS != 0) return
        if (ZalithWatcher.hasAccess(this)) {
            if (watcher == null) watcher = ZalithWatcher(this)
            zalithInFront = ZalithWatcher.isZalith(watcher?.poll())
            if (zalithInFront) lastZalithSeen = now
        } else {
            zalithInFront = false
        }

        val shouldPlay = (now - lastZalithSeen < GRACE_MS && lastZalithSeen != 0L) ||
            now < forceUntil
        if (shouldPlay != playing) {
            playing = shouldPlay
            Native.setPlaying(playing)
        }
    }
}
