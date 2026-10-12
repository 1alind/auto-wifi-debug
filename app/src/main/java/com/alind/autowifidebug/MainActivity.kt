package com.alind.autowifidebug

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = Color.rgb(17, 29, 49)
  window.navigationBarColor = Color.rgb(17, 29, 49)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(28, 28, 28, 28); setBackgroundColor(Color.rgb(17, 29, 49)) }
  root.addView(TextView(this).apply { text = "Auto Wi-Fi Debug"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
  root.addView(TextView(this).apply { text = "Wireless debugging helper\n\nNo root required. A one-time ADB permission grant is needed. Android may still restrict direct activation."; textSize = 16f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 24, 0, 24) })
  root.addView(Button(this).apply { text = "Open Developer options"; setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) } })
  root.addView(Button(this).apply { text = "Check setup"; setOnClickListener { val ok = checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED; android.widget.Toast.makeText(this@MainActivity, if (ok) "ADB permission granted" else "ADB permission not granted", android.widget.Toast.LENGTH_LONG).show() } })
  setContentView(root)
 }
}
