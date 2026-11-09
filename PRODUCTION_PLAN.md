# Production Readiness Plan for Healthcare App

> Generated: 2026-03-18
>
> **Superseded.** This plan describes the Skip-based app that shipped as v0.0.19. The
> 1.0 rewrite is tracked in [REFLEX_MIGRATION_PLAN.md](REFLEX_MIGRATION_PLAN.md); the
> current build and release commands are in the `Justfile`.

## Executive Summary

This Skip-based healthcare app has good architecture but **most interactive features are non-functional placeholders**. The app would fail App Store review because users cannot actually perform core tasks like adding medications, recording vitals, or scheduling appointments.

---

## Current App Overview

### Features and Screens

| Screen | File | Description |
|--------|------|-------------|
| **Dashboard** | `DashboardView.swift` | Home screen with greeting, today's appointments/medications, quick access cards, recent history |
| **Consultations** | `ConsultationsView.swift` | View doctor consultations/treatments with search |
| **Medications** | `MedicationTrackerView.swift` | Medication tracking by schedule (Morning/Afternoon/Evening) with stock tracking |
| **Appointments** | `AppointmentsView.swift` | Calendar view with horizontal day strip and appointment cards |
| **Vitals** | `VitalsView.swift` | Health vitals (Weight, Blood Pressure, Blood Sugar) with trend charts |
| **Add Vital** | `AddVitalView.swift` | Modal to add new vital entries |
| **New Consultation** | `NewConsultationView.swift` | Modal to add new consultations |
| **Onboarding** | `MainTabView.swift` | 5-tab onboarding overlay flow |

### Architecture

- **Framework**: Swift/SwiftUI with Skip transpilation to Android (Kotlin)
- **Architecture**: MVVM with repository pattern
- **Targets**: AppCore, AppModel, SharedUI (SPM)
- **Storage**: JSON file-based via `LocalMedicalRepository`

---

## Known Issues Summary

| Issue | Location | Impact |
|-------|----------|--------|
| Empty button actions | All views | Users cannot navigate or perform actions |
| Non-functional search | MedicationTrackerView, ConsultationsView | Search appears but doesn't work |
| Blood Pressure single value | AddVitalView, Vital model | Only stores one value, not systolic/diastolic |
| No delete functionality | All views | Cannot remove any data |
| No edit functionality | All views | Cannot modify existing data |
| Incomplete tab views | MedicationTrackerView | Only "Today" tab has content |
| Onboarding clears data | MainTabView | Completing onboarding regenerates mock data |
| Calendar not functional | AppointmentsView | Days not selectable, navigation broken |
| Charts are static | VitalsView | Hardcoded path, not data-driven |
| Missing AddMedicationView | - | No way to add medications |
| Missing AddAppointmentView | - | No way to add appointments |

---

## Phase 1: Fix Core CRUD Operations (Critical)

### 1.1 Add Delete Functionality to ViewModel

**File**: `Sources/AppModel/MedicalViewModel.swift`

Add delete methods:
```swift
func deleteVital(_ vital: Vital)
func deleteMedication(_ medication: Medication)
func deleteAppointment(_ appointment: Appointment)
func deleteConsultation(_ consultation: Consultation)
```

### 1.2 Add Edit Functionality

**File**: `Sources/AppModel/MedicalViewModel.swift`

Add update methods or make models mutable with update functions.

### 1.3 Implement Delete/Edit UI

**Files**: All views in `Sources/SharedUI/Medical/`
- Add swipe-to-delete or delete buttons
- Add edit buttons that open pre-filled forms

---

## Phase 2: Fix Search and Filter (Critical)

### 2.1 Medication Search

**File**: `Sources/SharedUI/Medical/MedicationTrackerView.swift:78`

**Current (broken)**:
```swift
TextField("Search medications...", text: .constant(""))
```

**Fix**: Change to `@State var searchText = ""` and bind properly.

### 2.2 Consultation Search

**File**: `Sources/SharedUI/Medical/ConsultationsView.swift`

Implement actual filtering logic when search text changes.

---

## Phase 3: Fix Vital Tracking (Critical)

### 3.1 Blood Pressure Dual Values

**File**: `Sources/SharedUI/Medical/AddVitalView.swift`

- Add second input field for diastolic pressure
- Display as "120/80" format
- Vital model already has `value2` but it's unused

### 3.2 Make Charts Dynamic

**File**: `Sources/SharedUI/Medical/VitalsView.swift`

Replace hardcoded `ChartPath` with data-driven path using actual `vitals` array filtered by vital type.

### 3.3 Add Vital Type Selection to AddVitalView

Currently only adds weight. Need picker to select vital type (Weight, Blood Pressure, Blood Sugar).

---

## Phase 4: Fix Navigation & Actions (Critical)

### 4.1 Dashboard Actions

**File**: `Sources/SharedUI/Medical/DashboardView.swift`

Add actions to:
- Bell icon (notifications)
- "View All" buttons (navigate to respective screens)
- Quick access cards (navigate to add forms)

