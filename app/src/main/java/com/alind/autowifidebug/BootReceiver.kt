package com.alind.autowifidebug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the local monitoring service after Android finishes booting.
 * Android may still delay or restrict background work based on device policy.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        try {
            val serviceIntent = Intent(context, LocalControlServerService::class.java)
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "Started monitoring service after boot")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start monitoring service after boot", e)
        }
    }

    companion object {
        private const val TAG = "AutoWifiDebugBoot"
    }
}
