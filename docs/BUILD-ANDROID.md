# Building WiLauncher for Android

## Requirements

- JDK 21 (the one bundled with Android Studio works: `C:\Program Files\Android\Android Studio\jbr`).
- Android SDK with platform 36, build-tools 35+, CMake 3.22.1 and NDK 27.1.12297006.
- **The project path must contain only ASCII characters** (the Android Gradle plugin refuses others on Windows), e.g.
  `D:\WiLauncherMobile`.
- `local.properties`: `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk`, optionally `arch=arm64`.

## Build

```bash
./gradlew :FCL:assembleRelease -Darch=arm64     # arm64-v8a, most phones
./gradlew :FCL:assembleRelease -Darch=arm       # armeabi-v7a, old 32-bit phones
./gradlew :FCL:assembleRelease -Darch=x86_64    # emulators
```

The APK lands in `FCL/build/outputs/apk/release/WiLauncher-release-<version>-<abi>.apk`.

## Signing

Release builds are signed with the key described by `~/.wilauncher-signing/android-signing.properties`
(or the file named in `WILAUNCHER_SIGNING_PROPERTIES`):

```
storeFile=C:/Users/<you>/.wilauncher-signing/wilauncher-android.jks
storePassword=...
keyAlias=wilauncher
keyPassword=...
```

Keep that key safe: Android installs an update only if it is signed with the same key. Without the file the build is
signed with the Android debug key, so anyone can build the sources (such APKs can't update the official ones).

## Catalog and touch layout

- Catalog: edit `catalog/catalog.source.json`, run `node scripts/build-catalog.mjs`. It writes the bundled
  `FCL/src/main/assets/catalog/catalog.json`; publish the same file as `android/catalog.json` in WiLauncher-releases so
  installed apps pick it up (the app keeps the newest of the bundled and downloaded catalog by `version`).
- Touch layout: edit `scripts/build-controls.mjs`, run it. The layout is copied to `/WiLauncher/control` on first start;
  an existing copy on a phone is not replaced.

## Release

1. Bump `versionCode` / `versionName` in `FCL/build.gradle.kts`, add a `CHANGELOG.md` section.
2. Build the three ABIs.
3. Publish them in WiLauncher-releases as `WiLauncher-Android-<version>-<abi>.apk` under the tag `android-v<version>`
   and update `android/version_map.json` there (the in-app updater replaces `-all` in its `url` with the device ABI).
