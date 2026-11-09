# Implementation Plan

> **Superseded.** This plan describes the Skip-based app that shipped as v0.0.19. The
> 1.0 rewrite is tracked in [REFLEX_MIGRATION_PLAN.md](REFLEX_MIGRATION_PLAN.md); the
> current build and release commands are in the `Justfile`.

## Overview

This document outlines the planned changes for the Skip mobile healthcare app based on user requirements.

---

## 1. Appointments Page

### 1.1 Notification Icon Click → Delete
- **File**: `Sources/SharedUI/Medical/AppointmentsView.swift`
- **Current Location**: Line 209 (bell icon display)
- **Change**: Make the bell icon button trigger `viewModel.deleteAppointment(appt)` instead of toggling notify state
- **Rationale**: User wants clicking notification icon to delete appointment

### 1.2 Add Appointment Auto-Populates Selected Date
- **Files**:
  - `Sources/SharedUI/Medical/AppointmentsView.swift` (pass selected date)
  - `Sources/SharedUI/Medical/AddAppointmentView.swift` (receive and use date)
- **Change**: Pass `selectedDate` to `AddAppointmentView` and pre-populate the date picker
- **Rationale**: When user taps + on a specific date, the form should pre-fill with that date

---

## 2. Vitals Page

### 2.1 Remove Back Arrow
- **File**: `Sources/SharedUI/Medical/VitalsView.swift`
- **Current Location**: Lines 84-87 (headerView)
- **Change**: Remove the chevron.left Button entirely
- **Rationale**: Vitals is a tab, no navigation stack needed

### 2.2 Remove Calendar Icon
- **File**: `Sources/SharedUI/Medical/VitalsView.swift`
- **Current Location**: Lines 98-100
- **Change**: Remove the calendar icon Button
- **Rationale**: Calendar functionality is not relevant to Vitals view

---

## 3. Consults Page

### 3.1 Remove Settings Icon
- **File**: `Sources/SharedUI/Medical/ConsultationsView.swift`
- **Current Location**: Lines 99-104
- **Change**: Remove the gearshape button
- **Rationale**: No settings page exists; can be added later if needed

### 3.2 Add Edit Functionality
- **Files**:
  - `Sources/SharedUI/Medical/ConsultationsView.swift` (add edit button)
  - `Sources/SharedUI/Medical/NewConsultationView.swift` (reuse for editing)
  - `Sources/AppModel/MedicalViewModel.swift` (update method already exists)
- **Changes**:
  1. Add "Edit" button in `consultationCard()` next to Delete
  2. Modify `NewConsultationView` to accept optional `Consultation` parameter
  3. Add `@State private var editingConsultation: Consultation?` in ConsultationsView
  4. Present sheet with edit mode when Edit tapped
- **Note**: `updateConsultation()` already exists in MedicalViewModel (line 217-222)

---

## 4. Home Page (Dashboard)

### 4.1 Cleanup Header
- **File**: `Sources/SharedUI/Medical/DashboardView.swift`
- **Locations**:
  - Line 107: Dummy name "Alex Johnson" → Replace with "User" or remove
  - Lines 127-141: Offline mode indicator → Remove entirely
- **Changes**:
  1. Remove the offline badge (wifi.slash + "OFFLINE" text)
  2. Update the greeting to show dynamic or placeholder name

---

## 5. General Changes

### 5.1 Clear Default Data After Onboarding
- **File**: `Sources/SharedUI/Medical/MainTabView.swift`
- **Current Location**: Lines 83-86 (onboarding completion)
- **Change**: Call `viewModel.clearData()` when onboarding completes
- **Code**:
  ```swift
  hasCompletedOnboarding = true
  await viewModel.clearData()  // Add this line
  ```
- **Rationale**: Remove mock/sample data after user completes tutorial

### 5.2 Automatic Keyboard Dismissal
- **Approach**: Create a view modifier that dismisses keyboard on tap outside
- **New File**: `Sources/SharedUI/HideKeyboard.swift`
- **Modifier**:
  ```swift
  struct HideKeyboardModifier: ViewModifier {
      func body(content: Content) -> some View {
          content
              .onTapGesture {
                  UIApplication.shared.sendAction(
                      #selector(UIResponder.resignFirstResponder),
                      to: nil, from: nil, for: nil
                  )
              }
      }
  }
  ```
- **Apply to**:
  - `AddAppointmentView.swift`
  - `AddMedicationView.swift`
  - `AddVitalView.swift`
  - `NewConsultationView.swift`
- **Implementation**: Add `.modifier(HideKeyboardModifier())` to the root view of each form

---

## Summary of Files to Modify

| File | Changes |
|------|---------|
| `Sources/SharedUI/Medical/AppointmentsView.swift` | Notification icon → delete, pass selectedDate |
| `Sources/SharedUI/Medical/AddAppointmentView.swift` | Accept pre-selected date parameter |
| `Sources/SharedUI/Medical/VitalsView.swift` | Remove back arrow, remove calendar icon |
| `Sources/SharedUI/Medical/ConsultationsView.swift` | Remove settings, add edit functionality |
| `Sources/SharedUI/Medical/NewConsultationView.swift` | Add edit mode support |
| `Sources/SharedUI/Medical/DashboardView.swift` | Remove offline badge, clean up header |
| `Sources/SharedUI/Medical/MainTabView.swift` | Clear data after onboarding |
| `Sources/SharedUI/HideKeyboard.swift` | **NEW** - Keyboard dismissal modifier |

---

## Implementation Order

1. Create `HideKeyboard.swift` modifier (foundational)
2. Apply keyboard dismissal to all form views
3. Vitals page cleanup (simple removals)
4. Dashboard header cleanup
5. Appointments page (notification + date)
6. Consults page (settings removal + edit)
7. Onboarding data cleanup

---

## Notes

- All changes maintain existing code style and patterns
- Platform-specific `#if SKIP` guards preserved where needed
- ViewModel already has `updateConsultation()` method - just needs to be called
- Mock data population in `MedicalViewModel.populateMockData()` will be cleared after onboarding
