package com.alind.autowifidebug

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PairingCodeAccessibilityService : AccessibilityService() {
    private var lastSentCode = ""
    private var lastNavigationAction = 0L
    private var navigationRequested = false

    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_OPEN_PAIRING_SCREEN) {
                navigationRequested = true
                lastSentCode = ""
                lastNavigationAction = 0L
                Log.i(TAG, "Navigation request received")
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(ACTION_OPEN_PAIRING_SCREEN)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pairingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pairingReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!navigationRequested || event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.android.settings" && pkg != "com.samsung.android.settings") return

        val root = rootInActiveWindow ?: return
        val labels = collectLabels(root)
        val allText = labels.joinToString(" ").lowercase()
        val now = System.currentTimeMillis()

        // The pairing dialog is the destination. Read only its visible six-digit code.
        val code = Regex("\\b[0-9]{6}\\b").find(allText)?.value
        if (code != null && (allText.contains("pair") || allText.contains("code"))) {
            if (code != lastSentCode) {
                lastSentCode = code
                sendBroadcast(
                    Intent(ACTION_PAIRING_CODE)
                        .setPackage(packageName)
                        .putExtra(EXTRA_PAIRING_CODE, code)
                )
                Log.i(TAG, "Pairing code detected and forwarded")
            }
            return
        }

        if (now - lastNavigationAction < 700) return

        // Match the actual visible labels, then click their clickable row/container.
        val pairNode = findMatchingNode(root, PAIR_LABELS)
        if (pairNode != null && clickNodeOrParent(pairNode)) {
            lastNavigationAction = now
            Log.i(TAG, "Opened pairing-code dialog")
            return
        }

        val wirelessNode = findMatchingNode(root, WIRELESS_LABELS)
        if (wirelessNode != null) {
            if (clickNodeOrParent(wirelessNode)) {
                lastNavigationAction = now
                Log.i(TAG, "Opened Wireless debugging")
            }
            return
        }

        val developerNode = findMatchingNode(root, DEVELOPER_LABELS)
        if (developerNode != null) {
            if (clickNodeOrParent(developerNode)) {
                lastNavigationAction = now
                Log.i(TAG, "Opened Developer options")
            }
            return
        }

        // Important: keep scrolling on BOTH the main Settings page and Developer
        // options. Previously scrolling stopped as soon as Developer options opened,
        // so Wireless debugging could remain below the visible area forever.
        if (scrollAnyContainer(root)) {
            lastNavigationAction = now
            Log.i(TAG, "Scrolled Settings while searching for the next target")
        }
    }

    private fun collectLabels(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(result::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(result::add)
        node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let(result::add)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            result.addAll(collectLabels(child))
        }
        return result
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun findMatchingNode(
        node: AccessibilityNodeInfo,
        candidates: List<String>,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > 35) return null
        val raw = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.viewIdResourceName
        ).joinToString(" ")
        val value = normalize(raw)
        if (candidates.any { value.contains(normalize(it)) }) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findMatchingNode(child, candidates, depth + 1)
            if (match != null) return match
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(8) {
            val candidate = current ?: return false
            if (candidate.isEnabled &&
                (candidate.isClickable || candidate.actionList.any {
                    it.id == AccessibilityNodeInfo.ACTION_CLICK
                }) &&
                candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) return true
            current = candidate.parent
        }
        return false
    }

    private fun scrollAnyContainer(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 35) return false
        if (node.isScrollable && node.isEnabled &&
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        ) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scrollAnyContainer(child, depth + 1)) return true
        }
        return false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        try {
            unregisterReceiver(pairingReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoWifiPairingAccess"
        const val ACTION_OPEN_PAIRING_SCREEN = "com.alind.autowifidebug.OPEN_PAIRING_SCREEN"
        const val ACTION_PAIRING_CODE = "com.alind.autowifidebug.PAIRING_CODE"
        const val EXTRA_PAIRING_CODE = "pairing_code"

        private val DEVELOPER_LABELS = listOf(
            "Developer options",
            "Developer mode",
            "Developer settings"
        )
        private val WIRELESS_LABELS = listOf(
            "Wireless debugging",
            "Wireless debug",
            "Wi-Fi debugging",
            "WiFi debugging",
            "ADB over Wi-Fi",
            "ADB over wireless"
        )
        private val PAIR_LABELS = listOf(
            "Pair device with pairing code",
            "Pair with pairing code",
            "Pair using pairing code",
            "Pair device"
        )
    }
}
