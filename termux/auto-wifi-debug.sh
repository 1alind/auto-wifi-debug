#!/data/data/com.termux/files/usr/bin/bash
# Auto Wi-Fi Debug Termux companion.
# Usage: bash ~/.autowifidebug/auto-wifi-debug.sh
# The APK invokes this same file with --reply <request-id> <status>.

set -u

BASE="$HOME/.autowifidebug"
RESULTS="$BASE/results"
APK_ACTION="com.alind.autowifidebug.ENABLE"

if [[ "${1:-}" == "--reply" ]]; then
    request_id="${2:-}"
    status="${3:-}"
    [[ "$request_id" =~ ^[A-Za-z0-9_-]{1,64}$ ]] || exit 2
    case "$status" in
        WIFI_OFF|WIFI_NOT_CONNECTED|PERMISSION_MISSING|WRITE_FAILED|VERIFY_FAILED|SETTING_ON|SETTING_OFF) ;;
        *) exit 2 ;;
    esac
    mkdir -p "$RESULTS" || exit 1
    printf '%s\n' "$status" > "$RESULTS/$request_id"
    exit $?
fi

mkdir -p "$RESULTS" || exit 1

request_apk() {
    local request_id result_file attempt
    request_id="$(date +%s)-$$-$RANDOM"
    result_file="$RESULTS/$request_id"
    rm -f "$result_file"

    /system/bin/am broadcast -a "$APK_ACTION" --es request_id "$request_id" >/dev/null 2>&1

    for attempt in {1..10}; do
        if [[ -s "$result_file" ]]; then
            cat "$result_file"
            rm -f "$result_file"
            return 0
        fi
        sleep 1
    done

    rm -f "$result_file"
    return 1
}

echo "Checking Wi-Fi and requesting Wireless debugging..."
status="$(request_apk)" || {
    echo "No reply from the APK."
    echo "Check that the APK is installed, Termux RUN_COMMAND permission is granted to it,"
    echo "and allow-external-apps=true is set in ~/.termux/termux.properties."
    exit 2
}

if [[ "$status" == "WIFI_OFF" ]]; then
    echo "Wi-Fi is off. Asking Termux:API to turn it on..."
    if ! command -v termux-wifi-enable >/dev/null 2>&1; then
        echo "termux-wifi-enable is missing. Install the termux-api package in Termux."
        exit 3
    fi
    if ! termux-wifi-enable true; then
        echo "Termux:API could not enable Wi-Fi."
        exit 4
    fi

    echo "Waiting for Wi-Fi to connect..."
    for attempt in {1..10}; do
        sleep 2
        status="$(request_apk)" || {
            echo "No reply from the APK while waiting for Wi-Fi."
            exit 2
        }
        [[ "$status" == "WIFI_NOT_CONNECTED" ]] && continue
        [[ "$status" == "WIFI_OFF" ]] && continue
        break
    done
fi

case "$status" in
    SETTING_ON)
        echo "Android reports the Wireless debugging setting is ON."
        echo "Please verify the actual toggle; Android/Samsung may still override it."
        ;;
    SETTING_OFF)
        echo "Android turned Wireless debugging back OFF."
        echo "Open Developer options > Wireless debugging and approve this Wi-Fi network once."
        exit 5
        ;;
    WIFI_OFF)
        echo "Wi-Fi is still off. Check Termux:API and Android Wi-Fi permissions."
        exit 6
        ;;
    WIFI_NOT_CONNECTED)
        echo "Wi-Fi is on, but the phone is not connected to a Wi-Fi network."
        echo "Connect to a network and run this command again."
        exit 7
        ;;
    PERMISSION_MISSING)
        echo "The APK needs WRITE_SECURE_SETTINGS permission."
        echo "Grant it once using an authorized ADB shell; see the README."
        exit 8
        ;;
    WRITE_FAILED|VERIFY_FAILED)
        echo "The APK could not change or verify the Wireless debugging setting: $status"
        exit 9
        ;;
    *)
        echo "Unexpected response from APK: $status"
        exit 10
        ;;
esac
