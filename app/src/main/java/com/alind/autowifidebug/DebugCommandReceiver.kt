package com.alind.autowifidebug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast

class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ENABLE) return
        if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "One-time ADB permission setup required", Toast.LENGTH_LONG).show()
            return
        }
        if (!isConnectedToWifi(context)) {
            Toast.makeText(context, "Connect to Wi-Fi first", Toast.LENGTH_LONG).show()
            Log.w("AutoWifiDebug", "Wireless debugging enable skipped: no active Wi-Fi network")
            return
        }

        val pending = goAsync()
        try {
            val wrote = Settings.Global.putInt(context.contentResolver, SETTING_ADB_WIFI_ENABLED, 1)
            Log.i("AutoWifiDebug", "settingWrite=$wrote")
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val enabled = Settings.Global.getInt(context.contentResolver, SETTING_ADB_WIFI_ENABLED, 0) == 1
                    Log.i("AutoWifiDebug", "settingReadAfterDelay=$enabled")
                    val message = if (enabled) {
                        "Wireless debugging setting is ON"
                    } else {
                        "Android turned it back OFF. Approve this Wi-Fi network in Developer options once."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("AutoWifiDebug", "Could not verify wireless debugging state", e)
                    Toast.makeText(context, "Could not verify Wireless debugging state", Toast.LENGTH_LONG).show()
                } finally {
                    pending.finish()
                }
            }, 1800)
        } catch (e: Exception) {
            Log.e("AutoWifiDebug", "Enable request failed", e)
            Toast.makeText(context, "Could not change Wireless debugging", Toast.LENGTH_LONG).show()
            pending.finish()
        }
    }

    private fun isConnectedToWifi(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        private const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"
        const val ACTION_ENABLE = "com.alind.autowifidebug.ENABLE"
    }
}
