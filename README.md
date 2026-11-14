# Candy Match (Kotlin, AdMob)

## Get the APK - option A: Android Studio
1. Install Android Studio, File > Open > this folder, wait for Gradle sync.
2. Build > Build Bundle(s)/APK(s) > Build APK(s). File: app/build/outputs/apk/debug/app-debug.apk
3. Copy it to your phone (or plug in the phone with USB debugging and press Run).

## Option B: no install, GitHub Actions
1. Create a GitHub repo, upload this folder (keep .github/workflows/build.yml).
2. Actions tab > "Build APK" > when green, download artifact CandyMatch-debug-apk.

## Before Play Store
- strings.xml: replace the TEST AdMob app id + interstitial id with yours.
- app/build.gradle.kts: change applicationId, raise targetSdk to Play's current requirement.
- Build a signed release AAB: Build > Generate Signed Bundle.
- Never tap your own live ads while testing (use test IDs until published).
