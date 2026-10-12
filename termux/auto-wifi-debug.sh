#!/data/data/com.termux/files/usr/bin/bash
# Auto Wi-Fi Debug HTTP client. The APK hosts a loopback-only API on port 23456.
set -u

API="http://127.0.0.1:23456"
command -v curl >/dev/null 2>&1 || {
    echo "curl is missing. Install it with: pkg install curl"
    exit 2
}

echo "Turning Wi-Fi on first..."
if ! command -v termux-wifi-enable >/dev/null 2>&1; then
    echo "termux-wifi-enable is missing. Install Termux:API and run: pkg install termux-api"
    exit 3
fi
if ! termux-wifi-enable true; then
    echo "Termux:API could not turn Wi-Fi on."
    exit 4
fi

sleep 2
echo "Opening Auto Wi-Fi Debug to start its local server..."
/system/bin/am start -n com.alind.autowifidebug/.MainActivity >/dev/null 2>&1 || true
sleep 2

echo "Sending request to 127.0.0.1:23456..."
response=""
for attempt in {1..10}; do
    response="$(curl --silent --show-error --connect-timeout 1 --max-time 12 -X POST "$API/enable" 2>/dev/null)" && [[ -n "$response" ]] && break
    sleep 1
done

if [[ -z "$response" ]]; then
    echo "No server response on port 23456."
    echo "Open Auto Wi-Fi Debug and check whether Android allowed its foreground service."
    echo "Otherwise open Auto Wi-Fi Debug and check whether Android allowed its foreground service."
    exit 5
fi

echo "Server response: $response"
status="$(printf '%s' "$response" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')"

case "$status" in
    SETTING_ON)
        ip="$(printf '%s' "$response" | sed -n 's/.*"ip":"\([^"]*\)".*/\1/p')"
        port="$(printf '%s' "$response" | sed -n 's/.*"port":\([0-9][0-9]*\).*/\1/p')"
        if [[ -n "$ip" && "$ip" != "null" && -n "$port" && "$port" != "null" ]]; then
            echo "Wireless debugging endpoint: $ip:$port"
            if command -v adb >/dev/null 2>&1; then
                echo "Connecting with ADB..."
                adb connect "$ip:$port"
            else
                echo "ADB is not installed in Termux. Install Android platform-tools to connect:"
                echo "pkg install android-tools"
                echo "Then run: adb connect $ip:$port"
            fi
        else
            echo "Wireless debugging setting is ON, but Android did not provide an ADB connection IP/port."
            echo "Open Developer options > Wireless debugging and confirm it is actually active."
            echo "Android must advertise _adb-tls-connect._tcp via mDNS for automatic discovery to work."
        fi
        echo "The local HTTP server stays available. Stop it explicitly with: curl -X POST http://127.0.0.1:23456/stop"
        ;;
    WIFI_OFF)
        echo "Wi-Fi is off. Re-run the command after enabling Wi-Fi."
        exit 6
        ;;
    WIFI_NOT_CONNECTED)
        echo "Wi-Fi is on but not connected to a network. Connect, then retry."
        exit 7
        ;;
    PERMISSION_MISSING)
        echo "Grant the one-time permission from an authorized ADB shell:"
        echo "adb shell pm grant com.alind.autowifidebug android.permission.WRITE_SECURE_SETTINGS"
        exit 8
        ;;
    SETTING_OFF)
        echo "Android did not keep the setting enabled. Approve Wireless debugging for this Wi-Fi network in Developer options."
        exit 9
        ;;
    WRITE_FAILED)
        echo "Android rejected the setting change."
        exit 10
        ;;
    *)
        echo "Unexpected server response."
        exit 11
        ;;
esac
