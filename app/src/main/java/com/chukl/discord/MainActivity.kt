package com.chukl.discord

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.discord.socialsdk.DiscordSocialSdkInit

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var usageText: TextView
    private lateinit var discordText: TextView
    private lateinit var gameText: TextView
    private lateinit var usageButton: Button
    private lateinit var loginButton: Button
    private lateinit var batteryText: TextView
    private lateinit var batteryButton: Button

    private val refresh = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        DiscordSocialSdkInit.setEngineActivity(this)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        PresenceService.start(this)
    }

    override fun onResume() {
        super.onResume()
        // The SDK opens the Discord login from the activity that is on screen.
        DiscordSocialSdkInit.setEngineActivity(this)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun label(text: String, size: Float = 16f, color: Int = Color.WHITE) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setPadding(0, 12, 0, 12)
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1F22"))
            setPadding(48, 96, 48, 48)
            gravity = Gravity.TOP
        }
        root.addView(label("Chukl Discord", 28f))
        root.addView(label("Shows \"Playing ChuklAddon\" on your Discord while Zalith is open.", 14f, Color.LTGRAY))

        root.addView(label("Step 1: Allow usage access", 18f))
        usageText = label("", 14f, Color.LTGRAY)
        root.addView(usageText)
        usageButton = Button(this).apply {
            text = "Open usage access settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        root.addView(usageButton)

        root.addView(label("Step 2: Sign in with Discord", 18f))
        discordText = label("", 14f, Color.LTGRAY)
        root.addView(discordText)
        loginButton = Button(this).apply {
            text = "Sign in with Discord"
            setOnClickListener {
                DiscordSocialSdkInit.setEngineActivity(this@MainActivity)
                Native.startLogin()
            }
        }
        root.addView(loginButton)

        root.addView(label("Step 3: Keep it running", 18f))
        batteryText = label("", 14f, Color.LTGRAY)
        root.addView(batteryText)
        batteryButton = Button(this).apply {
            text = "Don't let Android stop this app"
            setOnClickListener {
                @Suppress("BatteryLife")
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        root.addView(batteryButton)

        root.addView(label("Minecraft", 18f))
        gameText = label("", 14f, Color.LTGRAY)
        root.addView(gameText)
        root.addView(Button(this).apply {
            text = "Test: show my status for 20 seconds"
            setOnClickListener {
                PresenceService.forceUntil = System.currentTimeMillis() + 20_000L
            }
        })

        setContentView(root)
    }

    private fun updateUi() {
        val hasUsage = ZalithWatcher.hasAccess(this)
        usageText.text = if (hasUsage) "Done" else "Needed so the app can see when Zalith is open."
        usageButton.isEnabled = !hasUsage

        val state = Native.state()
        discordText.text = when (state) {
            "READY" -> "Connected to Discord"
            "WAITING_LOGIN" -> "Finish signing in in the browser..."
            "CONNECTING" -> "Connecting to Discord..."
            "ERROR" -> Native.message().ifEmpty { "Something went wrong." }
            else -> Native.message().ifEmpty { "Not signed in." }
        }
        loginButton.isEnabled = state == "SIGNED_OUT" || state == "ERROR"

        val unrestricted = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        batteryText.text = if (unrestricted) "Done" else
            "Recommended, so Android doesn't close the app while Minecraft is running."
        batteryButton.isEnabled = !unrestricted

        gameText.text = when {
            !hasUsage -> "Waiting for usage access."
            PresenceService.zalithInFront -> "Zalith is open. Your status is on."
            System.currentTimeMillis() < PresenceService.forceUntil -> "Test running..."
            else -> "Zalith is not open."
        }
    }
}
