# Auto Wi-Fi Debug

Android app starter for Android 11+ with a simple interface and an automation broadcast receiver.

## One-time permission setup

After installing, grant the secure-settings permission from an authorized ADB shell:

`adb shell pm grant com.alind.autowifidebug android.permission.WRITE_SECURE_SETTINGS`

This does not require root, but it does require an ADB shell for the one-time grant.

## Automation action

`com.alind.autowifidebug.ENABLE`

Package: `com.alind.autowifidebug`. Receiver: `com.alind.autowifidebug/.DebugCommandReceiver`.

The receiver attempts to write Android global setting `adb_wifi_enabled=1`. **This is not guaranteed to enable Wireless debugging**: Android/Samsung firmware may ignore or override this setting and can require a system-controlled confirmation. This must be tested on the Galaxy A50 Android 11 and Galaxy A52 Android 14.

## Build

GitHub Actions builds a debug APK on pushes to `main` and manual workflow runs. Download the `auto-wifi-debug-apk` artifact from the Actions run.
