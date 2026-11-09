# PharmTrack - App Store Setup Guide

## iOS App Store

### Basic Information
- **App Name**: PharmTrack
- **Subtitle**: Medication & Health Tracking
- **Bundle ID**: `com.tunnxo.medtrack`
- **Version**: 1.0.0
- **Build**: `ios/project.yml` (the Xcode project is generated with xcodegen)

### Required Info in Xcode

The Xcode project is **generated** — edit `ios/project.yml`, then run
`just ios-generate`. Never edit the generated `.xcodeproj`.

1. **Bundle Identifier**
   - In `ios/project.yml` → target `PharmTrack` → `PRODUCT_BUNDLE_IDENTIFIER`
   - Value: `com.tunnxo.medtrack`

2. **Display Name**
   - In `ios/project.yml` → `info.properties.CFBundleDisplayName`
   - Value: `PharmTrack`

3. **Version and Build**
   - `ios/project.yml` → `settings.base.MARKETING_VERSION` and `CURRENT_PROJECT_VERSION`
   - Current: `1.0.0` / `28` (the build number must exceed v0.0.19's 27)

4. **App Icons & Launch Screen**
   - The icon catalog lives at `ios/Assets.xcassets/AppIcon.appiconset/`
     (the 1024x1024 marketing icon is `AppIcon~ios-marketing.png`)
   - The launch screen is the `UILaunchScreen` key in `ios/project.yml`

5. **Signing**
   - `just generate-configs` writes `ios/User.xcconfig` from `.env`
     (`APPLE_TEAM_ID`)
   - Automatic signing, team from the xcconfig

### Screenshots (Required for App Store)
Create 6 screenshots for each device (iPhone Pro Max recommended):
1. Dashboard/Home screen
2. Medication tracking
3. Consultation records
4. Appointments view
5. Vitals tracking
6. Features overview

### Description
```
PharmTrack - Your personal medication and health management assistant.

Track medications, consultations, appointments, and vital signs all in one place. 
PharmTrack helps you stay organized and informed about your health journey.

Features:
• Easy medication tracking and reminders
• Store consultation records with doctors
• Manage appointments efficiently
• Track vital signs (blood pressure, temperature, weight, etc.)
• Works offline - access your health data anytime
• Clean, intuitive interface

Privacy: Your health data is stored locally on your device.
```

### Keywords
`medication, health, tracker, pharmacy, vitals, appointments, medical`

### Category
Medical

### Support URL
`https://your-domain.com/support`

### Privacy Policy URL
`https://your-domain.com/privacy`

---

## Android Play Store

### Basic Information
- **App Name**: PharmTrack
- **Package Name**: `com.tunnxo.medtrack` (in `android/app/build.gradle.kts`)
- **Version Code**: 28 (must exceed the shipping v0.0.19 build, 27)
- **Version Name**: 1.0.0
- **Min SDK**: 24
- **Target SDK**: 36

### Required Files

1. **Build Configuration**
   - Edit `android/app/build.gradle.kts`:
   ```kotlin
   defaultConfig {
       applicationId = "com.tunnxo.medtrack"
       versionCode = 28
       versionName = "1.0.0"
   }
   ```

2. **AndroidManifest.xml**
   - File: `android/app/src/main/AndroidManifest.xml`
   - `android:label="@string/app_name"` (the string is in `res/values/strings.xml`)
   - `android:icon="@drawable/ic_medical"`

3. **App Icon**
   - The launcher icon is the vector drawable `android/app/src/main/res/drawable/ic_medical.xml`
   - The 512x512 Play listing icon is uploaded in the Play Console, not in the repo

4. **Screenshot Assets**
   - Create 4-8 screenshots (1080x1920 or higher)
   - Place in Play Console during upload

5. **Feature Graphic**
   - Size: 1024x500 px
   - Showcase key features

### Play Store Description
```
PharmTrack - Medication & Health Management

Your personal health assistant for tracking medications, doctor consultations, 
appointments, and vital signs.

Key Features:
✓ Medication Tracker - Never miss a dose with easy tracking
✓ Consultation Records - Keep detailed notes from doctor visits
✓ Appointment Manager - Organize and track medical appointments
✓ Vitals Log - Monitor blood pressure, temperature, weight, and more
✓ Offline Access - All data stored locally, no internet required
✓ Dark Mode - Comfortable viewing anytime

PharmTrack puts you in control of your health information. All data is stored 
securely on your device with no cloud upload or third-party sharing.

Perfect for managing chronic conditions, medication schedules, and staying 
organized with your healthcare journey.

Privacy Policy: https://your-domain.com/privacy
```

### Play Store Keywords
`medication tracker, health tracking, pharmacy, vitals, appointments, medical records`

### Content Rating
- Medical/Health Apps
- Rating: Suitable for all ages

### Target Audience
- Age: 12+
- Health conscious users
- People managing medications

### App Signing

The upload key from the shipping app is already configured:
```bash
# The signing properties live at android/app/keystore.properties
# (storeFile, storePassword, keyAlias, keyPassword — gitignored).
# Regenerate them from .env with: just generate-configs
```

### Checklist Before Submission

- [ ] All TextFields render natively (no custom borders on Android)
- [ ] Icons display correctly on both platforms
- [ ] Onboarding flow works smoothly
- [ ] App name is "PharmTrack" (no spaces in middle)
- [ ] Bundle/Package ID set correctly
- [ ] Version numbers match across platforms
- [ ] Screenshots uploaded (min 2, recommended 4-8)
- [ ] Description and keywords added
- [ ] Privacy policy URL set
- [ ] Test on actual devices (iOS 14+ and Android 7+)
- [ ] Offline functionality verified
- [ ] Dark mode tested
- [ ] Performance tested with various data loads

---

## Next Steps

1. Register Apple Developer Account (if not done)
   - Visit: https://developer.apple.com
   
2. Register Google Play Developer Account (if not done)
   - Visit: https://play.google.com/console
   
3. Create apps in respective stores
   - iOS: App Store Connect
   - Android: Google Play Console
   
4. Update Bundle ID/Package Name in code
   
5. Generate app icons and screenshots
   
6. Set up signing certificates (iOS) and keystores (Android)
   
7. Build release versions:
   ```bash
   just ios-release       # archive + IPA export
   just android-release   # signed AAB
   ```
   
8. Upload and submit for review:
   ```bash
   just upload-ios      # TestFlight
   just upload-android  # Google Play (internal track)
   ```
