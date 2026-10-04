# Building wonderPlay

## Build

Use JDK 17, Android SDK platform 36 and Build Tools 35.0.0 or newer. Set `ANDROID_HOME`, or create an untracked `local.properties` containing `sdk.dir=/your/sdk`.

```sh
git clone https://github.com/johanjosesaju3608/wonderPlay.git
cd wonderPlay
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Start an emulator or connect an Android device with USB debugging, then run:

```sh
./gradlew connectedDebugAndroidTest
```

Gradle 8.13 (checksum-pinned), AGP 8.13.2 and Kotlin 2.3.0 are a tested compatible toolchain. Dependency versions are centralized in `gradle/libs.versions.toml`.

## Signed release builds

Keep the same private release keystore for compatible updates. Never commit the key or passwords.

```sh
export WONDERPLAY_KEYSTORE=/secure/location/wonderplay-release.jks
export WONDERPLAY_KEY_ALIAS=wonderplay
export WONDERPLAY_STORE_PASSWORD='your-store-password'
export WONDERPLAY_KEY_PASSWORD='your-key-password'
./gradlew testDebugUnitTest lintRelease assembleRelease
./scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk
```

Without signing variables, release output is unsigned and cannot be installed. The published APK uses a dedicated release key; a debug APK has a different signature. The release keystore is stored privately outside this repository.

A manual CI workflow template is in `docs/ci/android.yml`. The currently available GitHub credential cannot publish active workflow files. To enable it with an appropriately authorized credential, move the template to `.github/workflows/android.yml`.