### 4.2 Medication Tracker Actions

**File**: `Sources/SharedUI/Medical/MedicationTrackerView.swift`

- "+" button opens AddMedicationView (doesn't exist - needs creation)
- Checkmark marks medication as taken (needs state tracking)

### 4.3 Appointments Calendar

**File**: `Sources/SharedUI/Medical/AppointmentsView.swift`

- Make day cells selectable
- Chevron navigation changes displayed date
- "+" button opens AddAppointmentView

### 4.4 Vitals FAB

Already exists, needs to work (opens AddVitalView)

---

## Phase 5: Complete Tab Content (High)

### 5.1 Medication Tabs

**File**: `Sources/SharedUI/Medical/MedicationTrackerView.swift`

Implement all 4 tabs:

1. **Today** - Currently works
2. **Schedule** - Show medications grouped by time (Morning/Afternoon/Evening)
3. **Inventory** - Show stock levels with low-stock warnings
4. **History** - Show taken/missed medication history

---

## Phase 6: Fix Onboarding (High)

### 6.1 Onboarding Logic

**File**: `Sources/SharedUI/Medical/MainTabView.swift`

- Remove data clear on onboarding completion
- Only set `hasCompletedOnboarding = true`
- Don't regenerate mock data - use existing or create fresh

---

## Phase 7: Create Missing Views (High)

### 7.1 AddMedicationView

Create form to add new medications with:
- Name, dosage, frequency
- Schedule times (Morning/Afternoon/Evening)
- Stock quantity

### 7.2 AddAppointmentView

Create form to add appointments with:
- Doctor name, specialty
- Date and time
- Location/notes

### 7.3 AddConsultationView

Already exists as `NewConsultationView.swift` - verify it works.

---

## Phase 8: Configuration for App Stores (High)

### 8.1 Android Configuration

- [ ] Verify `Android/app/keystore.properties` has valid signing keys
- [ ] Set `ANDROID_APPLICATION_ID` in `Skip.env`
- [ ] Configure `Android/app/src/main/AndroidManifest.xml` with proper permissions
- [ ] Add app icons to `Android/app/src/main/res/mipmap-*/`

### 8.2 iOS Configuration

- [ ] Set `PRODUCT_BUNDLE_IDENTIFIER` in `Skip.env`
- [ ] Verify `DEVELOPMENT_TEAM` is correct in `Darwin/User.xcconfig`
- [ ] Configure `Darwin/App/Info.plist` with:
  - Privacy descriptions (camera, health, notifications)
  - App icons
  - Launch screen

### 8.3 App Store Listings

- [ ] Prepare app screenshots for both platforms
- [ ] Write app descriptions
- [ ] Prepare privacy policy
- [ ] Set up app categories

---

## Phase 9: Testing & Polish (Medium)

### 9.1 Test All Flows

- [ ] Add medication → appears in list → mark as taken → delete
- [ ] Add vital → appears in chart → edit → delete
- [ ] Add appointment → appears in calendar → delete
- [ ] Add consultation → appears in list → delete

### 9.2 Platform-Specific Testing

- [ ] Test on iOS Simulator
- [ ] Test on Android Emulator
- [ ] Test on physical device if possible

### 9.3 Error Handling

- [ ] Add try/catch for file operations
- [ ] Show user-friendly error messages
- [ ] Handle empty states gracefully

---

## Priority Order Summary

| Priority | Task | Effort |
|----------|------|--------|
| P0 | Add delete methods to ViewModel | Low |
| P0 | Fix search field bindings | Low |
| P0 | Add blood pressure dual values | Medium |
| P0 | Make charts dynamic | Medium |
| P1 | Fix all button actions | Medium |
| P1 | Complete medication tabs | Medium |
| P1 | Fix onboarding | Low |
| P1 | Create AddMedicationView | Medium |
| P1 | Create AddAppointmentView | Medium |
| P2 | Configure for App Store release | Medium |
| P2 | Test all flows | High |

---

## Recommended First Steps

1. **Start with ViewModel** - Add delete methods so UI has something to connect to
2. **Fix search** - Quick win that makes apps feel responsive
3. **Add vital type picker** - Makes vitals actually usable
4. **Connect buttons** - Navigate to forms that need to be created
5. **Create missing forms** - AddMedicationView, AddAppointmentView

---

## Build Commands Reference

| Command | Description |
|---------|-------------|
| `just setup` | Resolve Swift package dependencies |
| `just android-build` | Build Android Debug APK |
| `just android-run` | Install and launch on Android emulator |
| `just ios-build` | Build iOS App for Simulator |
| `just ios-run` | Run iOS App on Simulator |
| `just test` | Run tests on both platforms |

**Direct commands:**
- `swift test` - Run Swift tests
- `skip verify` - Validate Skip setup
- `skip checkup` - Check Skip prerequisites
- Open `Project.xcworkspace` in Xcode for iOS development
