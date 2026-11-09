# PharmTrack → Shopify Reflex Migration Plan

Status: **Phase 5 complete (static) — CI parity gate + migration verification tooling + 1.0 version bump + cutover to the repo root; the Skip tree is gone** · Baseline: `reflex` branch @ `3887aa8b` · Live app: v0.0.19 (build 27), `com.tunnxo.medtrack` · Shipping: 1.0.0 (build 28)

## Decisions

| Decision | Choice |
|---|---|
| Cutover | **Greenfield parallel rewrite.** The new `specs/` + `ios/` + `android/` tree now lives at the repo root and the Skip tree is deleted. Ship as PharmTrack 1.0 once both natives pass the behavior suite. |
| Order | **Behavior specs first.** Freeze `specs/` against the current app's observable behavior, then implement Swift and Kotlin against that contract. |
| Scope | Mobile apps + notification scheduling. `web/` and `backend/` untouched. |

## Why this is a rewrite, not a refactor

Skip is a shared-code runtime — Swift transpiled to Kotlin via `skipstone`. That is precisely what Reflex forbids. There is no path that keeps `Package.swift`'s skipstone plugins and satisfies "no shared code between platforms."

What we are actually replacing:

- **1 god-ViewModel** ([MedicalViewModel.swift](Sources/AppModel/MedicalViewModel.swift), 274 L) owning one `MedicalData` blob → **14 MVVM-U machines**.
- **~45 `@State` properties scattered across 13 views** → immutable `State` per screen.
- **Business logic inside SwiftUI computed properties** (trend %, filters, `takenTodayCount`, calendar math) — duplicated between `VitalsView` and `AllVitalsView`, and between `DashboardView` and `MedicationTrackerView` → pure reducers + selectors, written twice, parity-tested.
- **Timers driving logic** (`Task.sleep(3.5s)`, `asyncAfter(0.9s)`, `asyncAfter(0.5/2.5s)`) → declarative effects that run instantly under test.
- **`#if SKIP` duplicated notification scheduling** (~250 L of near-identical iOS/Android branches) → one pure `NotificationPlanner` reducer per platform + thin `UNUserNotificationCenter` / `AlarmManager` adapters.

Baseline size: ~5,074 LOC Swift. Post-migration expect ~4–5k LOC Swift Core + UI and a comparable Kotlin volume. The duplication is the point; the bridge glue is what we delete.

## Target layout

The tree was built under `reflex/` so the frozen Skip app stayed buildable while the rewrite was in flight (its Gradle plugin resolves `settings.rootDir.parent` and expects `Package.swift` at the repo root). **Phase 5 promoted it to the repo root and deleted the Skip tree.**

```text
specs/                               # SHARED SOURCE OF TRUTH — the only cross-platform artifact
├── contracts/                       # Service interfaces, JSON schema, icon names, action signatures
├── behavior/                        # Declarative journey YAML
└── fixtures/                        # medical_data.json variants, clock + UUID sequences
ios/
├── Package.swift                    # no skipstone, no Skip deps
├── project.yml                      # xcodegen project — the .xcodeproj is generated, never edited
├── Assets.xcassets/                 # AppIcon (carried over from the shipping app)
├── Sources/
│   ├── Core/                        # pure Swift: Foundation only, no SwiftUI/UIKit
│   │   ├── App/                     # AppState, RootSession, ServiceRegistrations
│   │   ├── Navigation/              # TabNavigator, PushNavigator, ModalNavigator
│   │   ├── Domain/                  # models + pure selectors
│   │   ├── Screens/<Feature>/       # State, Action, Message, update()
│   │   ├── Services/                # protocols + live adapters
│   │   └── Introspection/           # nodes, action schemas
│   └── App/                         # thin SwiftUI
├── Tests/BehaviorTests/             # runs specs/ against Core, headless
└── Tools/reflex-runner/             # CLI host
android/
├── settings.gradle.kts              # no skip-plugin, no Android plugin
├── core/                            # pure Kotlin JVM — no Android SDK imports
├── runnerkit/                       # YAML loading, journey parsing, executor, NDJSON emitter
├── core-tests/                      # JVM tests executing specs/
├── tools/reflex-runner/             # CLI host
└── app/                             # thin Jetpack Compose
tools/                               # parity gate, migration check, static validators
```

Cutover (done in Phase 5): `specs`, `ios`, `android` and `tools` promoted to the repo root; `Sources/`, `Tests/`, `Package.swift`, `Skip.env`, `Darwin/`, `Android/`, `Project.xcworkspace` and the Skip helper scripts deleted. `web/` and `backend/` are untouched.

## The 14 machines

