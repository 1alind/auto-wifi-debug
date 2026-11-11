package com.alind.autowifidebug

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class PairingCodeAccessibilityService : AccessibilityService() {
    private var lastSentCode = ""
    private var lastNavigationAction = 0L
    private var navigationRequested = false

    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_OPEN_PAIRING_SCREEN) return
            navigationRequested = true
            lastSentCode = ""
            val settings = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { startActivity(settings) } catch (e: Exception) {
                Log.e(TAG, "Could not open Developer options", e)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(ACTION_OPEN_PAIRING_SCREEN)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pairingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(pairingReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!navigationRequested || event == null) return
        val root = rootInActiveWindow ?: return
        val allText = collectText(root).joinToString(" ").lowercase()
        val now = System.currentTimeMillis()

        val codeMatch = Regex("\\b[0-9]{6}\\b").find(allText)
        if (codeMatch != null && (allText.contains("pair") || allText.contains("code"))) {
            val code = codeMatch.value
            if (code != lastSentCode) {
                lastSentCode = code
                sendBroadcast(Intent(ACTION_PAIRING_CODE).setPackage(packageName).putExtra(EXTRA_PAIRING_CODE, code))
                Log.i(TAG, "Detected pairing code and forwarded it to monitor")
            }
            return
        }

        if (now - lastNavigationAction < 900) return
        val pairButton = findNode(root, listOf("pair device with pairing code", "pair with pairing code"))
        if (pairButton != null) {
            if (clickNode(pairButton)) lastNavigationAction = now
            return
        }
        val wireless = findNode(root, listOf("wireless debugging"))
        if (wireless != null) {
            if (clickNode(wireless)) lastNavigationAction = now
        }
    }

    private fun collectText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        node.text?.toString()?.let { result.add(it) }
        node.contentDescription?.toString()?.let { result.add(it) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { result.addAll(collectText(it)) }
        return result
    }

    private fun findNode(node: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        val value = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        if (labels.any { value.contains(it) }) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, labels)
            if (found != null) return found
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
        }
        return false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        try { unregisterReceiver(pairingReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoWifiPairingAccess"
        const val ACTION_OPEN_PAIRING_SCREEN = "com.alind.autowifidebug.OPEN_PAIRING_SCREEN"
        const val ACTION_PAIRING_CODE = "com.alind.autowifidebug.PAIRING_CODE"
        const val EXTRA_PAIRING_CODE = "pairing_code"
    }
}
