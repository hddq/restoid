#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="${1:-fastlane/metadata/android/en-US/images/phoneScreenshots}"
PACKAGE_ID="io.github.hddq.restoid.debug"
TEST_CLASS="io.github.hddq.restoid.screenshots.StoreScreenshotsTest"
DEVICE_SCREENSHOT_DIR="/sdcard/Android/data/${PACKAGE_ID}/files/screenshots"

ADB_CMD=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB_CMD+=(-s "$ANDROID_SERIAL")
fi

# Verify exactly one target device is reachable if ANDROID_SERIAL is not set.
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    if [[ ${#DEVICES[@]} -eq 0 ]]; then
        echo "Error: No connected Android device or emulator found via adb." >&2
        exit 1
    elif [[ ${#DEVICES[@]} -gt 1 ]]; then
        echo "Error: Multiple devices connected (${DEVICES[*]}). Set ANDROID_SERIAL to choose one." >&2
        exit 1
    fi
    ADB_CMD+=(-s "${DEVICES[0]}")
fi

IS_EMULATOR=$("${ADB_CMD[@]}" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r[:space:]' || true)
PREV_NIGHT_MODE=$("${ADB_CMD[@]}" shell cmd uimode night 2>/dev/null | tr -d '\r' | awk -F': ' '{print $NF}' | tr -d '[:space:]' || true)
HIDE_ERROR_DIALOGS_READ_OK=0
PREV_HIDE_ERROR_DIALOGS=""
if RAW_HIDE_ERROR_DIALOGS=$("${ADB_CMD[@]}" shell settings get global hide_error_dialogs 2>/dev/null); then
    PREV_HIDE_ERROR_DIALOGS=$(printf '%s' "$RAW_HIDE_ERROR_DIALOGS" | tr -d '\r[:space:]')
    HIDE_ERROR_DIALOGS_READ_OK=1
fi

cleanup() {
    "${ADB_CMD[@]}" shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
    if [[ "$HIDE_ERROR_DIALOGS_READ_OK" == "1" ]]; then
        if [[ "$PREV_HIDE_ERROR_DIALOGS" == "null" || -z "$PREV_HIDE_ERROR_DIALOGS" ]]; then
            "${ADB_CMD[@]}" shell settings delete global hide_error_dialogs >/dev/null 2>&1 || true
        else
            "${ADB_CMD[@]}" shell settings put global hide_error_dialogs "$PREV_HIDE_ERROR_DIALOGS" >/dev/null 2>&1 || true
        fi
    fi
    case "$PREV_NIGHT_MODE" in
        no|auto|custom_schedule|custom_bedtime)
            "${ADB_CMD[@]}" shell cmd uimode night "$PREV_NIGHT_MODE" >/dev/null 2>&1 || true
            ;;
    esac
}
trap cleanup EXIT

echo "Preparing device (dark mode + System UI Demo Mode)..."
if [[ "$HIDE_ERROR_DIALOGS_READ_OK" == "1" ]]; then
    "${ADB_CMD[@]}" shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
fi
if [[ "$IS_EMULATOR" == "1" ]]; then
    "${ADB_CMD[@]}" shell am force-stop com.android.launcher3 >/dev/null 2>&1 || true
    "${ADB_CMD[@]}" shell am force-stop com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
    "${ADB_CMD[@]}" shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
fi
"${ADB_CMD[@]}" shell cmd uimode night yes >/dev/null 2>&1 || true
"${ADB_CMD[@]}" shell settings put global sysui_demo_allowed 1 >/dev/null 2>&1 || true
"${ADB_CMD[@]}" shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null 2>&1 || true
"${ADB_CMD[@]}" shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null 2>&1 || true
"${ADB_CMD[@]}" shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null 2>&1 || true
"${ADB_CMD[@]}" shell am broadcast -a com.android.systemui.demo -e command network -e airplane hide -e wifi show -e level 4 -e fully true -e mobile hide >/dev/null 2>&1 || true
"${ADB_CMD[@]}" shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null 2>&1 || true

"${ADB_CMD[@]}" shell rm -rf "$DEVICE_SCREENSHOT_DIR" >/dev/null 2>&1 || true

echo "Installing debug and instrumentation APKs..."
./gradlew --no-daemon :app:installDebug :app:installDebugAndroidTest

echo "Running screenshot instrumentation tests..."
INSTRUMENT_OUTPUT=$("${ADB_CMD[@]}" shell am instrument -w \
    -e class "$TEST_CLASS" \
    "${PACKAGE_ID}.test/androidx.test.runner.AndroidJUnitRunner" 2>&1)
echo "$INSTRUMENT_OUTPUT"

if ! grep -q "OK (9 tests)" <<< "$INSTRUMENT_OUTPUT"; then
    echo "Error: Screenshot instrumentation tests did not pass." >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
echo "Pulling screenshots into $OUTPUT_DIR..."
for i in 1 2 3 4 5 6 7 8 9; do
    "${ADB_CMD[@]}" pull "${DEVICE_SCREENSHOT_DIR}/${i}.png" "${OUTPUT_DIR}/${i}.png"
    if [[ ! -s "${OUTPUT_DIR}/${i}.png" ]]; then
        echo "Error: Missing or empty screenshot ${OUTPUT_DIR}/${i}.png" >&2
        exit 1
    fi
done

"${ADB_CMD[@]}" shell rm -rf "$DEVICE_SCREENSHOT_DIR" >/dev/null 2>&1 || true

echo "Done! Updated 9 screenshots in ${OUTPUT_DIR}."