| # | Node name | Replaces | Kind | Notes |
|---|---|---|---|---|
| 1 | `app_root` | `MainTabView` shell, `ContentView` | root | Owns load lifecycle, onboarding gate, tab selection, `selectedAppointmentDate` |
| 2 | `onboarding` | `OnboardingOverlay` + 3.5s sleeps | overlay | Step machine over 5 tabs; `clearData` then commit flag |
| 3 | `dashboard` | `DashboardView` | tab 0 | Greeting, today's meds, next appt hero, recent logs |
| 4 | `consultations` | `ConsultationsView` | tab 1 | Search filter, initials avatar |
| 5 | `medication_tracker` | `MedicationTrackerView` | tab 2 | Today/Inventory/History segments, stock ±, mark taken |
| 6 | `appointments` | `AppointmentsView` | tab 3 | Week strip, day filter, delete |
| 7 | `vitals` | `VitalsView` | tab 4 | Metric cards, trend, recent readings |
| 8 | `vitals_detail` | `AllVitalsView` (668 L) | push | Heaviest: metric × range × week offset × selected day × highlight |
| 9 | `add_vital` | `AddVitalView` | modal | Type switch, BP dual value, validation |
| 10 | `add_medication` | `AddMedicationView` | modal | Add/edit, stock parsing, schedule enum |
| 11 | `add_appointment` | `AddAppointmentView` | modal | Add/edit, 4-field validation |
| 12 | `consultation_form` | `NewConsultationView` | modal | Add/edit, linked medications |
| 13 | `medication_picker` | `MedicationPickerView` | modal child | Replaces `$linkedMedications` shared binding |
| 14 | `settings` | `SettingsView` | modal | Draft-vs-committed notification settings |

`Components.swift` (211 L, zero state, all callbacks `() -> Void`) maps directly to Action dispatch and needs no machine — port it as dumb views on both sides.

Migrate leaf-first: 9–13 → 14 → 4, 6, 7 → 5 → 8 → 3 → 2 → 1.

## Hard problems (resolve in specs, not in code)

These are the things that will silently break parity or lose user data if they are not pinned down before implementation.

### 1. On-device data compatibility — highest risk
The app is live. `LocalMedicalRepository` writes `medical_data.json` to iOS Documents / Android `filesDir`, ISO8601 dates. **The new natives must read and write the same schema at the same path.** A user upgrading to 1.0 must not lose their medications.

Required: `specs/contracts/medical_data.schema.json` plus a golden-file round-trip journey that loads a fixture captured from the shipping app and asserts no field loss. `MedicalData.init(from:)` already tolerates missing keys — preserve that leniency.

> **Correction (Phase 0).** An earlier draft of this plan said "byte-compatible" and "byte-stable". That was too strong and is withdrawn. `JSONEncoder` does not guarantee key order without `.sortedKeys`, and kotlinx.serialization orders by declaration, so the two will never emit identical bytes. The real contract is **same path, same schema, same date format, lossless semantic round-trip**. `data_compat` compares parsed values field-by-field, never raw bytes. See `specs/contracts/persistence.yaml`.

### 2. `isTaken` is string matching
`MedicationTrackerView` decides a dose was taken via `log.description.contains(med.name)` plus same-day check. Two meds with overlapping names collide; renaming a med orphans its history. Fix: add an optional `medicationId` to `Log`, write it going forward, fall back to the string match when absent. Schema stays backward-compatible; the fallback is a spec'd behavior, not a compatibility shim.

### 3. Impurity must be injected, not eliminated
Every `Date()` inside a computed property (greeting, upcoming filter, range cutoffs, calendar strips) and every `UUID()` in a model initializer becomes a service:
- `Clock` — `now` enters State via a message; fixtures pin it.
- `IDGenerator` — deterministic sequence in tests.
- `KeyValueStore` — replaces `@AppStorage("hasCompletedOnboarding")`.
- `Scheduler` — replaces `Task.sleep` / `asyncAfter`; test adapter fires immediately.

Without this the specs are non-deterministic and the whole parity premise collapses.

### 4. Onboarding timing is currently behavior-by-sleep
`MainTabView` waits 3.5s before showing the overlay, and 0.1s after `clearData()` "to ensure data is cleared." The 0.1s is a race workaround — the real fix is awaiting the clear and transitioning on its completion message. The 3.5s becomes a `Scheduler` effect the test runner collapses to zero.

### 5. Cross-screen navigation state
`DashboardView` writes `selectedTab = 3` and `selectedAppointmentDate`, but `AppointmentsView` receives the date **by value at init**, not as a live binding — so the jump lands on the wrong day. `RootState` owning both fixes this as a side effect. Pin the corrected behavior in a journey; don't replicate the bug.

### 6. Notification planner extraction
All trigger-date math (`-5min` / `-1h` / `-1day` offsets, `> now` guards, low-stock threshold, per-category enable flags) is pure and currently duplicated under `#if SKIP`. Extract to `NotificationPlanner.plan(data, settings, now) -> [ScheduledNotification]`. The adapters only serialize that list to `UNNotificationRequest` / `AlarmManager` intents. This is the single biggest testability win in the migration.

The iOS branch calls `removeAllPendingNotificationRequests()` on every update while the Android branch does not. **Resolved: clear-all-then-reschedule is canonical.** The planner emits the complete desired set; both adapters cancel everything first, then apply it. Android's incremental behavior is not ported.

### 7. Charts and derived data
`DynamicChartPath` is pure geometry and stays in the UI layer. The arrays feeding it (`chartValue`, `chartMax`, `chartMin`, filtered/sorted series) move into State. `ChartPath` is dead hardcoded demo code — delete, don't port.

