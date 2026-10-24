# Release checklist

## Signing

- The upload key is at `C:/Users/venka/keys/island-upload.jks` (alias `island-upload`). Its passwords are in `keystore.properties` at the project root, and both files are git-ignored. **Back up both files somewhere safe and off this PC.** If the upload key is lost, Play App Signing can reset it, but only after a support request.
- Enrol in **Play App Signing** when creating the app. Google keeps the app signing key, and this key only signs uploads.
- Without `keystore.properties`, release builds fall back to the debug key so they still install for testing. Play rejects those uploads.

## Build

```bash
./gradlew test :ui:island:verifyRoborazziDebug
./gradlew :app:bundleRelease      # app/build/outputs/bundle/release/app-release.aab
./gradlew :app:assembleRelease    # sideload / GitHub release APK
```

Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every upload.

## Play Console, first time

1. Create the app (name **Island**, not "Dynamic Island", with no Apple imagery or SF Pro).
2. Store listing: text from [store-listing.md](store-listing.md). Screenshots go through the "Try it" chips.
3. App content:
   - Privacy policy URL: host [privacy-policy.md](privacy-policy.md) (for example GitHub Pages) and paste the URL.
   - Data safety: [data-safety.md](data-safety.md).
   - Accessibility API declaration and demo video: [accessibility-declaration.md](accessibility-declaration.md) and [demo-video-script.md](demo-video-script.md).
   - Content rating questionnaire, target audience 18+, no ads.
4. New personal accounts need a **closed test with at least 12 testers opted in for 14 continuous days** before production. Invite 15 or more in case some drop out. Start this as soon as possible; v1 can keep improving while the clock runs.
5. Tester instructions: sideloaded APKs on Android 13+ need *App info → ⋮ → Allow restricted settings* before Accessibility can be turned on. Play installs don't.

## GitHub release

Attach `app-release.apk` (signed with the upload key) to a GitHub release, as the plan suggests, in case Play review takes time.
