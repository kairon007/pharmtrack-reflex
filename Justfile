set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

# PharmTrack 1.0 — the Reflex rewrite.
#
#   specs/   the frozen behavior contract, shared by both platforms
#   ios/     Swift Core + SwiftUI app + the iOS runner
#   android/ pure Kotlin core + Compose app + the Android runner
#   tools/   the parity gate, the migration check and the static validators

# List available commands
default:
	@just --list

# --- Setup ---

# Resolve the iOS package dependencies and warm the Gradle wrapper
setup:
	cd ios && swift package resolve
	cd android && ./gradlew --version

# Build both platforms
build: build-android build-ios

# --- Android Commands ---

# Alias for android-build
build-android: android-build

# Build Android Debug APK
android-build:
	cd android && ./gradlew :app:assembleDebug

# Install and Launch Android App on connected device/emulator
android-run:
	cd android && ./gradlew :app:installDebug
	adb shell am start -n com.tunnxo.medtrack/com.pharmtrack.reflex.app.MainActivity

# Install and Launch Android App on a specific device (e.g. if multiple connected)
# Usage: just android-device-run <SERIAL>
android-device-run device_id:
	cd android && ./gradlew :app:installDebug
	adb -s {{device_id}} shell am start -n com.tunnxo.medtrack/com.pharmtrack.reflex.app.MainActivity

# --- iOS Commands ---

# Alias for ios-build
build-ios: ios-build

# Generate the Xcode project from ios/project.yml (xcodegen)
ios-generate:
	cd ios && xcodegen generate

# Build iOS App for Simulator
ios-build: ios-generate
	xcodebuild -project ios/PharmTrack.xcodeproj -scheme PharmTrack \
		-derivedDataPath build/ios/DerivedData \
		-destination 'generic/platform=iOS Simulator' build

# Run iOS App on the booted simulator
ios-run: ios-build
	xcrun simctl install booted build/ios/DerivedData/Build/Products/Debug-iphonesimulator/PharmTrack.app
	xcrun simctl launch booted com.tunnxo.medtrack

# Install and Launch iOS App on a connected device
# Usage: just ios-device-run <UDID>    (list devices with: xcrun devicectl list devices)
ios-device-run device_id:
	cd ios && xcodegen generate
	@if [ -f .env ]; then set -a; . ./.env; set +a; fi; \
		xcodebuild -project ios/PharmTrack.xcodeproj -scheme PharmTrack \
			-derivedDataPath build/ios/DerivedData \
			-destination 'generic/platform=iOS' \
			-allowProvisioningUpdates \
			${APPLE_TEAM_ID:+DEVELOPMENT_TEAM="$APPLE_TEAM_ID"} build
	xcrun devicectl device install app --device {{device_id}} build/ios/DerivedData/Build/Products/Debug-iphoneos/PharmTrack.app
	xcrun devicectl device process launch --device {{device_id}} com.tunnxo.medtrack

# --- Tests and the parity gate ---

# Run the behavior suite on both platforms
test:
	cd ios && swift test
	cd android && ./gradlew :core-tests:test

# Run both runners over the frozen suite and diff their state streams
parity:
	tools/parity-gate.sh

# Static validation of the specs, the fixtures and the behavior suite
validate:
	node tools/validate-fixtures.js specs
	node tools/validate-behavior.js specs

# --- Migration (hand task on a virtual device) ---

# Capture the real medical_data.json from a live v0.0.19 install
golden-ios:
	tools/capture-golden-fixture.sh ios

golden-android:
	tools/capture-golden-fixture.sh android

# Verify a real capture survives the upgrade on both platforms
migration:
	tools/verify-migration.sh

# --- CI/CD & Release ---

# Setup Python environment for release scripts
setup-release:
	python3 -m venv scripts/release/venv
	scripts/release/venv/bin/pip install -r scripts/release/requirements.txt

# Generate all configuration files from .env
generate-configs:
	./scripts/generate-keystore-properties.sh
	./scripts/generate-ios-config.sh
	./scripts/generate-export-options.sh

# Show current version information
release-info:
	@scripts/release/venv/bin/python3 scripts/release/version_manager.py info

# Prepare a new release (fetch latest from stores and increment)
release-prepare:
	scripts/release/venv/bin/python3 scripts/release/version_manager.py prepare

# Bump semantic version (patch, minor, major)
release-bump type="patch":
	scripts/release/venv/bin/python3 scripts/release/version_manager.py bump {{type}}

# Build Android release App Bundle (AAB)
android-release: generate-configs
	cd android && ./gradlew :app:bundleRelease

# Build and archive iOS app for App Store
ios-release: generate-configs ios-generate
	xcodebuild -project ios/PharmTrack.xcodeproj -scheme PharmTrack \
		-derivedDataPath build/ios/DerivedData \
		-sdk iphoneos -configuration Release \
		-archivePath build/PharmTrack.xcarchive archive
	xcodebuild -exportArchive -archivePath build/PharmTrack.xcarchive \
		-exportOptionsPlist ios/ExportOptions.plist -exportPath build/ios/ipa -allowProvisioningUpdates

# Upload Android release to Google Play (internal track)
upload-android:
	scripts/release/venv/bin/python3 scripts/release/uploader.py android --android-track internal

# Upload iOS release to TestFlight
upload-ios:
	scripts/release/venv/bin/python3 scripts/release/uploader.py ios

# Upload both platforms
upload-all:
	scripts/release/venv/bin/python3 scripts/release/uploader.py all

# Full release: prepare, build, and upload to both stores
release: release-prepare android-release ios-release upload-all

# --- Screenshots ---

# Process raw screenshots for store upload
screenshots-process:
	python3 scripts/screenshots/process_screenshots.py

# Capture iOS screenshots from simulator
screenshots-capture-ios:
	./scripts/screenshots/capture_ios.sh

# Capture Android screenshots from emulator/device
screenshots-capture-android:
	./scripts/screenshots/capture_android.sh

# Capture screenshots from both platforms
screenshots-capture: screenshots-capture-ios screenshots-capture-android

# Generate iOS screenshots with Koubou (requires: pip install koubou==0.16.0)
screenshots-generate-ios:
	kou generate screenshots.yaml

# Generate screenshots with Koubou (all platforms)
screenshots-generate: screenshots-generate-ios