### 8. Icons and safe areas
Skip's `IconMapper` (SF Symbol → Android drawable) disappears. Define `specs/contracts/icons.yaml` mapping semantic names (`home`, `meds`, `calendar`, `vitals`, `consults`) to SF Symbols on iOS and Material/drawable on Android. Same for the `#if SKIP` padding hacks (16 vs 60, 120 vs 80) — each platform does real safe-area / edge-to-edge insets instead.

### 9. Stringly-typed state
`VitalsView.selectedMetric: String`, `MedicationTrackerView.selectedTab: String`, `Medication.schedule: String` → enums. `schedule` is persisted, so its raw values must stay `"Morning"` / `"Afternoon"` / `"Evening"` in JSON.

## Phases

Each phase ends at a boundary. Per your standing rule I will not run builds, tests, or simulators — I hand you a verify checklist and stop.

### Phase 0 — Contracts & fixtures — **COMPLETE**
Delivered under `specs/`:
- `contracts/medical_data.schema.json` — derived from the `Codable` structs; optional fields accept null on read, required fields do not
- `contracts/persistence.yaml` — paths, encoding rules, leniency, load-error branches, upgrade/downgrade safety
- `contracts/services.yaml` — Clock, IDGenerator, Scheduler, KeyValueStore, MedicalStore, NotificationScheduler + the pure NotificationPlanner
- `contracts/icons.yaml` — 28 semantic names, both platforms, no fallback icon
- `fixtures/` — 10 fixtures + `determinism.yaml` (pinned clock/IDs) + `index.yaml` (branch coverage)
- `tools/` — `validate-fixtures.js` (static, no deps) and `capture-golden-fixture.sh` (read-only, virtual devices only)

Static verification: all fixtures validate or fail exactly as `index.yaml` specifies; all YAML parses; icon contract has no unflagged missing drawable and covers every icon the app uses; capture script refuses a physical-device serial.

**Outstanding:** golden captures do not exist yet. `data_compat` cannot pass until they are pulled from a live v0.0.19 install.

### Phase 1 — Behavior suite (the frozen contract) — **COMPLETE**
Author `specs/behavior/*.yaml`. Minimum journeys:

- `app_load` — first launch seeds demo; returning launch loads file; parse error after onboarding does **not** wipe to mocks
- `onboarding_journey` — 5 tab steps → Get Started → clear → flag set
- `vital_crud` — add weight / BP / sugar, validation rejects empty, delete, edit; row icon derives from vital type
- `vitals_detail_navigation` — metric × range × week offset × day selection × highlight clears (eventual state only, timing not asserted)
- `medication_lifecycle` — add, mark taken (stock −1, next dose +24h, log written), stock ± floors at 0, low-stock flag
- `medication_search_and_segments` — Today / Inventory / History filters
- `appointment_crud` — add sorts by date, day filter, edit re-sorts, delete
- `consultation_crud` — add/edit, link + unlink medications via picker, search filter
- `dashboard_derivation` — greeting by hour, taken-today count, next-med selection, upcoming prefix(3), hero-card branch
- `dashboard_cross_tab_jump` — tap appointment → tab 3 **with the correct date** (the bug fix)
- `settings_draft_commit` — toggles mutate draft only; Cancel discards; Save commits
- `notification_planner` — every enable flag × reminder offset × past-date guard × threshold; planner output is the complete desired set, applied after a full cancel
- `data_compat` — load shipping-app fixture, round-trip through the real codec, assert **semantic** equality field-by-field (byte-stability is withdrawn — see `persistence.yaml#compatibility_guarantee`; JSONEncoder and kotlinx.serialization can never emit identical bytes)

Define the runner contract: `reflex-runner --platform ios|android --spec <glob>` emits per-step state JSON; CI diffs the two.

Delivered under `specs/`:
- `behavior/*.yaml` — all 13 journeys above, 180 cases total, each traced to legacy `file:line` evidence
- `behavior/decisions.yaml` — the decision registry: D1–D5 (pre-Phase-0), D6–D13 (signed off at the Phase 1 boundary), D14–D16 (`REVIEW_AT_FREEZE` engineering resolutions), plus preserved quirks Q1–Q18
- `behavior/SCHEMA.md` — the frozen journey grammar `reflex/behavior/1`: `given`/`steps`, the `send`/`deliver`/`flush`/`advance`/`inject`/`note` verbs, path namespaces, operators, value aliases, setup + counter baseline
- `contracts/nodes.yaml` — all 14 machines (state / derived / actions / messages / effects / rules) and the shared pure selectors
- `contracts/runner.yaml` — CLI, exit codes, NDJSON record types, canonicalization, per-platform implementation constraints, fixture adapters
- Phase 0 artifacts reconciled with the decisions: `services.yaml` consultation offset now honors `appointmentReminderTime` (D9); `seeded_demo.json` log repaired to Vitamin D3 + `medicationId` (D7); `index.yaml` seed/badge quirks marked resolved (D7/D8); two new leniency fixtures `partial_settings.json` and `unknown_keys.json`

Static verification: every journey parses; every `send`/`deliver` target exists in `nodes.yaml`; every `state.` path resolves to a declared field; every fixture reference exists; every `divergence:` id is in its file's `decisions` list; case ids unique. See `tools/validate-behavior.js`.

