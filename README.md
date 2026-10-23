# Auto Wi-Fi Debug

Android app for automatically monitoring Wireless debugging and posting its current ADB port to a Discord webhook.

## One-time Android permission

After installing the APK, grant the secure-settings permission from an authorized ADB shell:

`adb shell pm grant com.alind.autowifidebug android.permission.WRITE_SECURE_SETTINGS`

This does not require root, but it does require an authorized ADB shell for the one-time grant.

## Configure Discord

1. Create a Discord webhook for the channel that should receive the port.
2. Open Auto Wi-Fi Debug and paste the webhook URL in the **Discord Webhook** field.
3. Tap **Save webhook URL**.

The app sends only messages formatted like `connect 45827`. Keep the webhook URL secret: anyone who obtains it can post to that channel. The URL is stored in the app's private preferences and is not hardcoded into the APK.

## Behavior

- The app starts a foreground monitoring service when launched.
- A boot receiver attempts to start the service after reboot. On Samsung, set battery usage to Unrestricted and enable any available Auto start option.
- When Wi-Fi is connected, the service attempts to set Android's `adb_wifi_enabled` setting to on, provided `WRITE_SECURE_SETTINGS` was granted.
- It discovers the actual ADB TLS endpoint using Android NSD/mDNS service `_adb-tls-connect._tcp.`. It does not scan all ports.
- When debugging becomes enabled or Wi-Fi reconnects, it rediscovers the endpoint and posts `connect PORT` to Discord.
- If discovery or webhook delivery fails, it retries. It does not intentionally send repeated messages after a successful send for the same observed event.
- The app does not host an HTTP server and does not run a Termux script.

## Limitations

A value of 1 for `adb_wifi_enabled` does not guarantee that Wireless debugging is actually available. Android/Samsung firmware can override the setting or require confirmation in Developer options. NSD discovery, webhook delivery, and boot startup can also be delayed or restricted by Android. The app retries, but it cannot bypass system security restrictions.

Test on the target phone after installing, granting permission, configuring the webhook, and rebooting.

## Build

GitHub Actions builds a debug APK on pushes to `main` and publishes a prerelease APK.
