# PharmTrack

Medication, appointment, consultation and vital-sign tracking for iOS and Android.

Since 1.0 the app is a **greenfield dual-platform rewrite**: no Skip, no transpilation.
Each platform has its own native implementation of the same frozen behavior contract.

```
specs/     the frozen behavior contract — the only artifact both platforms share
ios/       Swift Core (Foundation only) + SwiftUI app + the iOS runner
android/   pure Kotlin core (JVM) + Jetpack Compose app + the Android runner
tools/     the parity gate, the migration check and the static validators
web/       marketing site (unchanged)
```

## Architecture

| Layer | iOS | Android |
|---|---|---|
| Domain + reducers | `ios/Sources/Core` (Swift, Foundation only) | `android/core` (Kotlin, kotlinx.serialization) |
| UI | `ios/Sources/App` (SwiftUI) | `android/app` (Compose) |
| Runner | `ios/Tools/reflex-runner` | `android/tools/reflex-runner` |

There is no shared code between the platforms. The shared source of truth is
`specs/behavior/*.yaml` — 13 journeys, 180 cases — plus the contracts in
`specs/contracts/`. Both runners execute the same journeys and emit a canonical
NDJSON state stream; CI diffs the two streams and fails on any divergence.

## Building

```bash
just setup          # resolve the Swift package, warm the Gradle wrapper
just build          # both apps
just ios-build      # iOS app for the simulator (xcodegen + xcodebuild)
just android-build  # Android debug APK
```

Requirements: Xcode with `xcodegen` installed, Android SDK 36, JDK 17.

## Running

```bash
just ios-run       # build, install and launch on the booted simulator
just android-run   # install and launch on the running emulator
```

## Testing and the parity gate

```bash
just validate      # static validation of the specs, fixtures and journeys
just test          # the behavior suite on both platforms
just parity        # both runners over the suite + the stream diff
```

`just parity` is what CI runs (`.github/workflows/reflex-parity.yml`). Make
**Reflex parity / Parity gate** a required status check so a PR cannot merge
while the two platforms disagree.

## Upgrading an existing install

The app reads and writes the same `medical_data.json` at the same path as
v0.0.19, so existing data carries over. To verify a real install:

```bash
just golden-ios       # capture medical_data.json from a live v0.0.19 install
just golden-android
just migration         # round-trip both captures through both real codecs
```

Captures come from a virtual device only — the capture script refuses a
physical iPhone or Pixel.

## Releases

```bash
just release-bump major
just release           # build, upload to TestFlight and Google Play
```

Versions live in `ios/project.yml` (`MARKETING_VERSION`,
`CURRENT_PROJECT_VERSION`) and `android/app/build.gradle.kts` (`versionName`,
`versionCode`).

## License

MIT — see [LICENSE](LICENSE).