- **Exit:** every journey reviewed against the current app; contract frozen. **Met** — pending the golden captures (a hand task) which `data_compat` documents as blocked, and the `REVIEW_AT_FREEZE` items D14–D16 which are flagged for a final read rather than unresolved.

### Phase 2 — iOS Core + runner — **COMPLETE**
Pure Swift package, no SwiftUI import anywhere in `Core`. Models, selectors, 14 machines, introspection nodes, service protocols, in-memory + fixture adapters, CLI host.

Delivered under `ios/`:
- `Sources/Core/` — 33 files, `import Foundation` and nothing else. No `UUID()` or `Date()` anywhere in Core; both arrive through `IDGenerator` / `Clock`.
  - `App/` — `AppState`, `MachineContext`, `Action`/`Message` wrappers with YAML decoders, `Effect`, `Reducer`, `RootSession` (synchronous `send`/`deliver`, queued service effects, 1000-effect cap)
  - `Screens/` — all 14 machines, each `State` + `Action` + `Message` + `reduce` + `emit`
  - `Domain/` — models, pure selectors, `NotificationPlanner.plan(data:now:)`, `DemoSeed`, `ValueFormat`, canonical entity rendering
  - `Services/` — the six ports only. Live adapters are Phase 4 and stay out of Core (`UserNotifications` is not Foundation)
  - `Introspection/` — `NodeRegistry`, action/message schemas, internal-action list
- `Tools/RunnerKit/` — 9 files: fixture adapters for all six ports, Yams-backed YAML loader, spec/fixture locator, journey parser, alias resolver, path resolver, operators, NDJSON emitter, executor
- `Tools/reflex-runner/Main.swift` — the CLI (`--platform --spec --case --out`, exit 0/1/2/3)
- `Tests/BehaviorTests/` — runs the whole suite through the *same* executor and canonicalization as the CLI, plus a two-run byte-identical determinism test

Verification: Core, RunnerKit, the CLI and the test target all typecheck clean. The suite was first **executed** offline in a `/tmp` scratch copy using a PyYAML-backed shim standing in for Yams: **180 cases, 651 assertions, 644 pass, 7 fail**, two consecutive runs producing byte-identical NDJSON.

All 7 failures were defects in the frozen Phase 1 journeys, not in Core — each contradicted `nodes.yaml` or its own fixture. The three minimal amendments below were signed off and applied on 2026-11-08 (`tools/phase2-spec-amendments.patch`); the patch is kept as the record of what changed and why.

1. `dashboard_cross_tab_jump#a_past_appointment_cannot_be_jumped_to_from_the_dashboard` asserted `state.appointments.visibleIds == []`, but `notification_matrix.appointments[0]` is at `2026-11-07T12:30:00Z` — today — and `selectedDate` initializes to `app_root.selectedAppointmentDate` (= `clock.now`), so that row is visible whether or not the jump happens. Now asserts `state.app_root.selectedAppointmentDate == $now`, which is the actual no-op.
2. `medication_search_and_segments#whitespace_query_matches_nothing` sent `text: " "` expecting no matches, but the contract rule is "substring on name **OR instructions**, not trimmed" and the seeded data is full of spaces (`Vitamin D3`, `With Food`, `After breakfast`, `Every 6 hours`). The case's own note ("no seeded name contains a space") was false. Renamed to `untrimmed_query_matches_nothing`, now sends `" Vitamin D3 "` — that proves the not-trimmed rule far better and still exercises the zero-total divide guard.
3. `vital_crud#add_blood_pressure_with_both_values`, `#blood_pressure_without_diastolic_falls_back_to_zero`, `#add_blood_sugar_formats_as_an_integer` asserted `state.vitals.formattedValue` for a non-weight metric without ever selecting it. `formattedValue` is over `filteredVitals` (`type == selectedMetric`, initial `weight`), and legacy `VitalsView.selectedMetric` is independent of `AddVitalView.type`. Each now sends `vitals.selectMetric` after the `flush: all`. The four cases that already selected the metric were unchanged.

**Green with the real toolchain** (Yams 5.4.0, pinned in `ios/Package.resolved`):
- `swift build` — clean, no warnings
- `swift test` — **5 tests, 0 failures**
- `reflex-runner --platform ios --spec '*.yaml'` — **exit 0**, 180/180 cases, 651/651 assertions, **1993 records** (180 `case_start` + 982 `step` + 651 `assertion` + 180 `case_end`)
- Two consecutive CLI runs byte-identical, and **byte-identical to the earlier PyYAML-shim stream** — so the YAML resolver was not masking anything

