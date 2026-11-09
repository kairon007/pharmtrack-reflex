#!/bin/bash
# iOS App Store Screenshot Capture Script
# Run on macOS with Xcode simulators installed

set -e

OUTPUT_DIR="raw/ios_appstore"
DEVICE="iPhone 16 Pro"  # 1290 x 2796 pixels (6.7" display)

echo "=== iOS App Store Screenshot Capture ==="
echo "Target device: $DEVICE"
echo ""

# Check for simulator
if ! xcrun simctl list devices available | grep -q "$DEVICE"; then
    echo "Error: Simulator '$DEVICE' not found."
    echo "Available devices:"
    xcrun simctl list devices available | grep -E "iPhone|iPad"
    exit 1
fi

# Boot simulator if not booted
if ! xcrun simctl list devices | grep -q "$DEVICE.*Booted"; then
    echo "Booting simulator..."
    xcrun simctl boot "$DEVICE"
    open -a Simulator
    sleep 3
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Map screen names to screenshot numbers (App Store order)
declare -A SCREENS=(
    ["Dashboard"]="01"
    ["Medications"]="02"
    ["Consultations"]="03"
    ["Appointments"]="04"
    ["Vitals"]="05"
    ["Settings"]="06"
)

echo "Capturing screenshots..."
echo ""

# Wait for app to be installed (build first if needed)
echo "Note: Ensure the app is built and installed on the simulator"
echo "To build: just ios-build"
echo ""

# Navigate to each screen and capture
# Edit these lines to match your app's navigation
SCREEN_NAMES=("Dashboard" "Medications" "Consultations" "Appointments" "Vitals" "Settings")

for i in "${!SCREEN_NAMES[@]}"; do
    SCREEN_NAME="${SCREEN_NAMES[$i]}"
    NUM=$((i + 1))
    FILENAME="$OUTPUT_DIR/${NUM}_${SCREEN_NAME}.png"

    echo "[$NUM/6] Capturing $SCREEN_NAME..."
    # Capture screenshot
    xcrun simctl screenshot "$DEVICE" --type png --display internal "$FILENAME"

    echo "  Saved: $FILENAME"
done

echo ""
echo "=== Capture Complete ==="
echo "Screenshots saved to: $OUTPUT_DIR"
echo ""
echo "Required sizes for App Store:"
echo "  - iPhone 6.7\" (Max): 1290 x 2796"
echo "  - iPhone 5.5\" (Plus): 1242 x 2208"
echo ""
echo "To resize if needed:"
echo "  sips -z 2796 1290 \$INPUT --out \$OUTPUT"
