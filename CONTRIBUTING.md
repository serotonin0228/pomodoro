# Contributing to Pomodoro

Open an issue for substantial changes. Keep pull requests focused and describe the user-visible behavior, the expected test result, and the devices or Android versions tested.

## Native Android development

The current app lives in [`android/`](android/). Follow the [Android build guide](android/README.md) to set up JDK 21, the JDK 17 toolchain, Gradle 9.5.0, and Android SDK Platform 37.

```bash
cd android
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Use the existing Miuix components and localization model for app screens. For visual changes, include screenshots or a short recording when practical. Test background audio, notifications, accessibility restrictions, or widget host behavior on a relevant device when touching those areas.

For changes to cloud sync, use a development Supabase project and follow the [Supabase guide](supabase/README.md). Check the workspace policy with the supplied verification SQL and test two-device behavior when modifying timer ownership or record merging.

## Historical code

The repository root also contains the older React Native and Expo implementation. Its Node tooling applies to changes in that historical code. Work on the current Android app should be made in the Kotlin project.

## Credentials and licensing

Keep signing material, local credentials, account tokens, privileged Supabase keys, and personal data out of commits and issues. Cloud-connected APKs use a publishable key with database access controlled by RLS.

By contributing, you agree to distribute the contribution under the [MIT License](LICENSE). Use compatible licenses for any third-party assets and include the appropriate attribution.
