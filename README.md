<p align="center">
  <img src="docs/icon.png" alt="Pomodoro app icon" width="88">
</p>

<h1 align="center">Pomodoro</h1>

<p align="center">A native Android focus timer built with Kotlin, Jetpack Compose, and Miuix</p>

<p align="center">
  <strong>English</strong> · <a href="README.zh-CN.md">简体中文</a> ·
  <a href="https://github.com/serotonin0228/pomodoro/releases/latest">Download APK</a> ·
  <a href="supabase/README.md">Set up cloud sync</a>
</p>

## Install and update

Download the signed **arm64-v8a** APK from [GitHub Releases](https://github.com/serotonin0228/pomodoro/releases/latest). It supports ARM64 phones and tablets running Android 8.0 (API 26) or later. Android may ask you to allow the browser or file manager to install the APK.

To update an official build, open **Settings → About → Check for updates**. Android may ask you to grant Pomodoro installation permission the first time. Confirm installation when the download is complete. The Releases page keeps the current stable release.

## Focus timer

- **Countdown and stopwatch.** Set the focus duration, short and long breaks, and sessions per round. Start the next phase automatically if you prefer.
- **Background timer and notifications.** Pause, resume, or end a session from the notification shade. The running notification shows the current time and requests Android Live Updates on compatible devices. Chip and Super Island presentation depends on the device.
- **App limits.** Run an optional blocklist or allowlist during active focus sessions. Grant Android accessibility access when prompted. Opening a restricted app during focus attempts to return you to the timer.

<p align="center">
  <a href="docs/screenshots/android/timer.png"><img src="docs/screenshots/android/timer.png" alt="Countdown screen" width="228"></a>
  &nbsp;
  <a href="docs/screenshots/android/timer-zh-dark.png"><img src="docs/screenshots/android/timer-zh-dark.png" alt="Dark countdown screen in Simplified Chinese" width="228"></a>
</p>

<p align="center"><sub>These screenshots illustrate an earlier native Miuix build. Focus records and music titles use sample data.</sub></p>

## Statistics and home-screen widgets

- **Statistics.** Review focus time, activity by hour, streaks, and goals by day, week, month, or a custom date range. Filter history by day or timer mode and delete individual records.
- **Focus calendar.** Each day gets progressively darker as its focus time increases, reaching the deepest shade at 12 hours. Tap a date to review its records.
- **Three widgets.** Add the focus timer, monthly calendar, or statistics card from Android’s widget picker. The statistics card shows today’s total, this week’s total, and daily bars for the current week.

<p align="center">
  <a href="docs/screenshots/android/statistics.png"><img src="docs/screenshots/android/statistics.png" alt="Focus statistics" width="228"></a>
  &nbsp;
  <a href="docs/screenshots/android/calendar.png"><img src="docs/screenshots/android/calendar.png" alt="Focus calendar" width="228"></a>
</p>

## Cloud sync and background music

Timers and statistics work offline. To sync, sign into the same email account on both devices under **Settings → Cloud sync → Account**. Pomodoro syncs focus records, workspace settings, and timer state. A second device can show the running timer and take it over at its current position.

Open background music from the timer screen. When a library is configured, individual tracks can be downloaded for offline playback in repeat-one, sequential, or shuffle mode. The catalog is account-scoped and each device manages its own downloads.

The official APK connects to the maintainer’s Supabase deployment. For your own deployment, enable email sign-in, run the repository’s workspace schema, then build the Android APK with your project URL and publishable key. The guide covers database permissions, two-device testing, and the optional private music library. [Supabase setup guide](supabase/README.md)

<p align="center">
  <a href="docs/screenshots/android/music.png"><img src="docs/screenshots/android/music.png" alt="Background music" width="228"></a>
  &nbsp;
  <a href="docs/screenshots/android/settings.png"><img src="docs/screenshots/android/settings.png" alt="Settings" width="228"></a>
</p>

## Build and contribute

The current Android source lives in [`android/`](android/). It requires JDK 21, Gradle 9.5, and Android SDK Platform 37. The app supports English and Simplified Chinese, plus system, light, and dark themes.

```bash
cd android
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

| Topic | Guide |
| --- | --- |
| Build environment, signing, and GitHub Actions | [Android build and release guide](android/README.md) |
| Email auth, RLS, sync testing, and a private music library | [Supabase setup guide](supabase/README.md) |
| Contributions and checks | [Contributing](CONTRIBUTING.md) |
| License | [MIT](LICENSE) |

The earlier React Native and Expo source remains in the repository as a historical reference.
