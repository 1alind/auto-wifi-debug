package com.alind.autowifidebug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Restarts the local monitoring service after Android finishes booting.
 * Android may still delay or restrict background work based on device policy.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        try {
            val serviceIntent = Intent(context, LocalControlServerService::class.java).apply {
                action = "com.alind.autowifidebug.action.START_FROM_BOOT"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "Started monitoring service after boot (action=$action)")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start monitoring service after boot", e)
        }
    }

    companion object {
        private const val TAG = "AutoWifiDebugBoot"
    }
}
