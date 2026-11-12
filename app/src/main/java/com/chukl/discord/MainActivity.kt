package com.chukl.discord

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.discord.socialsdk.DiscordSocialSdkInit

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store

    private lateinit var heroDot: View
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var usageText: TextView
    private lateinit var discordText: TextView
    private lateinit var batteryText: TextView
    private lateinit var usageButton: Button
    private lateinit var loginButton: Button
    private lateinit var batteryButton: Button

    // Colours: deep plum background, soft cards, lilac accent.
    private val bgTop = Color.parseColor("#3B2441")
    private val bgBottom = Color.parseColor("#15111B")
    private val card = Color.parseColor("#26FFFFFF")
    private val lilac = Color.parseColor("#C9A2F5")
    private val ink = Color.parseColor("#1B1325")
    private val muted = Color.parseColor("#B9AEC6")
    private val good = Color.parseColor("#8FE3A8")
    private val warn = Color.parseColor("#FFB86B")
    private val bad = Color.parseColor("#FF7A8A")

    private val refresh = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 500)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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

    // ---------- small view helpers ----------

    private fun text(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun cardLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(card, 24)
        setPadding(dp(20), dp(18), dp(20), dp(20))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) }
    }

    private fun pill(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        stateListAnimator = null
        minHeight = dp(48)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { topMargin = dp(14) }
        setOnClickListener { onClick() }
        styleButton(this, true)
    }

    private fun styleButton(b: Button, enabled: Boolean, doneLabel: String? = null) {
        b.isEnabled = enabled
        if (enabled) {
            b.background = rounded(lilac, 25)
            b.setTextColor(ink)
        } else {
            b.background = rounded(Color.parseColor("#1FFFFFFF"), 25)
            b.setTextColor(muted)
        }
    }

    private fun stepHeader(number: String, title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val badge = text(number, 14f, ink, true).apply {
            gravity = Gravity.CENTER
            background = rounded(lilac, 14)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }
        addView(badge)
        addView(text(title, 18f, Color.WHITE, true).apply {
            setPadding(dp(12), 0, 0, 0)
        })
    }

    // ---------- screen ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }

        root.addView(text("Chukl Discord", 32f, Color.WHITE, true))
        root.addView(text(
            "Shows \"Playing ChuklAddon\" on your Discord while Zalith is open.", 14f, muted
        ).apply { setPadding(0, dp(6), 0, dp(6)) })

        // Hero status card
        val hero = cardLayout().apply {
            background = rounded(Color.parseColor("#33C9A2F5"), 28, Color.parseColor("#55C9A2F5"))
        }
        val heroRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(12) }
            background = rounded(muted, 7)
        }
        heroTitle = text("", 20f, Color.WHITE, true)
        heroRow.addView(heroDot)
        heroRow.addView(heroTitle)
        heroSub = text("", 14f, muted).apply { setPadding(0, dp(8), 0, 0) }
        hero.addView(heroRow)
        hero.addView(heroSub)
        root.addView(hero)

        // Step 1
        val c1 = cardLayout()
        c1.addView(stepHeader("1", "Allow usage access"))
        usageText = text("", 14f, muted).apply { setPadding(0, dp(10), 0, 0) }
        c1.addView(usageText)
        usageButton = pill("Open usage access settings") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        c1.addView(usageButton)
        root.addView(c1)

        // Step 2
        val c2 = cardLayout()
        c2.addView(stepHeader("2", "Sign in with Discord"))
        discordText = text("", 14f, muted).apply { setPadding(0, dp(10), 0, 0) }
        c2.addView(discordText)
        loginButton = pill("Sign in with Discord") {
            DiscordSocialSdkInit.setEngineActivity(this@MainActivity)
            Native.startLogin()
        }
        c2.addView(loginButton)
        root.addView(c2)

        // Step 3
        val c3 = cardLayout()
        c3.addView(stepHeader("3", "Keep it running"))
        batteryText = text("", 14f, muted).apply { setPadding(0, dp(10), 0, 0) }
        c3.addView(batteryText)
        batteryButton = pill("Don't let Android stop this app") {
            @Suppress("BatteryLife")
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        c3.addView(batteryButton)
        root.addView(c3)

        // Test
        val c4 = cardLayout()
        c4.addView(text("Try it", 18f, Color.WHITE, true))
        c4.addView(text(
            "Shows your status for 20 seconds so you can check it works.", 14f, muted
        ).apply { setPadding(0, dp(8), 0, 0) })
        c4.addView(pill("Show my status now") {
            PresenceService.forceUntil = System.currentTimeMillis() + 20_000L
        })
        root.addView(c4)

        val scroll = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bgTop, bgBottom)
            )
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
    }

    private fun setDot(color: Int) {
        heroDot.background = rounded(color, 7)
    }

    private fun updateUi() {
        val hasUsage = ZalithWatcher.hasAccess(this)
        val state = Native.state()
        val message = Native.message()
        val unrestricted = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        val testing = System.currentTimeMillis() < PresenceService.forceUntil

        // Step cards
        usageText.text = if (hasUsage) "Done. The app can see when Zalith is open."
        else "Needed so the app can see when Zalith is open."
        styleButton(usageButton, !hasUsage)
        usageButton.text = if (hasUsage) "Done" else "Open usage access settings"

        discordText.text = when (state) {
            "READY" -> "Connected to Discord."
            "WAITING_LOGIN" -> "Finish signing in in the browser..."
            "CONNECTING" -> "Connecting to Discord..."
            "ERROR" -> message.ifEmpty { "Something went wrong." }
            else -> message.ifEmpty { "Not signed in." }
        }
        styleButton(loginButton, state == "SIGNED_OUT" || state == "ERROR")
        loginButton.text = if (state == "READY") "Signed in" else "Sign in with Discord"

        batteryText.text = if (unrestricted) "Done. Android won't close the app."
        else "Recommended, so Android doesn't close the app while Minecraft is running."
        styleButton(batteryButton, !unrestricted)
        batteryButton.text = if (unrestricted) "Done" else "Don't let Android stop this app"

        // Hero status
        when {
            state == "READY" && (PresenceService.zalithInFront || testing) -> {
                setDot(good)
                heroTitle.text = "Your status is on"
                heroSub.text = "Friends see \"Playing ChuklAddon\"."
            }
            state == "READY" -> {
                setDot(good)
                heroTitle.text = "Ready"
                heroSub.text = "Open Zalith and your status turns on."
            }
            state == "ERROR" -> {
                setDot(bad)
                heroTitle.text = "Problem"
                heroSub.text = message.ifEmpty { "Something went wrong." }
            }
            state == "CONNECTING" || state == "WAITING_LOGIN" -> {
                setDot(warn)
                heroTitle.text = "Connecting..."
                heroSub.text = "Hang on a moment."
            }
            else -> {
                setDot(muted)
                heroTitle.text = "Not signed in"
                heroSub.text = "Do the steps below to get started."
            }
        }
    }
}
