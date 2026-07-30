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
- Toolchain: Gradle 9.5.0 and AGP 9.3.1; the wrapper can start on JDK 17,
  while the Gradle daemon is pinned to Java 21
- UI/data/runtime: Jetpack Compose, Proto DataStore, AndroidX WebKit
- Languages: system default, Simplified Chinese, and English

The project currently passes 58 JVM tests, Android Lint, Debug and minified
Release builds, and 13 connected instrumentation tests on an Android 7.0 /
API 24 AVD. The Debug APK was strictly exercised with
`http://192.168.1.11:5173/`, including onboarding/branding, selected security
paths, network recovery with recovery reload enabled, boot launch enabled with
zero delay, core configuration-archive paths, and selected maintenance and
diagnostic actions. The target page body and eight checked business requests
ultimately loaded with Chrome/WebView 119. The AVD's system WebView 53 loaded
the main document but could not parse the Vite 8 client
(`SyntaxError: Unexpected token .`), resulting in a blank page.

This is API 24 Debug core-flow validation, not full production acceptance. A
full connected instrumentation run passed 13/13 with no skipped or failed
tests, but strict runtime testing reproduced three release blockers: disabling
reload-on-network-recovery exposes a failed Chromium page as Online, immersive
system bars are not restored after leaving Settings or using maintenance
reload, and a failed HTTPS subresource certificate incorrectly places the
entire rendered dashboard in Fatal. Source review also found a high-risk
fail-open path for corrupted administrator credentials. Signed Release
installation, physical hardware testing, and the remaining Android-version
matrix are also still required.

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
- The onboarding administrator password is optional. Without one, the hidden
  entry opens settings directly, where a password can be added later.
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

Install Android SDK 36.1 and Java 21. Alternatively, start the wrapper with
JDK 17 and allow Gradle to provision Java 21 according to
`gradle/gradle-daemon-jvm.properties`. Then run:

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
- On the API 24 AVD, first-run setup, HTTP confirmation, DataStore process
  restart, protected Back behavior, touch/D-pad administrator entry,
  initial-entry fullscreen/landscape/keep-awake behavior, the first two retry
  delays, real network disconnect/recovery with recovery reload enabled,
  rejection of a self-signed certificate, blocking of cross-Origin and
  `intent://` navigation, and `BOOT_COMPLETED` launch were exercised
  successfully.
- With WebView 119, the page and the eight data requests checked during this
  run ultimately returned HTTP 200. The backend on port 8071 was intermittently
  unavailable during testing and Vite temporarily returned HTTP 502; business
  data correctness and service SLA are outside Android client acceptance.
- The target page's Open-Meteo HTTPS weather request encountered a certificate
  failure on API 24/WebView 119. Cancelling the resource is correct, but the App
  currently escalates that subresource error into a full-page Fatal overlay.
- See the strict test report for known defects and coverage boundaries. In
  particular, synthetic instrumentation callbacks do not prove real HTTP 302,
  renderer-crash, or target-hardware behavior.
- API 29+ restricts background Activity launches. Standard mode posts a
  best-effort notification or resumes on the next user launch; it does not
  promise that the kiosk will appear automatically after boot.
- Android 7 should be used only with trusted intranet pages and a controlled
  WebView package. Public unattended deployments should use a newer Android
  release.
- API 25, 26, 29, 31, and 35/36 runtime acceptance remains outstanding. The API
  24 run covered rejection of a self-signed TLS certificate and direct
  cross-Origin navigation, but not real HTTP 302 redirect chains.

## Scope

This release does not include Device Owner provisioning, default Launcher mode,
Lock Task, cloud management, remote upgrades, or multi-page rotation. Those
capabilities remain outside the standard-mode MVP.

## Documentation

- [Product plan and implementation status (Simplified Chinese)](./docs/KioskRelay-产品规划.md)
- [Strict functional and scenario test report (Simplified Chinese)](./docs/KioskRelay-严格功能与场景测试报告-20260730.md)
- [Android 7.0 / API 24 validation report (Simplified Chinese)](./docs/Android-7-API24-验证报告-20260730.md)
- [Visual asset notes](./docs/assets/README.md)

## License

KioskRelay is released under the [MIT License](./LICENSE).
