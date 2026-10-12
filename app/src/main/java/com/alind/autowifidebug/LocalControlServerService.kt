package com.alind.autowifidebug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Loopback-only HTTP API. The server remains available regardless of Wi-Fi or
 * Wireless debugging state, until an explicit POST /stop request.
 */
class LocalControlServerService : Service() {
    @Volatile private var running = false
    @Volatile private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (worker?.isAlive != true) {
            running = true
            worker = Thread({ serverLoop() }, "AutoWifiDebugHttpServer").apply {
                isDaemon = true
                start()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        closeServer()
        worker?.interrupt()
        super.onDestroy()
    }

    private fun serverLoop() {
        while (running) {
            try {
                if (serverSocket == null) {
                    val socket = ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"))
                    socket.soTimeout = 1000
                    serverSocket = socket
                    Log.i(TAG, "Local API listening on 127.0.0.1:$PORT")
                }

                try {
                    val client = serverSocket?.accept()
                    if (client != null) handleClient(client)
                } catch (_: java.net.SocketTimeoutException) {
                    // Poll the debugging setting regularly.
                } catch (e: Exception) {
                    if (running) Log.w(TAG, "Server accept failed", e)
                    closeServer()
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Log.e(TAG, "Server loop error", e)
                closeServer()
                try { Thread.sleep(POLL_MS) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        closeServer()
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 2500
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
            }

            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore("?")
            val body = when {
                path == "/status" -> json(
                    if (isDebuggingSettingOn()) "SETTING_ON" else "SETTING_OFF",
                    "debugging" to isDebuggingSettingOn().toString(),
                    "server" to "true",
                    "port" to PORT.toString()
                )
                path == "/enable" && (method == "POST" || method == "GET") -> enableDebugging()
                path == "/stop" && method == "POST" -> json("STOPPING", "port" to PORT.toString())
                path == "/" -> json("OK", "port" to PORT.toString(), "endpoints" to "[\"GET /status\",\"POST /enable\",\"POST /stop\"]")
                else -> json("NOT_FOUND", "error" to "\"Use /status, /enable, or /stop\"")
            }
            val statusCode = if (path == "/status" || path == "/enable" || path == "/stop" || path == "/") 200 else 404
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val output = client.getOutputStream()
            output.write(("HTTP/1.1 $statusCode ${if (statusCode == 200) "OK" else "Not Found"}\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
            output.write(bytes)
            output.flush()
            if (path == "/stop" && method == "POST") {
                Log.i(TAG, "Received explicit stop request")
                stopSelf()
            }
        }
    }

    private fun enableDebugging(): String {
        if (!isWifiEnabled()) return json("WIFI_OFF", "message" to "\"Turn Wi-Fi on first\"")
        if (!isConnectedToWifi()) return json("WIFI_NOT_CONNECTED", "message" to "\"Connect to a Wi-Fi network first\"")
        if (checkSelfPermission(WRITE_SECURE_SETTINGS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return json("PERMISSION_MISSING", "message" to "\"Grant WRITE_SECURE_SETTINGS via authorized ADB shell\"")
        }

        return try {
            val wrote = Settings.Global.putInt(contentResolver, SETTING_ADB_WIFI_ENABLED, 1)
            if (!wrote) return json("WRITE_FAILED")
            Thread.sleep(VERIFY_DELAY_MS)
            val enabled = isDebuggingSettingOn()
            if (!enabled) {
                json("SETTING_OFF", "debugging" to "false", "message" to "\"Android did not keep the setting enabled\"")
            } else {
                val endpoint = findWirelessDebuggingEndpoint()
                if (endpoint != null) {
                    val (ip, port) = endpoint
                    json(
                        "SETTING_ON",
                        "debugging" to "true",
                        "ip" to jsonString(ip),
                        "port" to port.toString(),
                        "connect_command" to jsonString("adb connect $ip:$port")
                    )
                } else {
                    json(
                        "SETTING_ON",
                        "debugging" to "true",
                        "ip" to "null",
                        "port" to "null",
                        "connect_command" to "null",
                        "message" to jsonString("Wireless debugging is enabled, but Android did not advertise its connection endpoint via mDNS. Check the Wireless debugging screen.")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not enable debugging setting", e)
            json("WRITE_FAILED")
        }
    }

    /**
     * Finds the actual ADB TLS connect service announced by Android's mDNS.
     * The pairing service uses a different name and is intentionally ignored.
     */
    private fun findWirelessDebuggingEndpoint(): Pair<String, Int>? {
        val nsd = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val latch = CountDownLatch(1)
        val result = AtomicReference<Pair<String, Int>?>(null)
        val resolving = AtomicBoolean(false)
        val discoveryStopped = AtomicBoolean(false)

        val discoveryListener = object : NsdManager.DiscoveryListener {
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
                            if (!address.isNullOrBlank() && port > 0) {
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
            nsd.discoverServices(ADB_CONNECT_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            latch.await(NSD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "ADB mDNS discovery failed", e)
        } finally {
            if (!discoveryStopped.get()) {
                try { nsd.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
            }
        }
        return result.get()
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

    private fun closeServer() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun json(status: String, vararg values: Pair<String, String>): String {
        val extras = values.joinToString(",") { (key, value) -> "\"$key\":$value" }
        return if (extras.isEmpty()) "{\"status\":\"$status\"}"
        else "{\"status\":\"$status\",$extras}"
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Auto Wi-Fi Debug server", NotificationManager.IMPORTANCE_LOW)
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
                .setContentText("Monitoring Wireless debugging; localhost API port $PORT")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Auto Wi-Fi Debug")
                .setContentText("Monitoring Wireless debugging; localhost API port $PORT")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        const val PORT = 23456
        private const val TAG = "AutoWifiDebugServer"
        private const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"
        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        private const val VERIFY_DELAY_MS = 1800L
        private const val POLL_MS = 1000L
        private const val NOTIFICATION_ID = 23456
        private const val CHANNEL_ID = "auto_wifi_debug_server"
        private const val ADB_CONNECT_SERVICE_TYPE = "_adb-tls-connect._tcp."
        private const val NSD_TIMEOUT_SECONDS = 6L
    }
}
