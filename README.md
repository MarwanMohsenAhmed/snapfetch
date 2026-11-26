# SnapFetch

Snaptube-interface clone — native Android app in Kotlin + Jetpack Compose
(package `com.snapfetch.app`).

- Internal player with unlimited editable playlists
- Resumable + batch downloads, data saver, video size reduction
- Target: low-end / budget phones; APK ≤ 15 MB per ABI (splits), sideload only

## Build

Requirements: Android Studio or JDK 17, Android SDK 37, and Gradle 9.6.0.

```bash
./gradlew assembleRelease
```

APKs land in `app/build/outputs/apk/release/`.

GitHub Actions also builds release APKs on every push and pull request. To build
one on demand, run **Build APK** from the repository's Actions tab. Download
the `snapfetch-apk` artifact from the completed workflow run.

Note: `gradle/wrapper/gradle-wrapper.jar` is intentionally not in this repo —
run `gradle wrapper --gradle-version 9.6.0` once (or let Android Studio sync)
if `gradlew` complains about the missing wrapper jar.

Release signing falls back to the debug keystore when
`~/.android/snapfetch-release.jks` does not exist (see
`app/build.gradle.kts`). The automatically built APK therefore uses a debug
key and is intended for sideloading, not Play Store publishing.

## Status

Source complete (84 files). Built from the Neon v2 plan in
`agy-brief-v2.md` (kept in the workspace, not in this repo).
