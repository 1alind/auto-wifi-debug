package com.alind.autowifidebug

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var webhookInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 26, 45)
        window.navigationBarColor = Color.rgb(15, 26, 45)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(15, 26, 45), Color.rgb(25, 46, 73))
            )
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }

        root.addView(text("ADB TOOLKIT", 12f, Color.rgb(112, 190, 255), true))
        root.addView(space(8))
        root.addView(text("Auto Wi-Fi Debug", 29f, Color.WHITE, true))
        root.addView(space(8))
        root.addView(text("Automatically monitors Wireless debugging and posts the current ADB port to Discord.", 15f, Color.rgb(207, 218, 233), false))
        root.addView(space(24))

        val statusCard = card()
        statusCard.addView(text("WIRELESS DEBUGGING", 12f, Color.rgb(163, 185, 212), true))
        statusCard.addView(space(10))
        status = text("Checking status…", 20f, Color.WHITE, true)
        statusCard.addView(status)
        root.addView(statusCard)
        root.addView(space(14))

        root.addView(Button(this).apply {
            text = "Start / retry monitor"
            isAllCaps = false
            textSize = 15f
            setOnClickListener {
                startMonitorService()
                Toast.makeText(this@MainActivity, "Monitor is running; waiting for Wi-Fi and Wireless debugging.", Toast.LENGTH_LONG).show()
            }
        }, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(space(14))

        val webhookCard = card()
        webhookCard.addView(text("DISCORD WEBHOOK", 12f, Color.rgb(163, 185, 212), true))
        webhookCard.addView(space(8))
        webhookCard.addView(text("Paste your Discord webhook URL. The app sends only messages like: connect 45827", 14f, Color.rgb(207, 218, 233), false))
        webhookCard.addView(space(8))
        webhookInput = EditText(this).apply {
            hint = "https://discord.com/api/webhooks/…"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(WifiDebugMonitorService.WEBHOOK_KEY, ""))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(170, 184, 202))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(35, 255, 255, 255))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(65, 255, 255, 255))
            }
        }
        webhookCard.addView(webhookInput, LinearLayout.LayoutParams(-1, dp(52)))
        webhookCard.addView(space(8))
        webhookCard.addView(Button(this).apply {
            text = "Save webhook URL"
            isAllCaps = false
            setOnClickListener {
                val value = webhookInput.text.toString().trim()
                if (value.isNotEmpty() && (!value.startsWith("https://discord.com/api/webhooks/") &&
                    !value.startsWith("https://discordapp.com/api/webhooks/"))) {
                    Toast.makeText(this@MainActivity, "Enter a valid Discord webhook URL.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(WifiDebugMonitorService.WEBHOOK_KEY, value).apply()
                Toast.makeText(this@MainActivity, if (value.isEmpty()) "Webhook cleared." else "Webhook saved.", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(webhookCard)
        root.addView(space(14))

        val setupCard = card()
        setupCard.addView(text("ONE-TIME PERMISSION", 12f, Color.rgb(163, 185, 212), true))
        setupCard.addView(space(8))
        permissionStatus = text("", 14f, Color.WHITE, false)
        setupCard.addView(permissionStatus)
        setupCard.addView(space(8))
        setupCard.addView(Button(this).apply {
            text = "Copy ADB grant command"
            isAllCaps = false
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ADB grant command", GRANT_COMMAND))
                Toast.makeText(this@MainActivity, "Command copied", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(setupCard)
        root.addView(space(14))

        root.addView(Button(this).apply {
            text = "Open Developer options"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        })
        root.addView(space(12))
        root.addView(text("Connect to Wi-Fi first. Android/Samsung may refuse automatic Wireless debugging changes or restrict boot startup.", 13f, Color.rgb(207, 218, 233), false))

        setContentView(scroll)
        startMonitorService()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun startMonitorService() {
        try {
            val serviceIntent = Intent(this, WifiDebugMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start monitor: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshStatus() {
        val enabled = try {
            Settings.Global.getInt(contentResolver, SETTING_ADB_WIFI_ENABLED, 0) == 1
        } catch (_: Exception) { false }
        status.text = if (enabled) "●  ON (setting)" else "○  OFF"
        status.setTextColor(if (enabled) Color.rgb(112, 231, 170) else Color.rgb(255, 202, 120))
        permissionStatus.text = if (hasPermission()) {
            "Permission granted. The monitor can request the Wireless debugging toggle."
        } else {
            "Permission not granted. Copy the command and run it from an authorized ADB shell."
        }
    }

    private fun hasPermission(): Boolean =
        checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = GradientDrawable().apply {
            setColor(Color.argb(30, 255, 255, 255))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.argb(45, 255, 255, 255))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.START
    }

    private fun space(height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS = "auto_wifi_debug"
        private const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"
        private const val GRANT_COMMAND =
            "adb shell pm grant com.alind.autowifidebug android.permission.WRITE_SECURE_SETTINGS"
    }
}
