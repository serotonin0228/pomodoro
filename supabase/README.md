# Connect your own Supabase project

[简体中文](README.zh-CN.md) · [Project README](../README.md)

The official APK is built for the maintainer’s Supabase project. Build your own APK to use a different project. The timer, statistics, and widgets work offline. Cloud sync and a private music library are optional.

## 1. Create a project and enable email sign-in

1. Create a project in the [Supabase dashboard](https://supabase.com/dashboard). Get its **Project URL** and **publishable key** from **Connect** or the project API settings. Legacy `anon` keys also work.
2. Enable the **Email** provider under **Authentication → Sign In / Providers**. For public registration, keep email confirmation enabled and configure [custom SMTP](https://supabase.com/docs/guides/auth/auth-smtp). For a private deployment, create a confirmed user under **Authentication → Users** and disable public sign-up if desired.
3. With confirmation enabled, open the verification link in a browser, then return to the app and sign in with email and password. Set the Supabase Auth Site URL to an HTTPS page you control.

The APK uses a public client key. Keep database passwords, `service_role` and `sb_secret_` keys, account tokens, and signing material in a controlled environment.

## 2. Install the workspace schema

1. Run all of [`schema.sql`](schema.sql) once in the new project’s **SQL Editor**. This creates `public.workspaces`, the `save_workspace` and `server_time` RPCs, and their role grants and row-level security policies. The script expects Supabase’s `auth.users` and `supabase_realtime` objects.
2. Confirm that the **Data API** is enabled and exposes `public`. The script explicitly grants the required privileges to `authenticated`. Row-level policies restrict each account to its own workspace. On an existing deployment, inspect the installed objects and apply only missing changes.
3. On a development or staging project, run [`verify.sql`](verify.sql). The script reports `PASS` after checking creation, updates, stale revisions, and isolation between accounts. Its test rows are rolled back.

The native client authenticates through Supabase Auth and polls the Data API over REST. A write supplies the previous revision to `save_workspace`; the client fetches and merges again on a conflict. The server clock helps align the timer across devices. The schema retains a Realtime publication for compatibility with earlier clients.

See [API keys](https://supabase.com/docs/guides/api/api-keys), [Data API grants](https://supabase.com/docs/guides/api/securing-your-api), and [row-level security](https://supabase.com/docs/guides/database/postgres/row-level-security).

## 3. Build and connect two devices

Run the following from the repository root with values from your project.

```bash
export ORG_GRADLE_PROJECT_SUPABASE_URL='https://YOUR_PROJECT_REF.supabase.co'
export ORG_GRADLE_PROJECT_SUPABASE_ANON_KEY='sb_publishable_YOUR_KEY'
cd android
gradle :app:assembleDebug
```

The APK is at `android/app/build/outputs/apk/debug/app-debug.apk`. These settings are compiled into the APK. Use a separate test device or emulator for packages signed with a different key from the official APK. See the [Android build guide](../android/README.md) for release signing.

1. Install builds targeting the same project on both devices and sign into the same account under **Settings → Cloud sync → Account**.
2. Start a stopwatch on device A. When device B syncs, open its timer screen and choose **Take over here**. It should continue from the current elapsed time.
3. Finish and save the session on B. Check that its record appears on A. Repeat with B offline and reconnect it to check record merging.
4. Sign in with a different account to verify that history is isolated. Guest history and signed-in users have separate on-device workspaces.

Cloud sync covers focus records, workspace settings, and the timer state. App restriction lists, system accessibility permission, and downloaded MP3s are local. Network delay can affect when a timer first appears on a second device.

## 4. Optional private music library

Run [`music-catalog.sql`](music-catalog.sql), [`music-audio.sql`](music-audio.sql), and [`music-audio-version.sql`](music-audio-version.sql) in that order, once each. The catalog is stored in `public.music_tracks`, and MP3 data in `private.music_audio`, which stays outside the exposed Data API schemas. The app downloads through the account-scoped `download_music` RPC. The catalog’s `storage_path` is a retained metadata field. This setup uses database audio and requires no Storage bucket.

Each track can contain up to 30,000,000 bytes. Use the [worked administrator import example](README.zh-CN.md) to add an MP3 from your local machine with `psql`. Use the owner’s Supabase Auth user UUID. To replace a track, update its private audio row. A trigger recalculates the size, hashes, and audio revision so devices can refresh the download. Set `enabled` to `false` to hide a track. With a test track populated, [`verify-music.sql`](verify-music.sql) checks access, downloads, and revision updates.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Cloud sync is unavailable | Confirm both Gradle environment variables were present when this APK was built. |
| No confirmation email | Check the Email provider, SMTP, and confirmation settings. The built-in mail service is restricted. |
| Signed in with no sync | Confirm the same project and email on both devices, then check Data API exposure and the table grants and RPCs using `verify.sql` on a test project. |
| Empty music library | Check that the catalog’s owner UUID matches the signed-in account and that the track is enabled. |
| A visible track fails to download | Confirm the private audio row, matching track and owner IDs, and the music revision script. |
