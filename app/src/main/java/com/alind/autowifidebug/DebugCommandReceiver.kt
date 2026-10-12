package com.alind.autowifidebug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast

class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ENABLE) return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
            ?.takeIf { REQUEST_ID_PATTERN.matches(it) }

        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            respond(context, requestId, "PERMISSION_MISSING")
            Toast.makeText(context, "One-time ADB permission setup required", Toast.LENGTH_LONG).show()
            return
        }

        if (!isWifiEnabled(context)) {
            respond(context, requestId, "WIFI_OFF")
            Toast.makeText(context, "Wi-Fi is off. Termux can turn it on; retry afterward.", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Wi-Fi is off; requestId=$requestId")
            return
        }

        if (!isConnectedToWifi(context)) {
            respond(context, requestId, "WIFI_NOT_CONNECTED")
            Toast.makeText(context, "Wi-Fi is on, but not connected to a network.", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Wi-Fi is enabled but not connected; requestId=$requestId")
            return
        }

        val pending = goAsync()
        try {
            val wrote = Settings.Global.putInt(context.contentResolver, SETTING_ADB_WIFI_ENABLED, 1)
            Log.i(TAG, "settingWrite=$wrote requestId=$requestId")
            if (!wrote) {
                respond(context, requestId, "WRITE_FAILED")
                pending.finish()
                return
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val settingOn = Settings.Global.getInt(
                        context.contentResolver,
                        SETTING_ADB_WIFI_ENABLED,
                        0
                    ) == 1
                    val result = if (settingOn) "SETTING_ON" else "SETTING_OFF"
                    respond(context, requestId, result)
                    Log.i(TAG, "result=$result requestId=$requestId")

                    val message = if (settingOn) {
                        "Wireless debugging setting is ON; verify the actual system toggle."
                    } else {
                        "Android turned it back OFF. Approve this Wi-Fi network in Developer options once."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not verify Wireless debugging state", e)
                    respond(context, requestId, "VERIFY_FAILED")
                    Toast.makeText(context, "Could not verify Wireless debugging state", Toast.LENGTH_LONG).show()
                } finally {
                    pending.finish()
                }
            }, VERIFY_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Enable request failed", e)
            respond(context, requestId, "WRITE_FAILED")
            Toast.makeText(context, "Could not change Wireless debugging", Toast.LENGTH_LONG).show()
            pending.finish()
        }
    }

    private fun isWifiEnabled(context: Context): Boolean = try {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        manager.isWifiEnabled
    } catch (e: Exception) {
        Log.e(TAG, "Could not read Wi-Fi state", e)
        false
    }

    private fun isConnectedToWifi(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun respond(context: Context, requestId: String?, result: String) {
        if (requestId == null) return
        val reportIntent = Intent().apply {
            setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
            action = TERMUX_RUN_COMMAND_ACTION
            putExtra(TERMUX_EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
            putExtra(
                TERMUX_EXTRA_ARGUMENTS,
                arrayOf(TERMUX_RESPONSE_SCRIPT, "--reply", requestId, result)
            )
            putExtra(TERMUX_EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(TERMUX_EXTRA_BACKGROUND, true)
            putExtra(TERMUX_EXTRA_COMMAND_LABEL, "Auto Wi-Fi Debug status reply")
            putExtra(TERMUX_EXTRA_COMMAND_DESCRIPTION, "Writes a status reply for the pending Auto Wi-Fi Debug request.")
        }
        try {
            context.startService(reportIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not reply to Termux. Check Termux RUN_COMMAND permission and allow-external-apps.", e)
        }
    }

    companion object {
        const val ACTION_ENABLE = "com.alind.autowifidebug.ENABLE"
        const val EXTRA_REQUEST_ID = "request_id"

        private const val TAG = "AutoWifiDebug"
        private const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"
        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        private const val VERIFY_DELAY_MS = 1800L
        private val REQUEST_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        private const val TERMUX_EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val TERMUX_EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val TERMUX_EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val TERMUX_EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val TERMUX_EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
        private const val TERMUX_EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
        private const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
        private const val TERMUX_RESPONSE_SCRIPT = "/data/data/com.termux/files/home/.autowifidebug/auto-wifi-debug.sh"
    }
}
