package com.alind.autowifidebug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast

class DebugCommandReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  if (intent.action != ACTION_ENABLE) return
  try {
   if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
    Toast.makeText(context, "One-time ADB permission setup required", Toast.LENGTH_LONG).show(); return
   }
   val wrote = Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1)
   val enabled = Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
   Log.i("AutoWifiDebug", "settingWrite=$wrote settingRead=$enabled")
   Toast.makeText(context, if (enabled) "Enable request sent" else "Android did not enable Wireless debugging", Toast.LENGTH_LONG).show()
  } catch (e: Exception) { Log.e("AutoWifiDebug", "Enable request failed", e); Toast.makeText(context, "Could not change Wireless debugging", Toast.LENGTH_LONG).show() }
 }
 companion object { const val ACTION_ENABLE = "com.alind.autowifidebug.ENABLE" }
}
