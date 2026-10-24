# Island

A native Android island that sits on the camera cutout, animates like system UI, shows the right notification content, and takes under two minutes to set up.

This repository covers the **MVP and v1 phases** of the build plan:
- notifications with inline reply and actions;
- live activities: music (MediaSession), calls, and progress (downloads, deliveries, rides, Android 16 `ProgressStyle`);
- system glances for charging, battery, ringer and headphones;
- split states, with up to three items at once shown as bubbles;
- auto colour from app icons;
- real backdrop blur behind the expanded card;
- per-app rules, "When to show" rules, onboarding, and a health card.

Island asks for **no INTERNET permission**. The manifest strips it, along with `ACCESS_NETWORK_STATE`, so no dependency can merge either one back in.

## Build and run

You need JDK 17 or newer and the Android SDK (compileSdk 37). The project uses the Gradle wrapper, AGP 9.4 and Kotlin 2.4.

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                      # parser, engine, layout and screenshot tests
./gradlew :app:assembleRelease      # minified, signed with the upload key (see docs/play/release-checklist.md)
./gradlew :app:bundleRelease        # the .aab for Play
```

### Tester setup

1. Install the APK and open Island. The onboarding checklist updates live as each step is granted.
2. **Notification access**: lets Island read what to show.
3. **Accessibility**: lets Island draw over the status bar. Island shows the Play-required disclosure first.
   * **Sideloaded on Android 13+?** The switch is greyed out by "restricted settings". Go to *App info → ⋮ → Allow restricted settings*, then try again. The app links to App info from this step.
4. **Battery**: set it to Unrestricted. The app shows extra OEM steps for Xiaomi, Samsung, OnePlus/OPPO/realme, vivo and Huawei/Honor.
5. Use the **Try it** chips on Home (Message, Call, Download, Music) to check the whole path end to end.

For emulator or CI testing only, you can grant everything over adb:

```bash
adb shell cmd notification allow_listener dev.indla.island.debug/dev.indla.island.service.listener.IslandNotificationListener
adb shell settings put secure enabled_accessibility_services dev.indla.island.debug/dev.indla.island.service.overlay.IslandAccessibilityService
```

## Architecture

```
NotificationListenerService ──► NotificationExtractor ──► RawNotification ─┐
MediaSessionManager ──────────► MediaWatcher ──────────► MediaSnapshot ─────┤
Broadcasts / AudioDeviceCallback ► SystemEventMonitor ──► System event ─────┤
                                                                             ▼
                          IslandEngine (rules, dedupe, parser, live activities, state machine)
                                                │ StateFlow<IslandUiState>  (the only copy)
                          IslandAccessibilityService
                            ├─ blur window: a Dialog window under everything, real backdrop blur, moved once per state
                            ├─ render window: fixed size, not touchable, Compose morph on the GPU
                            └─ hit window: sized to the resting bounds, catches gestures, focusable only for reply
```

| Module | What it holds |
| --- | --- |
| `:core:model` | Plain Kotlin types: `RawNotification`, `IslandItem`, `LiveContent`, `IslandUiState`, settings, rules |
| `:engine` | `NotificationParser` and `IslandEngine`. Pure JVM, so the accuracy tests run in seconds |
| `:data` | DataStore settings, Room per-app rules, the in-memory avatar and album-art cache |
| `:service:listener` | Notification listener, `MediaWatcher`, actions (reply, answer, hang up, media controls), app colours, the "Try it" activities, the fixture recorder |
| `:service:overlay` | The accessibility service: the three windows, system events, environment detection, overlay frame stats |
| `:ui:island` | The island composable: cutout geometry, one-spring morph, compact/expanded layouts per kind, bubbles, hit layer, AGSL glass edge |
| `:ui:settings` | Glass design system and the three tabs (Home, Apps, Look & feel), plus onboarding |
| `:app` | Hilt wiring, `MainActivity` (with JankStats), manifest, baseline profile |
| `:benchmark` | Macrobenchmarks (startup, expand/collapse frame timing) and the Baseline Profile generator |

**Live activities.** Calls, playing media and progress stay on the island while they're happening:
- A new notification takes over briefly, then the island returns to the live activity.
- Updates such as a progress tick or a seek refresh the activity in place without popping.
- Paused media lingers for 10 s and then leaves.
- A live activity is parked while its own app is open, and comes back when you leave.
- Swiping up hides a live activity until it changes in a way that matters, such as a new track or the call being answered.
- With several active at once, the second shows as a bubble on the right and the third on the left.

**Smoothness.** The overlay windows are never resized per frame. One spring (damping 0.8, low-medium stiffness) animates a single shape, read only in draw and layer lambdas. Call timers and media positions tick once a second, never per frame. The blur window appears only after the card has settled.

**Accuracy rules** (covered by `engine/src/test`):
- **Dedupe:** items are keyed by notification key and content hash, and an app echoing your own reply never pops.
- **Summaries:** group summaries never pop while their children are present.
- **Silence:** ranking importance, ambient state and DND decide silence.
- **Chats:** read through MessagingStyle, with repeated media collapsed.
- **Calls:** CallStyle answer, decline and hang-up intents come from extras; older dialers' actions are matched by role.
- **Media:** media notifications are left to MediaSession, so there are no duplicates.
- **Redacted content:** Android 15+ redactions show **Hidden by Android**, never a blank pill.
- **Early posts:** notifications that arrive before your per-app rules load wait for them.

**Cutout auto-align.** Island uses `DisplayCutout.cutoutPath` when the cutout has a real shape. A tall rectangle touching the top edge is read as a round hole at its bottom; vivo reports its punch hole this way. You can drag or nudge the position in 1 dp steps, and offsets are stored in dp.

**Getting out of the way.** An overlay above the status bar receives no insets, so Island reads the window list instead (window types and bounds only, never content):
- **Full screen:** the status bar window disappears in full-screen video and games.
- **Shade:** the notification shade shows up as a SystemUI window covering most of the screen.

This works the same on any OEM skin.

## Performance and tests

| Tool | Where | How |
| --- | --- | --- |
| Engine and parser tests | `engine/src/test` | `./gradlew :engine:test` |
| Screenshot tests (Roborazzi) | `ui/island/src/test`, goldens in `ui/island/src/test/snapshots` | `./gradlew :ui:island:recordRoborazziDebug` / `verifyRoborazziDebug` |
| Macrobenchmark | `benchmark/` | `./gradlew :benchmark:connectedBenchmarkAndroidTest` (startup, expand/collapse ×10) |
| Baseline Profile | `app/src/main/baseline-prof.txt` | regenerate with `BaselineProfileGenerator` (see its KDoc) |
| JankStats | `MainActivity` | logs janky settings frames in debug builds |
| Overlay frames | `OverlayFrameStats` | Choreographer tracker that runs only while the island animates; logs to `IslandJank` |

Detection logging: `adb shell setprop log.tag.IslandOverlay DEBUG`. The debug build also has `FullscreenTestActivity`, an immersive screen for checking that the island hides.

## Release

The signing setup, Play Console steps and every form's text are in [`docs/play/`](docs/play):
- the accessibility declaration;
- the demo video script;
- the data safety answers;
- the privacy policy;
- the store listing;
- the release checklist.

## Debug tools

Debug builds add **Home → Notification fixtures**. It records the last 30 notifications' extras as JSON, in memory only, so you can export them and turn them into parser test fixtures.
