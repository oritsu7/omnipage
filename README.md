# OmniPage (Phase 0: skeleton)

Package: `com.ansh.omnipage` | Kotlin 2.0.21 | AGP 8.7.3 | Gradle 8.9 | JDK 17 | minSdk 24 / target 35

## Build and install
```bash
export ANDROID_HOME=$HOME/android-sdk     # or create local.properties with sdk.dir=...
chmod +x gradlew
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Reader:*
```

## Release signing
```bash
keytool -genkey -v -keystore release.jks -alias reader -keyalg RSA -keysize 2048 -validity 10000
```
Create `keystore.properties` (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, then `./gradlew assembleRelease`.

## Licenses
- Fonts: Inter, Literata, Source Serif 4: SIL OFL 1.1 (see `licenses/`)
- Jetpack / Compose / Material 3: Apache 2.0
