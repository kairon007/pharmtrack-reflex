#!/bin/bash
# Android Play Store Screenshot Capture Script
# Run with Android emulator or device connected

set -e

OUTPUT_DIR="raw/android_playstore"
DEVICE_PATTERN="emulator"  # Change to device serial if using physical device

echo "=== Android Play Store Screenshot Capture ==="
echo ""

# Check for connected device
if ! adb devices | grep -q "$DEVICE_PATTERN"; then
    echo "Error: No Android device/emulator found."
    echo "Available devices:"
    adb devices
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "Capturing screenshots..."
echo ""

# Screen names in order for Play Store
SCREEN_NAMES=("Dashboard" "Medications" "Consultations" "Appointments" "Vitals" "Settings")

for i in "${!SCREEN_NAMES[@]}"; do
    SCREEN_NAME="${SCREEN_NAMES[$i]}"
    NUM=$((i + 1))
    FILENAME="$OUTPUT_DIR/${NUM}_${SCREEN_NAME}.png"
    TEMP_FILE="/sdcard/screenshot_${NUM}.png"

    echo "[$NUM/6] Capturing $SCREEN_NAME..."

    # Capture screenshot to device
    adb shell screencap -p "$TEMP_FILE"

    # Pull to local
    adb pull "$TEMP_FILE" "$FILENAME"

    # Clean up temp file
    adb shell rm "$TEMP_FILE"

    echo "  Saved: $FILENAME"
done

echo ""
echo "=== Capture Complete ==="
echo "Screenshots saved to: $OUTPUT_DIR"
echo ""
echo "Required sizes for Play Store:"
echo "  - Phone portrait: 1080 x 1920 (minimum)"
echo "  - Phone portrait: 1080 x 2220 (18:9)"
echo "  - Phone portrait: 1080 x 2400 (20:9)"
echo ""
echo "Current screenshot sizes:"
sips -g pixelWidth -g pixelHeight "$OUTPUT_DIR"/*.png | grep -E "pixel|filename" | paste - - | head -3
