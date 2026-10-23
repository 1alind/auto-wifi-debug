package com.alind.autowifidebug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Starts the monitor after boot. Android and device-vendor background policies may still restrict it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        try {
            val serviceIntent = Intent(context, WifiDebugMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "Started Wi-Fi debugging monitor after boot: " + action)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start monitor after boot", e)
        }
    }

    companion object {
        private const val TAG = "AutoWifiDebugBoot"
    }
}
