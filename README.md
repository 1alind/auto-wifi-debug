# Auto Wi-Fi Debug

Android app for Android 11+ with a simple interface and a broadcast API for Termux automation.

## One-time Android permission

After installing the APK, grant the secure-settings permission from an authorized ADB shell:

`adb shell pm grant com.alind.autowifidebug android.permission.WRITE_SECURE_SETTINGS`

This does not require root, but it does require an authorized ADB shell for the one-time grant.

## Termux bridge setup

The APK sends request results back into Termux using Termux's documented `RUN_COMMAND` integration. This requires a one-time setup:

1. Make sure both Termux and Termux:API are installed from compatible sources, and install the Termux API command package:
   `pkg install termux-api curl`
2. In Termux, edit `~/.termux/termux.properties` and set:
   `allow-external-apps=true`
   Create the `~/.termux` directory first if needed. Restart Termux after changing this setting.
3. Open Android Settings → Apps → Auto Wi-Fi Debug → Permissions → Additional permissions, and grant **Run commands in Termux environment** if that permission is shown.
4. Install the companion script:
   ```sh
   mkdir -p ~/.autowifidebug
   curl -L https://raw.githubusercontent.com/1alind/auto-wifi-debug/main/termux/auto-wifi-debug.sh -o ~/.autowifidebug/auto-wifi-debug.sh
   ```

The RUN_COMMAND permission is powerful because Android allows the app to start commands in Termux's user context. Only grant it to an APK you trust. This app invokes a fixed script path and passes a validated request ID and fixed status values.

## Run the workflow

In Termux, run:

```sh
bash ~/.autowifidebug/auto-wifi-debug.sh
```

The script sends a request to the APK. The APK checks Wi-Fi and replies with a status file written through Termux's RUN_COMMAND service. If Wi-Fi is off, the script runs `termux-wifi-enable true`, waits, and retries the request. If Wi-Fi is on and connected, the APK attempts to set `adb_wifi_enabled=1` and returns the verified setting value.

## Broadcast API

- Action: `com.alind.autowifidebug.ENABLE`
- Extra: `request_id` (letters, digits, underscore, or hyphen; 1–64 characters)
- Response file: `~/.autowifidebug/results/<request_id>`
- Possible responses: `WIFI_OFF`, `WIFI_NOT_CONNECTED`, `PERMISSION_MISSING`, `WRITE_FAILED`, `VERIFY_FAILED`, `SETTING_ON`, `SETTING_OFF`

Example request (the companion script handles response tracking):

```sh
am broadcast -a com.alind.autowifidebug.ENABLE --es request_id test_123
```

## Important limitation

`SETTING_ON` means Android's `adb_wifi_enabled` setting remained on after a short delay. It does **not** prove that Wireless debugging is actually available or that the ADB port is reachable. Android/Samsung firmware can override the setting or require a one-time approval in Developer options. Test on the Galaxy A50 (Android 11) and Galaxy A52 (Android 14).

## Build

GitHub Actions builds a debug APK on pushes to `main` and manual workflow runs, and publishes a prerelease APK.
