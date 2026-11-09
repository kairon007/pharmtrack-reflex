#!/bin/bash
set -e

# Generate export_options.plist for iOS release build
# This file is used by 'xcodebuild -exportArchive' to specify signing options

EXPORT_OPTIONS_FILE="ios/ExportOptions.plist"

echo "🔧 Generating iOS export_options.plist..."

# Ensure directory exists
mkdir -p ios

# Load .env variables if .env exists
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

# Determine Team ID
TEAM_ID="${APPLE_TEAM_ID}"

if [ -z "$TEAM_ID" ]; then
  echo "⚠️  APPLE_TEAM_ID not found in .env. export_options.plist might be incomplete."
fi

cat <<EOF > "$EXPORT_OPTIONS_FILE"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store-connect</string>
    <key>signingStyle</key>
    <string>automatic</string>
    <key>teamID</key>
    <string>${TEAM_ID}</string>
    <key>destination</key>
    <string>export</string>
    <key>manageAppVersionAndBuildNumber</key>
    <true/>
</dict>
</plist>
EOF

echo "✅ Generated $EXPORT_OPTIONS_FILE"

# Add permission validation
chmod +x scripts/generate-export-options.sh
