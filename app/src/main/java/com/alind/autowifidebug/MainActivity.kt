package com.alind.autowifidebug

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var permissionStatus: TextView

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
        root.addView(text("A small helper for turning Wireless debugging on from the app or your own automation.", 15f, Color.rgb(207, 218, 233), false))
        root.addView(space(24))

        val statusCard = card()
        statusCard.addView(text("WIRELESS DEBUGGING", 12f, Color.rgb(163, 185, 212), true))
        statusCard.addView(space(10))
        status = text("Checking status…", 20f, Color.WHITE, true)
        statusCard.addView(status)
        root.addView(statusCard)
        root.addView(space(14))

        root.addView(Button(this).apply {
            text = "Turn Wireless Debugging ON"
            isAllCaps = false
            textSize = 15f
            setOnClickListener { enableWirelessDebugging() }
        }, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(space(10))

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
        root.addView(text("Android may require a system confirmation. Connect to Wi-Fi first. This app cannot bypass Android's security prompts.", 13f, Color.rgb(207, 218, 233), false))

        setContentView(scroll)
        startLocalServerService()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun startLocalServerService() {
        try {
            startForegroundService(Intent(this, LocalControlServerService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start the local API service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun enableWirelessDebugging() {
        if (!hasPermission()) {
            Toast.makeText(this, "Grant the one-time ADB permission first.", Toast.LENGTH_LONG).show()
            return
        }
        if (!isConnectedToWifi()) {
            Toast.makeText(this, "Connect to Wi-Fi first. Android requires an active Wi-Fi network.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val wrote = Settings.Global.putInt(contentResolver, SETTING_ADB_WIFI_ENABLED, 1)
            if (!wrote) {
                Toast.makeText(this, "Android rejected the setting change.", Toast.LENGTH_LONG).show()
                return
            }
            status.text = "Checking Android…"
            Toast.makeText(this, "Checking whether Android accepted the change…", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                refreshStatus()
                val enabled = try {
                    Settings.Global.getInt(contentResolver, SETTING_ADB_WIFI_ENABLED, 0) == 1
                } catch (_: Exception) { false }
                if (enabled) {
                    Toast.makeText(this, "Wireless debugging setting is ON.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Android turned it back OFF. Open Developer options > Wireless debugging and approve this Wi-Fi network once.", Toast.LENGTH_LONG).show()
                }
            }, 1800)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not enable Wireless debugging", Toast.LENGTH_LONG).show()
        }
    }

    private fun isConnectedToWifi(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun refreshStatus() {
        val enabled = try {
            Settings.Global.getInt(contentResolver, SETTING_ADB_WIFI_ENABLED, 0) == 1
        } catch (_: Exception) { false }
        status.text = if (enabled) "●  ON" else "○  OFF"
        status.setTextColor(if (enabled) Color.rgb(112, 231, 170) else Color.rgb(255, 202, 120))
        permissionStatus.text = if (hasPermission()) {
            "Permission granted. The app can request the Wireless debugging toggle."
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
        private const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"
        private const val GRANT_COMMAND =
            "adb shell pm grant com.alind.autowifidebug android.permission.WRITE_SECURE_SETTINGS"
    }
}
