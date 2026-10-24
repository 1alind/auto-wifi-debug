package com.alind.autowifidebug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Monitors Wi-Fi and Wireless debugging, then posts the current ADB port to Discord.
 * No HTTP control server is hosted by this service.
 */
class WifiDebugMonitorService : Service() {
    @Volatile private var serviceRunning = false
    private var monitorThread: Thread? = null
    @Volatile private var receiverLastWifiConnected = false
    private val wifiReconnectEvent = AtomicBoolean(false)

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connected = isConnectedToWifi()
            val wasConnected = receiverLastWifiConnected
            receiverLastWifiConnected = connected
            if (connected && !wasConnected) {
                wifiReconnectEvent.set(true)
                Log.i(TAG, "Wi-Fi reconnect event received")
            } else {
                Log.i(TAG, "Connectivity event: " + intent.action)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        receiverLastWifiConnected = isConnectedToWifi()
        registerConnectivityReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceRunning = true
        if (monitorThread?.isAlive != true) {
            monitorThread = Thread({ monitorLoop() }, "WifiDebugDiscordMonitor").apply {
                isDaemon = true
                start()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceRunning = false
        monitorThread?.interrupt()
        try {
            unregisterReceiver(connectivityReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was not registered.
        }
        super.onDestroy()
    }

    private fun registerConnectivityReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(connectivityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(connectivityReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register connectivity listener", e)
        }
    }

    private fun monitorLoop() {
        var previousWifiConnected = false
        var previousDebugSetting = false
        var announcePending = false
        var lastEnableAttempt = 0L
        var lastDiscoveryTime = 0L
        var lastAnnouncedEndpoint: Pair<String, Int>? = null

        while (serviceRunning) {
            try {
                val wifiConnected = isConnectedToWifi()
                val debugSetting = isDebuggingSettingOn()
                val reconnectEvent = wifiReconnectEvent.getAndSet(false)

                val wifiJustReconnected = wifiConnected && (!previousWifiConnected || reconnectEvent)
                if (wifiJustReconnected) {
                    announcePending = true
                    // Do not wait for the normal retry interval after Wi-Fi returns.
                    lastEnableAttempt = 0L
                    Log.i(TAG, "Wi-Fi connected; immediately rechecking Wireless debugging")
                }

                if (debugSetting && !previousDebugSetting) {
                    announcePending = true
                    Log.i(TAG, "Wireless debugging setting changed to ON")
                }

                if (wifiConnected &&
                    (wifiJustReconnected || !debugSetting) &&
                    System.currentTimeMillis() - lastEnableAttempt >= ENABLE_RETRY_MS) {
                    lastEnableAttempt = System.currentTimeMillis()
                    attemptEnableDebugging()
                }

                val currentDebugSetting = isDebuggingSettingOn()
                if (currentDebugSetting && !previousDebugSetting) {
                    announcePending = true
                }

                val now = System.currentTimeMillis()
                val shouldDiscover = announcePending || now - lastDiscoveryTime >= ENDPOINT_POLL_MS
                if (wifiConnected && currentDebugSetting && shouldDiscover) {
                    lastDiscoveryTime = now
                    val endpoint = findWirelessDebuggingEndpoint()
                    if (endpoint != null) {
                        if (endpoint != lastAnnouncedEndpoint) announcePending = true
                        if (announcePending) {
                            val webhook = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .getString(WEBHOOK_KEY, "").orEmpty().trim()
                            if (webhook.isNotEmpty()) {
                                if (sendDiscordMessage(webhook, "connect " + endpoint.second)) {
                                    Log.i(TAG, "Sent current ADB port to Discord: " + endpoint.second)
                                    announcePending = false
                                    lastAnnouncedEndpoint = endpoint
                                } else {
                                    Log.w(TAG, "Discord webhook failed; will retry")
                                }
                            } else {
                                Log.w(TAG, "Discord webhook URL not configured; waiting for it to be saved")
                            }
                        }
                    } else {
                        Log.i(TAG, "ADB mDNS endpoint not found yet; will retry")
                    }
                }

                previousWifiConnected = wifiConnected
                previousDebugSetting = currentDebugSetting
                Thread.sleep(MONITOR_INTERVAL_MS)
            } catch (_: InterruptedException) {
                if (!serviceRunning) break
            } catch (e: Exception) {
                Log.e(TAG, "Monitor loop error", e)
                try { Thread.sleep(MONITOR_INTERVAL_MS) } catch (_: InterruptedException) {
                    if (!serviceRunning) break
                }
            }
        }
    }

    private fun attemptEnableDebugging() {
        if (!isWifiEnabled() || !isConnectedToWifi()) return
        if (checkSelfPermission(WRITE_SECURE_SETTINGS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS is missing; cannot request Wireless debugging")
            return
        }
        try {
            val wrote = Settings.Global.putInt(contentResolver, SETTING_ADB_WIFI_ENABLED, 1)
            Log.i(TAG, "Requested Wireless debugging ON; setting write accepted=" + wrote)
        } catch (e: Exception) {
            Log.e(TAG, "Could not request Wireless debugging ON", e)
        }
    }

    private fun findWirelessDebuggingEndpoint(): Pair<String, Int>? {
        val nsd = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val latch = CountDownLatch(1)
        val result = AtomicReference<Pair<String, Int>?>(null)
        val resolving = AtomicBoolean(false)
        val discoveryStopped = AtomicBoolean(false)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains(ADB_CONNECT_SERVICE_TYPE) ||
                    !resolving.compareAndSet(false, true)) return
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            resolving.set(false)
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val address = info.host?.hostAddress
                            val port = info.port
                            if (!address.isNullOrBlank() && port in 1..65535) {
                                result.set(address to port)
                                latch.countDown()
                            } else {
                                resolving.set(false)
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "Could not resolve ADB mDNS service", e)
                    resolving.set(false)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) { discoveryStopped.set(true) }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
                latch.countDown()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
        }

        try {
            nsd.discoverServices(ADB_CONNECT_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(NSD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "ADB mDNS discovery failed", e)
        } finally {
            if (!discoveryStopped.get()) {
                try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
            }
        }
        return result.get()
    }

    private fun sendDiscordMessage(webhookUrl: String, message: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(webhookUrl)
            val allowedHost = url.host.equals("discord.com", ignoreCase = true) ||
                url.host.equals("discordapp.com", ignoreCase = true)
            if (url.protocol != "https" || !allowedHost) {
                Log.e(TAG, "Webhook URL must be an HTTPS Discord webhook URL")
                return false
            }
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "Auto-WiFi-Debug")
            }
            val body = JSONObject().put("content", message).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }
            connection.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Discord webhook request failed", e)
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun isDebuggingSettingOn(): Boolean = try {
        Settings.Global.getInt(contentResolver, SETTING_ADB_WIFI_ENABLED, 0) == 1
    } catch (_: Exception) { false }

    private fun isWifiEnabled(): Boolean = try {
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (_: Exception) { false }

    private fun isConnectedToWifi(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Wi-Fi Debug monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Auto Wi-Fi Debug")
                .setContentText("Monitoring Wireless debugging and Discord port updates")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Auto Wi-Fi Debug")
                .setContentText("Monitoring Wireless debugging and Discord port updates")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val TAG = "AutoWifiDebugMonitor"
        private const val PREFS = "auto_wifi_debug"
        const val WEBHOOK_KEY = "discord_webhook_url"
        private const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"
        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        private const val ADB_CONNECT_SERVICE_TYPE = "_adb-tls-connect._tcp."
        private const val NSD_TIMEOUT_SECONDS = 6L
        private const val MONITOR_INTERVAL_MS = 3000L
        private const val ENDPOINT_POLL_MS = 10000L
        private const val ENABLE_RETRY_MS = 10000L
        private const val NOTIFICATION_ID = 23456
        private const val CHANNEL_ID = "wifi_debug_monitor"
    }
}
