# Auto Wi-Fi Debug

Android app for Android 11+ with a local HTTP API for Termux automation.

## One-time Android permission

After installing the APK, grant the secure-settings permission from an authorized ADB shell:

`adb shell pm grant com.alind.autowifidebug android.permission.WRITE_SECURE_SETTINGS`

This does not require root, but it does require an authorized ADB shell for the one-time grant.

## Termux setup

Install Termux:API and the command-line tools:

`pkg install termux-api curl`

Download the companion client:

```sh
mkdir -p ~/.autowifidebug
curl -L https://raw.githubusercontent.com/1alind/auto-wifi-debug/main/termux/auto-wifi-debug.sh -o ~/.autowifidebug/auto-wifi-debug.sh
chmod +x ~/.autowifidebug/auto-wifi-debug.sh
```

## Run

```sh
bash ~/.autowifidebug/auto-wifi-debug.sh
```

The client turns Wi-Fi on first, opens the APK, then sends an HTTP request to `127.0.0.1:23456/enable`.

## Local API and automatic server lifecycle

The APK hosts a localhost-only HTTP server on port **23456**. It does not listen on the Wi-Fi/LAN interface.

- `GET http://127.0.0.1:23456/status` returns the setting/server status.
- `POST http://127.0.0.1:23456/enable` attempts to set `adb_wifi_enabled=1`.
- The listening socket closes when the `adb_wifi_enabled` setting is on.
- A foreground monitoring service checks the setting every second and reopens the socket when the setting is off.
- Open Auto Wi-Fi Debug at least once to start the monitoring service. Android may stop services if the app is force-stopped or restricted by battery settings.

No Termux RUN_COMMAND permission, broadcast response file, or `allow-external-apps=true` setting is needed for this HTTP approach.

## Important limitation

The server checks the Android global setting `adb_wifi_enabled`. A value of 1 does **not** guarantee the actual Wireless debugging feature is available or reachable. Android/Samsung firmware can override the setting or require approval in Developer options. Test on the Galaxy A50 (Android 11) and Galaxy A52 (Android 14). The app cannot bypass Android's security prompts.

## Build

GitHub Actions builds a debug APK on pushes to `main` and manual workflow runs, and publishes a prerelease APK.
