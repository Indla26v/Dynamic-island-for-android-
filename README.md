# Island

A native Android island that sits on the camera cutout, animates like system UI, shows the right notification content, and takes under two minutes to set up.

This repository contains the **MVP phase** of the build plan: notifications (messaging and generic) with compact and expanded states, inline reply and action buttons, auto-hide, per-app rules, cutout auto-align, "When to show" rules, a four-step onboarding checklist, and a health card.

Island asks for **no INTERNET permission**. The manifest strips it, along with `ACCESS_NETWORK_STATE`, so no dependency can merge either one back in.

## Build and run

You need JDK 17 or newer and the Android SDK (compileSdk 37). The project uses the Gradle wrapper, AGP 9.4 and Kotlin 2.4.

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                      # parser, engine and layout unit tests
./gradlew :app:assembleRelease      # minified; signed with the debug key until a release key exists
```

### Tester setup

1. Install the APK and open Island. The onboarding checklist updates live as each step is granted.
2. **Notification access**: lets Island read what to show.
3. **Accessibility**: lets Island draw over the status bar. Island shows the Play-required disclosure first.
   * **Sideloaded on Android 13+?** The switch is greyed out by "restricted settings". Go to *App info → ⋮ → Allow restricted settings*, then try again. The app links to App info from this step.
4. **Battery**: set it to Unrestricted. The app shows extra OEM steps for Xiaomi, Samsung, OnePlus/OPPO/realme, vivo and Huawei/Honor.
5. Use **Send test notification** on the Home tab to check the whole path end to end, including the inline reply.

For emulator or CI testing, you can grant everything over adb:

```bash
adb shell cmd notification allow_listener dev.indla.island.debug/dev.indla.island.service.listener.IslandNotificationListener
adb shell settings put secure enabled_accessibility_services dev.indla.island.debug/dev.indla.island.service.overlay.IslandAccessibilityService
```

## Architecture

```
NotificationListenerService ──► NotificationExtractor ──► RawNotification
                                                              │
                                     IslandEngine (rules, dedupe, parser, queue, state machine)
                                                              │ StateFlow<IslandUiState>  (the only copy)
                                     IslandAccessibilityService
                                       ├─ render window: fixed size, not touchable, Compose morph on the GPU
                                       └─ hit window: sized to the resting bounds, catches gestures, focusable only for reply
```

| Module | What it holds |
| --- | --- |
| `:core:model` | Plain Kotlin types: `RawNotification`, `IslandItem`, `IslandUiState`, settings, rules |
| `:engine` | `NotificationParser` and `IslandEngine`. Pure JVM, so the accuracy tests run in seconds |
| `:data` | DataStore settings, Room per-app rules, the in-memory avatar cache |
| `:service:listener` | The notification listener, PendingIntent/RemoteInput actions, the test notification, the fixture recorder |
| `:service:overlay` | The accessibility service with the two-window overlay, plus environment detection (foreground app, fullscreen, landscape, lock, shade) |
| `:ui:island` | The island composable: cutout geometry, one-spring shape morph, content, hit layer, AGSL glass edge |
| `:ui:settings` | Glass design system and the three tabs (Home, Apps, Look & feel), plus onboarding |
| `:app` | Hilt wiring, `MainActivity`, manifest |

**Smoothness.** The overlay window is never resized per frame. One spring (damping 0.8, low-medium stiffness) animates a single shape. Width, height and radius are read only in draw and layer lambdas, so nothing recomposes while it moves. Content fades out before a shrink and fades in about 80 ms after a grow starts. A new event mid-animation retargets the running spring and keeps its velocity.

**Accuracy rules** (all covered by `engine/src/test`):
* Each item is keyed by notification key. A re-post with the same content hash is ignored.
* Group summaries never pop while their children are present.
* A `FLAG_ONLY_ALERT_ONCE` update refreshes in place. An app echoing back the user's own inline reply never pops.
* Ranking importance, ambient state and DND decide silence. Silent notifications never pop.
* Chats are read through `NotificationCompat.MessagingStyle`. The card shows the last three lines with sender avatars, and repeated media is collapsed ("Arpit + 2 sent 5 reels").
* Android 15+ redacted content (OTPs) shows **Hidden by Android** with the app icon, never a blank pill. Detection also uses the device's own localized system string.

**Cutout auto-align.** The overlay reads the exact camera hole from `DisplayCutout.cutoutPath` on API 31+. Many devices report a punch hole's bounding rect from the top edge down, which would make the island far too tall. Centre punch holes, corner punch holes, teardrop notches and devices with no cutout each get a sensible default. You can fine-tune the position by dragging the real island, or with 1 dp nudges. Offsets are stored in dp.

## Debug tools

Debug builds add **Home → Notification fixtures**. It records the last 30 notifications' extras as JSON, in memory only, so you can export them and turn them into parser test fixtures.

## Status against the plan

Done in the MVP:
* Everything in the MVP row of the plan.
* Glass design: Haze blur on the floating tab bar, an AGSL edge highlight on Android 13+, a Glass intensity slider, and support for the system's reduce-transparency setting.
* Remappable gestures on one screen.

Not done yet:
* **v1:** media (MediaSession), calls, progress and `ProgressStyle` notifications, battery/charging/ringer/Bluetooth events, auto colour from the app icon. The parser currently skips these categories on purpose.
* **Performance tooling:** Baseline Profiles, Macrobenchmark, JankStats and Roborazzi screenshot tests. These need a device or CI runner.
* **Expanded card blur:** cross-window blur isn't available to `WindowManager`-added accessibility overlays, so the expanded card uses the plan's frosted-tint fallback.
* **Fullscreen and shade detection** use window insets and accessibility window-state events. Both are best effort and need checking on Samsung and Xiaomi devices.
* A release keystore and the Play declaration/demo video.
