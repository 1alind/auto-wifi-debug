#!/data/data/com.termux/files/usr/bin/bash
# Ask the installed Auto Wi-Fi Debug APK to discover and post the current ADB port.
set -u

APP="com.alind.autowifidebug"
ACTION="com.alind.autowifidebug.CONNECT_PORT"

if ! command -v termux-wifi-enable >/dev/null 2>&1; then
    echo "Termux:API is missing. Install the Termux:API app and run: pkg install termux-api"
    exit 2
fi

echo "Turning Wi-Fi on..."
if ! termux-wifi-enable true; then
    echo "Could not turn Wi-Fi on."
    exit 3
fi

sleep 2
echo "Starting Auto Wi-Fi Debug..."
if ! /system/bin/am start -n "$APP/.MainActivity" >/dev/null 2>&1; then
    echo "Could not open Auto Wi-Fi Debug. Is the APK installed?"
    exit 4
fi

# MainActivity starts the APK's foreground monitoring service.
sleep 3
echo "Requesting the APK to discover the ADB port and send it to Discord..."
/system/bin/am broadcast -a "$ACTION" -p "$APP" >/dev/null 2>&1 || {
    echo "Could not send the request broadcast to the APK."
    exit 5
}

echo "Request sent. The APK will discover the current _adb-tls-connect._tcp endpoint and post:"
echo "connect PORT"
echo "to the configured Discord webhook once it finds the endpoint."
