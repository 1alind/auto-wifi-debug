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
- The listening socket stays available even when Wireless debugging is enabled or Wi-Fi disconnects.
- `POST http://127.0.0.1:23456/stop` explicitly stops the service.
- The app registers a boot receiver for `BOOT_COMPLETED` and vendor `QUICKBOOT_POWERON` broadcasts.
- Open Auto Wi-Fi Debug at least once after installation. On Samsung, set the app battery use to Unrestricted and enable any available Auto start option. Android may still restrict startup if the app is force-stopped.

No Termux RUN_COMMAND permission, broadcast response file, or `allow-external-apps=true` setting is needed for this HTTP approach.

## Important limitation

The server checks the Android global setting `adb_wifi_enabled`. A value of 1 does **not** guarantee the actual Wireless debugging feature is available or reachable. Android/Samsung firmware can override the setting or require approval in Developer options. Test on the Galaxy A50 (Android 11) and Galaxy A52 (Android 14). The app cannot bypass Android's security prompts.

## Build

GitHub Actions builds a debug APK on pushes to `main` and manual workflow runs, and publishes a prerelease APK.