**Conventions Phase 3's Kotlin runner MUST mirror** (these are choices the contract leaves open; diverging on any of them breaks the byte diff):
- A `steps:` entry carrying only `expect` still emits a step record, labelled `verb: "expect"`, `target: ""`. This is a superset of `runner.yaml`'s verb enum and is required by "emitted after EVERY step".
- `target` spellings: `send`/`deliver` → `<node>.<name>`; `flush` → `all|scheduler|store`; `advance` → `clock`; `inject` → `<service>.<failure>`; `note`/`expect` → `""`.
- **All 14 nodes are emitted on every step**, not just the journey's declared `nodes` list.
- `rowIcon` emits the semantic spellings `weight | blood_pressure | blood_sugar` (the journey's expected values), not `icons.yaml`'s `vital.weight` keys.
- `inject: { service: store, fail_next: X }` names no operation, so it arms the **next store operation of any kind**.
- Path tokens balance brackets: `[id=$fixture:m.appointments[2].id]` nests.
- `$fixture:` aliases resolve through the **real codec** (decode → re-render) so an explicit null in a fixture equals an omitted key in `store.saved`; intentionally-broken fixtures fall back to raw JSON.
- `$1`/`$2` index the ids handed out **since setup's counter reset**, and aliases resolve in step `args` as well as in expected values.
- `%.1f` in Swift is half-**even**; Kotlin must use `BigDecimal.setScale(1, HALF_EVEN)`, not `String.format` (half-up). `shortestDecimal` normalizes exponent spelling.
- `Instant` formatting is arithmetic (Hinnant civil-date), never `DateFormatter`/`DateTimeFormatter`, so it is locale-independent.

- **Exit:** `swift test` + `reflex-runner --platform ios` green on all journeys. **Met** — 5 tests / 0 failures, runner exit 0, 180/180 cases. The golden captures remain a Phase 1 hand task that `data_compat` documents as blocked; they gate Phase 5's real-install migration check, not this phase.

### Phase 3 — Android core + runner — **COMPLETE**
Pure JVM Kotlin module — `kotlinx.serialization`, coroutines, zero `android.*` imports. Same 14 machines, same nodes, same action names.

Delivered under `android/` — 46 Kotlin files, ~7,100 lines:
- `settings.gradle.kts` — no skip-plugin and no Android plugin. Four JVM modules, Kotlin 2.4.21, JVM toolchain 17.
- `core/` — 34 files, 5,121 lines. `kotlinx.serialization` and `kotlinx.coroutines` only; **zero `android.*` imports**, and no wall-clock read and no UUID construction anywhere (both arrive through `Clock` / `IDGenerator`).
  - `Support/` — `Instant` (Hinnant civil-date arithmetic, no `java.time`), `ReflexValue`, `SwiftNumber` (Swift-compatible double/int parse and render), the UUID and `MedicalData` codecs
  - `Domain/` — models, pure selectors, `NotificationPlanner.plan(data, now)`, `DemoSeed`, `ValueFormat`, entity rendering
  - `App/` — `AppState`, `MachineContext`, `Action`/`Message` wrappers with YAML decoders, `Effect`, `Reducer`, `RootSession` (synchronous `send`/`deliver`, queued service effects, 1000-effect cap)
  - `Screens/` — all 14 machines, each `State` + `Action` + `Message` + `reduce` + `emit`
  - `Services/` — the six ports only. Live adapters stay out of Core until Phase 4
  - `Introspection/` — `NodeRegistry`, action/message schemas, internal-action list
- `runnerkit/` — 10 files, 1,742 lines: SnakeYAML journey loader, journey parser, alias resolver, path resolver, operators, NDJSON emitter, executor, fixture adapters for all six ports
- `tools/reflex-runner/` — the CLI (`--platform android --spec --case --out`, exit 0/1/2/3)
- `core-tests/` — runs the whole suite through the *same* executor and canonicalization as the CLI, plus a two-run byte-identical determinism test

Verification (run in a scratch copy of the tree, never in the repo):
- `:core-tests:test` — **5 tests, 0 failures**
- `reflex-runner --platform android --spec '*.yaml'` — **exit 0**, 180/180 cases, 651/651 assertions, **1993 records** (180 `case_start` + 982 `step` + 651 `assertion` + 180 `case_end`)
- **Byte-identical to the iOS stream.** `cmp` of the two NDJSON files is clean — same 1993 records, same bytes, same order. This is the Phase 3 exit criterion met, not approximated.
- Two consecutive Android runs are byte-identical.
- Exit codes confirmed on **both** platforms: 0 all-pass, 1 assertion failure, 2 usage / unknown node-action-fixture / malformed journey, 3 ID sequence exhausted (same stderr text).
- `core/` contains no `android.*` import, no `System.currentTimeMillis`, no `Instant.now`, no `UUID.randomUUID`.

**Conventions the Kotlin runner had to match** (these are the places where the two languages disagree; Phase 4's Compose layer inherits them):
- `%.1f` in Swift is half-**even**; Kotlin's `String.format` is half-up and would disagree on ties such as 76.25. The port uses `BigDecimal(value).setScale(n, RoundingMode.HALF_EVEN).toPlainString()`.
- Swift's `String(Double)` and Kotlin's `Double.toString` differ in BOTH the exponential threshold and the exponent spelling. `SwiftNumber.doubleString` re-lays-out the shortest round-trip digits into Swift's own rules: decimal notation for decimal exponents -4…15, otherwise `e±dd` with a forced sign and at least two exponent digits.
- Swift's `Double(String)` accepts hex floats and `inf`/`nan` and rejects surrounding whitespace; `toDoubleOrNull` differs on hex and on whitespace. `SwiftNumber.parseDouble` reproduces Swift's acceptance set exactly (Q7 / numeric_leniency).
- Swift's `UUID.uuidString` is UPPERCASE, `java.util.UUID.toString` is lowercase. Every identifier goes through `ReflexValue.uuidString`.
- `java.util.UUID.fromString` accepts `1-1-1-1-1`; Swift's `UUID(uuidString:)` does not. `parseUUID` validates the 8-4-4-4-12 shape first, so a malformed id is exit 2 on both platforms.
- **Kotlin 2.4 removed `floor`, `abs`, `round` and `ceil` from `kotlin.math`** — only `truncate`, `roundToInt` and `roundToLong` remain. The port uses `java.lang.Math` plus a local half-away-from-zero `round`, because Swift's `.rounded()` is half-away-from-zero while `Math.round` is half-up.
- `Instant` never uses `java.time`: the format is arithmetic (Hinnant civil-date), so it is locale-independent and byte-identical to the Swift side.
- kotlinx.serialization's leniency differs from Swift's synthesized `Codable`: a nullable property needs a `= null` default to tolerate a MISSING key, and `explicitNulls = false` is what drops nulls on write. `MedicalData`'s six top-level keys therefore use a hand-written serializer, because the generated one cannot tell "absent" from "null" for a defaulted property — exactly the `decode()` vs `decodeIfPresent()` asymmetry persistence.yaml requires.
- A non-nullable property with no default rejects both a missing key and an explicit null, matching Swift's `decode()`; `NotificationSettings` carries no defaults, so a partial settings object is rejected rather than silently filled in.
- SnakeYAML resolves YAML 1.1 booleans (`true`/`false` only in this suite) and would resolve an unquoted timestamp to a `Date`; every instant in the suite is quoted, and the loader converts a `Date` to an ISO instant string exactly as the Swift loader does.
- `object` is a Kotlin keyword: the Swift `object` locals became `map` / `members`.

- **Exit:** `./gradlew :core-tests:test` + `reflex-runner --platform android` green; **state JSON diffs clean against iOS**. **Met** — 5 tests / 0 failures, runner exit 0, 180/180 cases, 651/651 assertions, and the Android NDJSON is byte-identical to the iOS NDJSON. The golden captures remain a Phase 1 hand task that `data_compat` documents as blocked; they gate Phase 5's real-install migration check, not this phase.

### Phase 4 — Thin UI, both platforms — **COMPLETE (static) · device parity pass is the hand task**
SwiftUI views and Compose composables that only read State and `send(action:)`. No `if` logic beyond rendering branches. Port `Components.swift` as dumb views. Live service adapters: file store, notifications, UserDefaults / SharedPreferences.
- The Compose layer inherits every numeric and formatting convention listed at the end of Phase 3 — those are the places the two platforms silently disagree.

Delivered — iOS (`ios/Sources/App/`, 16 files):
- `LiveServices.swift` — LiveClock, LiveIDGenerator, LiveScheduler (DispatchWorkItem, keyed replacement), DefaultsKeyValueStore, FileMedicalStore (Documents/medical_data.json, atomic), LiveNotificationScheduler (clear-all then add, UNCalendarNotificationTrigger)
- `ReflexApp.swift` — the live host: one RootSession, `@Published AppState`, single-flight `flushStore()` drain, scheduler messages delivered back into the session
- `ScreenData.swift` — typed extraction of each machine's `emit` output (the views never re-implement a selector); `Theme.swift` — v0.0.19 palette, the 28-name `SemanticIcon` contract, DateFormatting; `Components.swift` — the dumb building blocks; `ChartShape.swift` — the legacy DynamicChartPath geometry
- 14 screens + `RootView.swift` (five tabs, every form sheet bound to its machine's presentation state) + `PharmTrackApp.swift` + `project.yml` (xcodegen; the App target stays out of `Package.swift` so the macOS core build/test keep working)

Delivered — Android (`android/app/`, 22 files):
- New `:app` module: AGP 9.5.1 with **built-in Kotlin** (the Kotlin plugin is pinned to 2.4.21 on the build classpath so `:core` metadata and `:app` agree), Compose compiler plugin 2.4.21, Compose BOM 2026.06.01, minSdk 24 / compile+target 36, applicationId `com.tunnxo.medtrack`
- `LiveServices.kt` — LiveClock, LiveIDGenerator, HandlerScheduler (main looper, keyed replacement), PrefsKeyValueStore (SharedPreferences), FileMedicalStore (`filesDir/medical_data.json`), AlarmNotificationScheduler (clear-all then arm `AlarmManager.setAndAllowWhileIdle` alarms; a nil triggerAt posts immediately through NotificationManager) + `NotificationAlarmReceiver`
- `ReflexApp.kt` — the live host: StateFlow<AppState>, CoroutineScope(Main) drain, pinned device UTC offset as `Instant.Zone`
- `ScreenData.kt` (the Kotlin twin of the Swift extraction layer), `Theme.kt` (palette + `SemanticIcon` → drawable), `Components.kt`, `ChartShape.kt`, `DateTimeField.kt`, `RootView.kt` (five tabs + five full-screen form sheets), 11 screen/sheet composables
- Icons: the 23 reused vectors from the legacy app plus the 5 new ones `icons.yaml` requires (`ic_water_drop`, `ic_trending_up`, `ic_trending_flat`, `ic_remove_circle`, `ic_auto_awesome`) — 28 semantic names, no fallback drawable

Static verification (no build, no device):
- `:core` and the new `:app` module both compile clean under `kotlinc` 2.4.21 with the serialization + Compose compiler plugins, against the Android 36 platform jar and the cached Compose/AndroidX artifacts.
- The iOS Core + App tree type-checks clean under `swiftc -typecheck` (iOS 17 simulator target).
- No view re-derives a selector: a grep for `sortedBy`/`filter {`/`groupBy`/`dayIndex` finds hits only in `ScreenData.kt` (the glue), never in a screen.

**Exit:** device parity pass — photo + reference screenshot per screen pair, per your cross-platform testing rule. **Pending — your hand task.** The iOS sheet presentation is a modal sheet; the Android one is a full-screen dialog, and the iOS segmented pickers are chip rows on Android, so the screenshot pairs are expected to differ in chrome while matching in content.

### Phase 5 — CI parity gate & cutover — **COMPLETE (static) · the store submission is the hand task**
- CI job running both runners and diffing state; PR cannot merge on divergence.
- Data-migration verification against a real v0.0.19 install on both platforms.
- Version bump to 1.0, promote `specs/`, `ios/`, `android/`, `tools/` to root, delete the Skip tree.
- **Exit:** both stores accept 1.0; Skip removed. **Skip removed ✓ — the store acceptance is pending your submission.**

Delivered — the parity gate:
- `tools/diff-parity.js` — diffs two canonical NDJSON streams record by record and reports the journey, case, step index and **first differing path** (never a whole state object), per `runner.yaml#failure_report`. A difference in record *count* fails too.
- `tools/parity-gate.sh` — builds both runners, runs the same glob through each, diffs the streams. Works in either layout (before or after the cutover).
- `.github/workflows/reflex-parity.yml` — four jobs: `specs` (static validators), `ios` (SwiftPM build + runner), `android` (Gradle build + runner), `gate` (the diff). The gate runs `if: always()` and fails if either runner produced no stream, so a broken runner cannot pass silently.
- `android/settings.gradle.kts` skips the `:app` module when `REFLEX_SKIP_ANDROID_APP=1` — CI builds the headless JVM modules only, so the runner needs no Android SDK.
- **To make it a merge gate:** add **Reflex parity / Parity gate** as a required status check in the branch protection rules. That is a repository setting, not a file.

Delivered — the migration check:
- `tools/verify-migration.sh` — takes a `medical_data.json` captured from a live v0.0.19 install, validates it against `specs/contracts/medical_data.schema.json`, runs it through **both real codecs** and diffs the two streams. It materializes `tools/templates/migration_install.yaml` into a throwaway copy of the specs tree, so the frozen suite is never mutated by a migration check.
- `specs/fixtures/index.yaml` registers `golden_ios` / `golden_android`; the capture script writes them.
- The captures themselves are still a **hand task**: `tools/capture-golden-fixture.sh both` on a virtual device (the script refuses a physical iPhone or Pixel).

Delivered — the 1.0 bump and the cutover:
- `ios/project.yml` — `MARKETING_VERSION: 1.0.0`, `CURRENT_PROJECT_VERSION: 28` (27 was the shipping v0.0.19 build).
- `android/app/build.gradle.kts` — `versionName = "1.0.0"`, `versionCode = 28`, plus the release signing config the Skip build had (`android/app/keystore.properties`, carried over).
- `ios/Assets.xcassets` restored into the new tree (the icon catalog lived in the deleted `Darwin/` tree) and wired through `ASSETCATALOG_COMPILER_APPICON_NAME`.
- `specs/`, `ios/`, `android/`, `tools/` promoted to the repo root; `Sources/`, `Tests/`, `Package.swift`, `Skip.env`, `Darwin/`, `Android/`, `Project.xcworkspace` and the Skip helper scripts deleted.
- `Justfile`, `CLAUDE.md`, `README.md`, `scripts/release/*` and the store docs repointed at the new tree; the release scripts now read the version from `ios/project.yml` and `android/app/build.gradle.kts` instead of `Skip.env`.

**Exit:** both stores accept 1.0; Skip removed. **Skip removed ✓.** The store acceptance is yours: build the signed artifacts, upload them, and submit for review.

## Verification protocol

Every phase boundary: I produce a static-check summary plus a hand-verify checklist (exact commands, expected output, what to eyeball). You run builds, tests, simulators, and emulators. No exceptions — and per your device rule, emulator/simulator only, never the physical Pixel or iPhone.

## Resolved decisions

| # | Question | Decision |
|---|---|---|
| 1 | Notification reset semantics | **Clear all, then reschedule** — the iOS behavior is canonical. Android's incremental path is a bug to fix, not a variant to preserve. Both adapters cancel everything before applying the planner's output. |
| 2 | `vitals_detail` highlight auto-clear | **Keep the 0.9s delay; assert only the eventual state.** Specs check that the highlight clears, never how long it takes. The `Scheduler` test adapter fires immediately. |
| 3 | First-launch demo data | **Keep it.** `app_load` still seeds the demo set on first launch and clears it on onboarding completion. Seed content moves into `specs/fixtures/` so both platforms seed identically. |
| 4 | `vitals_row` icon bug | **Fix in 1.0.** Icon derives from `Vital.VitalType` (weight / bloodPressure / bloodSugar) instead of always rendering `scalemass`. Covered by a `vital_crud` assertion. |
| 5 | Minimum OS versions | **iOS 17**; Android `minSdk` from the existing version catalog. Core is pure Swift / pure JVM, so the floor is a UI-layer concern only. |
| 6 | Mark taken guard + count | **Guard in the reducer on both screens; count DISTINCT medications.** No-op when already taken today (by `medicationId`, legacy substring fallback for pre-1.0 logs). The `min(count,total)` clamp is removed. |
| 7 | Inconsistent demo seed | **Repair it.** Seed log → "Vitamin D3 Dose Taken" with `medicationId = seed_medication_1`. Same 1/3 shape, coherent content; the checked row now exists. |
| 8 | Low-stock badge vs setting | **One selector.** `isLowStock = active && stock <= lowStockThreshold` feeds badge, tint and planner. Adds the `active` gate to the badge (`REVIEW_AT_FREEZE`). |
| 9 | Consultation reminder offset | **Honor `appointmentReminderTime`.** Trigger = `date + offset`, then `> now` guard. Default "1 day before" reproduces legacy −24h exactly. |
| 10 | Dead vital-edit / consultation-delete code | **Add both in 1.0.** The only scope-adding decision. Vital edit (replace by id) and consultation delete (remove by id, immediate, no confirmation) become full journeys. |
| 11 | Rolling day window | **Preserve it.** 7-cell rolling window, center normalized to `startOfDay`. No weekday anchor; unbounded paging; `selectedDate` not reset on metric/range/offset change. |
| 12 | Recent activity "View all" | **Route to Meds → History** (tab 2 + history segment) via one root action, not Vitals. |
| 13 | Blank consultation saves | **Require a doctor name** (trimmed). The other three forms' non-trimming gates are preserved as-is (`REVIEW_AT_FREEZE` if they should be unified). |
| 14 | Vitals value formatting | **Unify** (`REVIEW_AT_FREEZE`): weight `%.1f`, sugar `%.0f`, BP `Int/Int`; unit appended exactly once. Fixes the doubled unit and the sugar precision fork. |
| 15 | Rendered date strings | **Not contract** (`REVIEW_AT_FREEZE`). State holds Instants; locale rendering verified in the Phase 4 device pass. |
| 16 | Sort tie-breaks | **Every sort is `(date asc, id asc)`** (`REVIEW_AT_FREEZE`). Pins previously undefined order so two implementations can diff clean. |

Full detail, legacy evidence and rationale for each: `specs/behavior/decisions.yaml`. Preserved quirks Q1–Q18 are listed there so an implementer does not "helpfully" fix them and break parity.

Phase 1 complete; the behavior contract is frozen.

## Found during Phase 0 — resolved in Phase 1

These surfaced while extracting contracts. Each was real v0.0.19 behavior; all five now have a signed-off resolution (see the decision table above and `decisions.yaml`).

1. **Dashboard "Mark taken" is unguarded.** `MedicationTrackerView` guards the row tap with `isTaken`; `DashboardView` calls `markMedicationTaken(nextMed)` on every tap. Repeated taps decrement stock repeatedly and inflate `takenTodayCount`, which counts logs rather than distinct medications. Two screens, same action, different rules. **Resolved: D6** — guard in the reducer, count distinct medications.
2. **The demo seed is internally inconsistent.** Its log reads "Omega-3 Dose Taken" but no seeded medication is named Omega-3. Dashboard shows 1/3 taken; the Meds summary also shows 1/3, yet zero rows render as checked. Decision #3 keeps the seed, so this needs an explicit call. **Resolved: D7** — seed repaired to Vitamin D3 with a `medicationId`.
3. **Low-stock badge ignores the setting.** The planner alerts at `stock <= lowStockThreshold`, but the Inventory badge is hardcoded to `stock < 5`. At the default threshold a med with stock 5 alerts without showing a badge; at threshold 10 the divergence is larger. **Resolved: D8** — one `isLowStock` selector for badge and planner.
4. **Unmapped icons render as a plus sign on Android.** `IconMapper` falls back to `ic_add`. Five icons hit it today — including `minus.circle.fill`, so "unlink medication" shows a **plus**. Also `drop.fill` (blood sugar), `sparkles`, `arrow.up.right`, `arrow.right`. The new contract has no fallback; five new drawables are required in Phase 4. **Resolved: `icons.yaml`** — no fallback icon; the five drawables are a Phase 4 task, and D4 fixes the vitals row icon.
5. **Consultation reminders ignore the reminder setting.** They use a fixed −24h offset rather than `appointmentReminderTime`. Preserved as-is in `services.yaml`; flag if it should change. **Resolved: D9** — consultations now honor `appointmentReminderTime`; `services.yaml` updated.