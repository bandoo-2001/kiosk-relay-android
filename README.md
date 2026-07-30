# KioskRelay for Android

English | [简体中文](./README.zh-CN.md)

KioskRelay is an open-source, dynamically brandable Android WebView kiosk for
industrial tablets, digital signage, phones, and basic Android TV deployments.
The `v0.4.0` standard-mode MVP is implemented as a buildable single-module
Android application.

## Current status

- Application ID: `io.github.kioskrelay`
- Version: `0.4.0`
- Minimum Android version: Android 7.0 / API 24
- Target SDK: 36; compile SDK: 36.1
- Toolchain: JDK 17, Gradle 9.4.1, AGP 9.2.1
- UI/data/runtime: Jetpack Compose, Proto DataStore, AndroidX WebKit
- Languages: system default, Simplified Chinese, and English

The project currently passes JVM tests, Android Lint, Debug and minified Release
builds, and Android instrumentation-test APK compilation. API 24 code/build
compatibility is verified, but Android 7 installation and runtime validation
still require an API 24 emulator or a physical Android 7 device.

## Implemented MVP

- Four-step first-run setup with language, branding, URL/display, and
  administrator/security configuration
- Dynamic product name, colors, logo, splash background, and status messages
- HTTPS-first WebView with explicit HTTP risk confirmation and exact-Origin
  navigation allowlists
- JavaScript, DOM Storage, and first-party cookies enabled; mixed content,
  third-party cookies, downloads, uploads, popups, file/content access,
  JavaScript interfaces, location, camera, and microphone disabled
- SSL errors always rejected; WebView debugging enabled only in Debug builds
- Offline state, bounded `5/10/20/40/60/60` retry backoff, network recovery, and
  API 26+ renderer recreation
- Immersive fullscreen, screen wake lock, orientation selection, and protected
  Back navigation
- Hidden administrator entry: five top-left taps within three seconds, or the
  TV sequence `Up Up Down Down Left Right Left Right OK`
- Five settings groups, password change, reload, cache/Cookie/site-data
  clearing, reset, and diagnostics export
- Versioned `.kioskrelay` ZIP import/export with size, schema, image, and path
  traversal checks; credentials, cookies, cache, and diagnostics are excluded
- Best-effort boot handling: direct launch on API 24–28 and a notification
  fallback on API 29+
- Android TV launcher declaration, D-pad-friendly controls, adaptive icons,
  Android 7 density icons, and a 320×180 TV banner

The customer dashboard shown in the visual reference is not implemented in this
repository; it is content loaded by the configured WebView.

## Build

Install JDK 17 and Android SDK 36.1, then run:

```bash
./gradlew test lint assembleDebug assembleRelease assembleDebugAndroidTest
```

Generated APKs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

The Release APK is unsigned and must be signed with the deployment key before
distribution.

## Android compatibility notes

- API 24–25 cannot receive `WebViewClient.onRenderProcessGone`; persisted
  configuration is restored after the process or Activity is restarted.
- API 26+ recreates a terminated WebView renderer and stops automatic recovery
  after repeated renderer failures.
- API 29+ restricts background Activity launches. Standard mode posts a
  best-effort notification or resumes on the next user launch; it does not
  promise that the kiosk will appear automatically after boot.
- Android 7 should be used only with trusted intranet pages and a controlled
  WebView package. Public unattended deployments should use a newer Android
  release.
- Runtime acceptance is still required on API 24, 25, 26, 29, 31, and 35/36,
  including WebView 119 and the current WebView release.

## Scope

This release does not include Device Owner provisioning, default Launcher mode,
Lock Task, cloud management, remote upgrades, or multi-page rotation. Those
capabilities remain outside the standard-mode MVP.

## Documentation

- [Product plan and implementation status (Simplified Chinese)](./docs/KioskRelay-产品规划.md)
- [Visual asset notes](./docs/assets/README.md)

## License

KioskRelay is released under the [MIT License](./LICENSE).
