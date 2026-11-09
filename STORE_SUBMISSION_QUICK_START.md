# PharmTrack - App Store Submission Quick Start

## 🚀 Pre-Submission Checklist (30 mins)

- [ ] Version set to 1.0.0 (both platforms)
- [ ] App name is "PharmTrack" (no typos)
- [ ] Build numbers incremented
- [ ] All UI tested on actual devices
- [ ] Onboarding flow works smoothly
- [ ] Dark/Light mode toggle works
- [ ] Offline functionality verified
- [ ] TextFields render natively (no custom borders on Android)

---

## 📱 iOS App Store (15-20 mins)

### 1. Create App ID
```
1. Visit: https://developer.apple.com/account
2. Go to Identifiers → App IDs
3. Click "+"
4. Select "App"
5. Bundle ID: com.pharmtrack.ios
6. App Services: None needed initially
7. Register
```

### 2. Create Provisioning Profile
```
1. Certificates, Identifiers & Profiles
2. Under Development: Add provisioning profile
3. Select App ID: com.pharmtrack.ios
4. Select certificate
5. Download and install
```

### 3. Xcode Configuration
```
File: ios/project.yml   (the project is generated — run `just ios-generate`)
1. Target "PharmTrack":
   - Bundle Identifier: com.tunnxo.medtrack
   - Display Name: PharmTrack
   - Version: 1.0.0
   - Build: 28
2. Signing & Capabilities:
   - Select team
   - Provisioning profile: Auto
```

### 4. Create App in App Store Connect
```
1. Visit: https://appstoreconnect.apple.com
2. Apps → "+"
3. Name: PharmTrack
4. Primary Language: English
5. Bundle ID: com.pharmtrack.ios
6. SKU: PHARMTRACK001
```

### 5. Fill App Information
```
1. App Information:
   - Category: Medical
   - Subtitle: Medication & Health Tracking
2. Description: [See APPSTORE_METADATA.md]
3. Keywords: [See APPSTORE_METADATA.md]
4. Support URL: https://your-domain.com/support
5. Privacy Policy URL: https://your-domain.com/privacy
```

### 6. Add Screenshots & Icon
```
1. App Previews & Screenshots:
   - Upload for iPhone 6.7-inch, iPhone 5.5-inch
   - Min 2 screenshots, recommended 4-8
2. App Icon:
   - 1024x1024 PNG
   - Place in: ios/Assets.xcassets/AppIcon.appiconset/
```

### 7. Build & Upload
```bash
# In Xcode
1. Product → Build For → Running
2. Product → Archive
3. Organizer → Distribute App
4. App Store Connect
5. Follow upload wizard
```

### 8. Submit for Review
```
1. App Store Connect → TestFlight → Internal Testing
2. Test for 1-2 hours on actual device
3. Once verified, go to Version Release
4. Click "Submit for Review"
5. Answer review questions
6. Select availability date
7. Submit
```

**Expected Review Time**: 24-48 hours

---

## 🤖 Google Play Store (15-20 mins)

### 1. Create Signing Key
```bash
keytool -genkey -v -keystore pharmatrack-release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias pharmatrack-key \
  -storepass YOUR_PASSWORD \
  -keypass YOUR_PASSWORD
```

Move to: `Android/app/pharmatrack-release.keystore`

### 2. Create keystore.properties
```
File: Android/app/keystore.properties

storeFile=pharmatrack-release.keystore
storePassword=YOUR_PASSWORD
keyAlias=pharmatrack-key
keyPassword=YOUR_PASSWORD
```

### 3. Update Build Configuration
```
Android/app/build.gradle.kts:
- Already updated to: com.pharmtrack (1.0.0)
```

### 4. Build Release APK/AAB
```bash
# From Android directory
./gradlew bundleRelease

# Output:
# Android/app/build/outputs/bundle/release/app-release.aab
```

