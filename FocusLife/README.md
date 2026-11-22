# Focus Life — Android source project (development build)

Package: `com.focuslife.app`. Kotlin, Jetpack Compose, Material 3, Room, Hilt, WorkManager, DataStore. Minimum API 29.

## Build

1. Install Android Studio with Android SDK 35, JDK 17 and Gradle 8.9.
2. Open this directory as a Gradle project. Allow Gradle to sync dependencies.
3. Run `gradle :app:assembleDebug` or use Android Studio **Run**. No APK is included; this environment did not provide an Android SDK or Gradle executable.
4. Run `gradle :app:testDebugUnitTest` and `gradle :app:connectedDebugAndroidTest` on a device or emulator.
5. For release, create a private signing keystore and configure signing in your local Gradle environment; never commit credentials. Use `bundleRelease` for Play distribution, or `assembleRelease` for a signed APK after configuring signing.

## Permission setup

- Usage Access: Settings > Special app access > Usage access > Focus Life. Usage records are reconstructed from UsageEvents; missing access shows an empty state.
- Accessibility: Settings > Accessibility > Focus Life. Optional; service only sees foreground package changes (`canRetrieveWindowContent=false`). It displays a voluntary interruption, and the Close App button navigates Home rather than force-stopping apps.
- Notification permission: not yet wired; no notifications are sent by this version.

## Implementation status — six milestones

| Milestone | Implemented source | Incomplete / unverified |
|---|---|---|
| 1 | Compose dark/light theme, five tabs, dashboard, basic cards | Focus score, baseline, streaks, chart rendering |
| 2 | Manifest permissions, app detection, UsageEvents reconstruction, Room day snapshots, periodic worker | Cross-profile apps, OEM behavior, timezone reaggregation, device validation |
| 3 | Limit setting, threshold calculation, optional accessibility interruption | Warning notifications, extension UI, one-time extension enforcement, reliable near-real-time usage refresh |
| 4 | Basic focus sessions, pause/resume, manually logged goals, custom goals | Reboot-safe timer, completion notification, automatic timer finish, goal target editing, focus distraction count |
| 5 | Basic daily/weekly stats, sleep schedule editor | Monthly graphs, sleep usage analysis, reminders, export, weekly report |
| 6 | Pure Kotlin math tests, Room instrumented persistence test | Compilation, execution, regression tests, Play policy review, real-device testing, signed APK |

## Known engineering risks / blockers

- This is a **source-code development scaffold**, not a certified production build. It has not been compiled or run here.
- `UsageEvents` can be incomplete or truncated by Android. A one-day lookback is a best-effort boundary recovery, not a full event reconciliation engine. The screen-time union and per-app sum differ when multi-window apps overlap.
- Accessibility checks read the last stored summary, which may lag by WorkManager's minimum periodic interval. Thus limit enforcement is not accurate in real time yet.
- The accessibility overlay currently does not discriminate focus-mode messaging and may appear repeatedly after dismissal. Add debounce and explicit grace periods before release.
- Persisted focus timer uses `elapsedRealtime` and therefore needs reboot reconciliation; never claim reboot-safe sessions yet.
- The app does not implement the requested extension UI, notifications, export, weekly/monthly graphs, individual app limits, migrations beyond version 1, or full privacy controls.
- A Room schema migration must be added before increasing database version; `fallbackToDestructiveMigration` is intentionally not enabled.
- Default goal seeding must be moved to an explicit onboarding transaction to prevent recreation after deletion.
- Accessibility and usage access need a Google Play policy and data-safety review before distribution.

## Privacy

No INTERNET permission or external analytics SDK. The local database stores package names and daily aggregate foreground durations. No password, message or accessibility window content is accessed. Android's system permission settings remain under user control. Deleting app data through Android Settings also clears all locally stored Focus Life data.

## Device test checklist (NOT YET PERFORMED)

Android 10, 11, 12, 13, 14, 15: grant/revoke permissions; monitored app missing/uninstalled; reboot; timezone and date changes; midnight rollover; force-stop; multi-window; screen off; DST; timer pause/resume/restart; overlay accessibility escape; notification permissions; Room upgrade migration; battery optimization; accessibility disclosure.

**Real-device test results:** None. No physical device was accessible in this environment.

## Follow-up source improvements (2026-11-21)

- The optional accessibility interruption now refreshes usage data before checking limits and offers a one-time, database-enforced five-minute extension. It also applies a one-minute interruption cooldown. This remains **best-effort**, not a guaranteed real-time block.
- The boot receiver is exported so Android can deliver protected boot/timezone broadcasts.
- Focus sessions cannot be manually marked completed before their recorded duration has elapsed; goal targets can be edited.
- Activity return triggers a permission/usage refresh. Data deletion turns monitoring off until the user explicitly re-enables it.
- WorkManager sends at-most-once-per-day threshold notifications (40 and 50 minute stages) when the user has granted notification permission; periodic scheduling does **not** guarantee immediate delivery.

### Build and release instructions

1. Install Android Studio with JDK 17, Android SDK Platform 35, and Build Tools. Open the `FocusLife` directory.
2. Sync Gradle and run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` from the project root after installing the Gradle wrapper, or use Android Studio's Gradle tasks. The Gradle wrapper binary is not included in this archive.
3. Connect a physical device with USB debugging and run `./gradlew :app:connectedDebugAndroidTest`. Check Usage Access, accessibility interruption, notifications, midnight, reboot, time changes, and OEM battery restrictions manually.
4. For release, configure a privately held keystore in Android Studio **Build > Generate Signed App Bundle / APK**, choose a release variant, and retain the signing key securely. Never put signing credentials in source control.
5. Complete Google Play accessibility permission disclosures, privacy policy, data safety declarations, and device compatibility review before publishing.

**Build status:** The supplied environment has Java but no Android SDK or Gradle wrapper executable; Android compilation, instrumented tests, device tests, and signed APK creation have **not** been performed. Do not distribute this project as a validated release.

## Build an installable debug APK with GitHub Actions

This source archive includes `.github/workflows/android-build.yml`. Upload the extracted project to a private GitHub repository, open **Actions → Android APK build → Run workflow**, and download the **FocusLife-debug-apk** artifact after the workflow succeeds. The workflow installs Android SDK 35 and Gradle 8.9, runs JVM unit tests, and builds a debug APK. A debug APK is installable for testing but is **not** a signed Play Store release. The workflow has not been run or validated in this environment. If compilation or tests fail, fix the reported errors before distributing the APK.

For a release build, configure your own signing key securely in Android Studio (Build → Generate Signed App Bundle / APK), never commit signing secrets, and perform actual device testing and Play policy review first.
