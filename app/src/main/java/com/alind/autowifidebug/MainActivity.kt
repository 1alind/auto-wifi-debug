package com.alind.autowifidebug

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.net.Uri
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
    private lateinit var webhookStatus: TextView

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
        webhookStatus = text("Checking webhook.json…", 15f, Color.WHITE, true)
        webhookCard.addView(webhookStatus)
        webhookCard.addView(space(6))
        webhookCard.addView(text("Automatically reads /sdcard/autoclick/config/webhook.json", 13f, Color.rgb(170, 210, 190), false))
        webhookCard.addView(space(8))
        webhookCard.addView(Button(this).apply {
            text = "Open storage permission settings"
            isAllCaps = false
            setOnClickListener { openStoragePermissionSettings() }
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
            text = "Check for Update"
            isAllCaps = false
            setOnClickListener { checkForUpdate() }
        }, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(space(12))

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
        refreshWebhookStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                startMonitorService()
            }
            refreshStatus()
            refreshWebhookStatus()
        }
    }

    private fun openStoragePermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            refreshWebhookStatus()
            return
        }
        try {
            startActivity(Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open storage settings: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkForUpdate() {
        Toast.makeText(this, "Checking GitHub for the latest release…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val api = URL("https://api.github.com/repos/1alind/auto-wifi-debug/releases/latest")
                val connection = api.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                val releaseText = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val release = JSONObject(releaseText)
                val latestTag = release.optString("tag_name", "latest")
                val assets = release.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (apkUrl.isNullOrBlank()) throw IllegalStateException("No APK asset found in GitHub Release.")
                val apk = File(cacheDir, "auto-wifi-debug-update.apk")
                val download = URL(apkUrl).openConnection() as HttpURLConnection
                download.connectTimeout = 15000
                download.readTimeout = 30000
                download.instanceFollowRedirects = true
                download.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
                download.disconnect()
                if (!apk.exists() || apk.length() < 100_000L) {
                    apk.delete()
                    throw IllegalStateException("Downloaded APK looks incomplete.")
                }
                runOnUiThread {
                    try {
                        stopService(Intent(this, WifiDebugMonitorService::class.java))
                        val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", apk)
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        Toast.makeText(this, "Downloaded " + latestTag + ". Confirm installation in Android.", Toast.LENGTH_LONG).show()
                        startActivity(installIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open installer: " + e.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Update failed: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show() }
            }
        }.start()
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

    private fun refreshWebhookStatus() {
        val file = File("/sdcard/autoclick/config/webhook.json")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            webhookStatus.text = "⚠ All files access permission is missing"
            webhookStatus.setTextColor(Color.rgb(255, 202, 120))
            return
        }
        if (!file.isFile || !file.canRead()) {
            webhookStatus.text = "✕ File not found or cannot be read"
            webhookStatus.setTextColor(Color.rgb(255, 130, 130))
            return
        }
        try {
            val root = org.json.JSONTokener(file.readText(Charsets.UTF_8)).nextValue()
            val pattern = Regex(
                """https://(?:discord\.com|discordapp\.com)/api/webhooks/[0-9]+/[A-Za-z0-9._-]+""",
                RegexOption.IGNORE_CASE
            )
            fun find(value: Any?): String? {
                when (value) {
                    is JSONObject -> {
                        val keys = value.keys()
                        while (keys.hasNext()) find(value.opt(keys.next()))?.let { return it }
                    }
                    is org.json.JSONArray -> {
                        for (i in 0 until value.length()) find(value.opt(i))?.let { return it }
                    }
                    is String -> return pattern.find(value)?.value
                }
                return null
            }
            if (find(root) != null) {
                webhookStatus.text = "✓ Webhook URL found in file"
                webhookStatus.setTextColor(Color.rgb(112, 231, 170))
            } else {
                webhookStatus.text = "✕ No Discord webhook URL found in file"
                webhookStatus.setTextColor(Color.rgb(255, 130, 130))
            }
        } catch (e: Exception) {
            webhookStatus.text = "✕ Invalid or unreadable JSON file"
            webhookStatus.setTextColor(Color.rgb(255, 130, 130))
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