### 5. Create App in Google Play Console
```
1. Visit: https://play.google.com/console
2. Create new app
3. Name: PharmTrack
4. App type: App
5. Category: Medical
```

### 6. Fill App Details
```
1. App Information:
   - Short description: [See APPSTORE_METADATA.md]
   - Full description: [See APPSTORE_METADATA.md]
2. Category: Medical
3. Content rating: Everyone
4. Privacy policy: https://your-domain.com/privacy
5. Support email: your-email@example.com
```

### 7. Add Graphics
```
1. Screenshots (required):
   - 4-8 screenshots (1080x1920 px minimum)
   - Place in: play-console UI
2. Feature Graphic:
   - 1024x500 px
   - PNG format
3. App Icon:
   - 512x512 PNG
```

### 8. Add Release
```
1. Release → Create new release
2. Select: Production (or Internal Testing first)
3. Upload: app-release.aab
4. Review content rating:
   - Medical apps: OK
   - All ages: Fine
5. Submit
```

**Expected Review Time**: 2-3 hours (usually faster)

---

## 📋 Content to Prepare

### Minimum Required
- [ ] App Icon (1024x1024)
- [ ] 2-4 Screenshots (1080x1920)
- [ ] Short description (80 chars)
- [ ] Full description (4000 chars)
- [ ] Privacy policy URL
- [ ] Support email

### Recommended
- [ ] 8 Screenshots (better showcase)
- [ ] Feature graphic (1024x500)
- [ ] Preview video (15-30 seconds)
- [ ] About page/website
- [ ] Blog post

---

## 🎯 Marketing After Launch

### Immediate (Day 1)
- [ ] Share announcement on social media
- [ ] Notify email list
- [ ] Update website/portfolio
- [ ] Share link in developer communities

### Week 1
- [ ] Respond to user reviews
- [ ] Monitor crash reports
- [ ] Fix any bugs found
- [ ] Gather user feedback

### Week 2+
- [ ] Submit to app review sites
- [ ] Create tutorial blog posts
- [ ] Reach out to health/tech bloggers
- [ ] Consider app marketing

---

## 🔗 Important Links

**Development Accounts:**
- iOS: https://developer.apple.com/account
- Android: https://play.google.com/console

**Metadata & Marketing:**
- See: APP_STORE_SETUP.md (detailed)
- See: APPSTORE_METADATA.md (copy & descriptions)

**Monitoring:**
- iOS: App Store Connect → Analytics
- Android: Google Play Console → Statistics

---

## ⚠️ Common Issues & Solutions

### iOS
**Issue**: Bundle ID doesn't match  
**Solution**: Verify Bundle ID in Xcode matches App ID

**Issue**: Screenshots too small  
**Solution**: Use 1242x2688 (5.7") or 1170x2532 (6.1")

**Issue**: Rejected for unclear functionality  
**Solution**: Add more descriptive screenshots, demo video

### Android
**Issue**: AAB too large  
**Solution**: Run `./gradlew bundleRelease --optimize-for=universal`

**Issue**: Crash on launch  
**Solution**: Test on actual device, check logs in Play Console

---

## 📞 Support Resources

**Apple**
- Developer Support: https://developer.apple.com/support
- App Store Review Guidelines: https://developer.apple.com/app-store/review/guidelines

**Google**
- Play Console Help: https://support.google.com/googleplay/android-developer
- Policy Center: https://play.google.com/intl/en/about/developer-content-policy

---

## 🎉 After Launch

1. **Monitor Performance**
   - Check daily crash rates
   - Monitor user ratings
   - Track download numbers

2. **Gather Feedback**
   - Read user reviews
   - Respond to feedback
   - Note feature requests

3. **Plan Updates**
   - Version 1.0.1: Bug fixes
   - Version 1.1: New features
   - Version 2.0: Major improvements

4. **Keep Current**
   - Update SDKs as needed
   - Fix any deprecated APIs
   - Support new OS versions

