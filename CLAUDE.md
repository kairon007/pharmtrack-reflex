# CLAUDE.md

This file provides guidance to AI coding agents working in this repository.

## Project Overview

PharmTrack is a dual-platform (iOS + Android) medication and health-tracking
app. Since 1.0 it is a **greenfield rewrite with no shared runtime**: no Skip,
no transpilation, no shared code between the platforms. The only shared
artifact is the frozen behavior contract in `specs/`.

| Tree | What it is |
|---|---|
| `specs/` | The frozen contract: behavior journeys, contracts, fixtures. Shared by both platforms. |
| `ios/` | Swift Core (Foundation only), the SwiftUI app, the iOS runner. |
| `android/` | Pure Kotlin core (JVM), the Compose app, the Android runner. |
| `tools/` | The parity gate, the migration check, the static validators. |
| `web/` | Marketing site. Untouched by the rewrite. |

## Build Commands

| Command | Description |
|---|---|
| `just setup` | Resolve the Swift package and warm the Gradle wrapper |
| `just build` | Build both apps |
| `just ios-build` | Build the iOS app for the simulator |
| `just ios-run` | Install and launch on the booted simulator |
| `just android-build` | Build the Android debug APK |
| `just android-run` | Install and launch on the running emulator |
| `just test` | Run the behavior suite on both platforms |
| `just parity` | Run both runners and diff their state streams (the CI gate) |
| `just validate` | Static validation of the specs, fixtures and journeys |
| `just migration` | Round-trip real v0.0.19 captures through both codecs |

**Direct commands:**

- `cd ios && swift build && swift test` — the Swift core and the runner
- `cd android && ./gradlew :core-tests:test` — the Kotlin core and the runner
- `cd ios && xcodegen generate` — regenerate the Xcode project from `ios/project.yml`
- `cd android && ./gradlew :app:assembleDebug` — the Android app

## Architecture

### The contract first

`specs/behavior/*.yaml` defines 13 journeys (180 cases). Each case is a `given`
block plus ordered steps; every `send`/`deliver` target, `state.` path and
fixture reference is declared in `specs/contracts/nodes.yaml` and
`specs/fixtures/index.yaml`. The contract is **frozen** — change it only with a
signed-off decision id from `specs/behavior/decisions.yaml`.

The grammar is documented in `specs/behavior/SCHEMA.md`. The runner contract
(CLI, exit codes, canonicalization, comparison) is `specs/contracts/runner.yaml`.

### Two independent cores

- `ios/Sources/Core` — Foundation only. No SwiftUI, no UIKit, no Combine, no
  `Date()`, no `UUID()`. Time and identity arrive through the `Clock` and
  `IDGenerator` ports.
- `android/core` — kotlinx.serialization and coroutines only. **Zero
  `android.*` imports**, no wall-clock read, no random UUID construction.

Both implement the same 14 state machines (one per screen). Business logic
lives in the cores; the UI layers only render state and dispatch actions.

### Rules that keep the two platforms in step

- The two cores share **no source, generated code or model**. If a rule needs
  to exist on both sides, write it twice.
- `runner.yaml#canonicalization` is applied by both runners before
  serialization — sorted keys, ISO8601 UTC seconds, uppercase UUIDs, omitted
  null optionals. It is what makes the byte diff meaningful.
- A machine's `emit` dictionary is built by assignment, not as one large
  literal. A big heterogeneous dictionary literal can exceed the Swift
  type-checker's budget on a slower toolchain — the CI runner hit this before
  any local build did.
- The formatting conventions that Swift and Kotlin disagree on are listed at
  the end of the phase notes in `REFLEX_MIGRATION_PLAN.md` (half-even vs
  half-up rounding, `String(Double)` vs `Double.toString`, UUID case,
  `String.format` vs Swift's `%.1f`). Read them before touching number or date
  code.
- `specs/contracts/persistence.yaml` is the upgrade contract: same file path,
  same schema, same date format, lossless **semantic** round-trip. Byte
  stability is explicitly not the contract.

### The parity gate

`tools/parity-gate.sh` builds both runners, runs the same glob through each,
and diffs the canonical streams with `tools/diff-parity.js`. CI runs it in
`.github/workflows/reflex-parity.yml`; the **Parity gate** job must be a
required status check.

When the gate reports a divergence it prints the journey, case, step index and
the first differing path — not the whole state object. Start there.

### Persistence

`medical_data.json`, at the app's documents/files directory, on both platforms.
`Log.medicationId` is the only additive field in 1.0 — optional, and absent
files keep the legacy substring match for "is taken".

### Notifications

`NotificationPlanner.plan(data:settings:now:)` is pure and lives in each core.
The planner emits the complete desired set; both adapters cancel everything
first, then apply it. Clear-all-then-reschedule is canonical — Android's
incremental behavior from v0.0.19 is not ported.

## Development Notes

- The Xcode project is **generated**: edit `ios/project.yml`, not the `.xcodeproj`.
  The app target is deliberately outside `ios/Package.swift` so `swift build`
  and `swift test` keep working on macOS.
- The Android app module needs the Android SDK; the headless runner modules do
  not. `REFLEX_SKIP_ANDROID_APP=1` leaves `:app` out of the build (CI uses it).
- Icons are semantic names from `specs/contracts/icons.yaml` — 28 of them, no
  fallback icon. A new icon means a contract entry plus both platform assets.
- Never edit `web/` for app work; it is the marketing site.
