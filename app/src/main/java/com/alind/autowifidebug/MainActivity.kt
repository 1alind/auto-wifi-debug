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
    private val webhookFilePickerRequest = 7104
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
            text = "Load webhook from port-webhook.txt"
            isAllCaps = false
            setOnClickListener {
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                }
                try {
                    startActivityForResult(picker, webhookFilePickerRequest)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not open file picker: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        })
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
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != webhookFilePickerRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                .orEmpty().trim()
            val value = raw.lineSequence().map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                .orEmpty().let { line ->
                    if (line.startsWith("DISCORD_WEBHOOK_URL=")) line.substringAfter("=")
                        .trim().trim('"', '\\'') else line
                }
            if (!value.startsWith("https://discord.com/api/webhooks/") &&
                !value.startsWith("https://discordapp.com/api/webhooks/")) {
                Toast.makeText(this, "File must contain a Discord webhook URL on one line.", Toast.LENGTH_LONG).show()
                return
            }
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(WifiDebugMonitorService.WEBHOOK_KEY, value).apply()
            webhookInput.setText(value)
            Toast.makeText(this, "Webhook loaded from file and saved.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read webhook file: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
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
