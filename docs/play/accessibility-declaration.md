# Accessibility API declaration (Play Console)

Paste into **Play Console → App content → Accessibility API**. Island is **not** an accessibility tool and must not claim to be (`isAccessibilityTool="false"` in `island_accessibility.xml`).

## Which features use the Accessibility Service API?

> Island shows notifications and live activities (music, calls, downloads) as an "island" at the phone's camera cutout. Android only lets an app draw over the status bar, where the cutout is, with a `TYPE_ACCESSIBILITY_OVERLAY` window. Normal overlays (`TYPE_APPLICATION_OVERLAY`) render below the status bar and can't sit on the cutout. Island uses the AccessibilityService API for that overlay window.
>
> Island also reads the on-screen **window list**, meaning window types and bounds. It never reads window content. This tells it which app is in front, whether the status bar is hidden (full-screen video or games), and whether the notification shade is open, so the island can get out of the way. `canRetrieveWindowContent` is set only because Android requires it to list windows. Island never calls `getRootInActiveWindow()` and never reads node text or views.

## Does the app collect or share personal and sensitive data through this API?

> No. Nothing read through the API is stored or transmitted. Island has no INTERNET permission at all.

## Prominent disclosure (in the app, before the system screen)

The onboarding step "Accessibility" shows this dialog, with Accept and Decline buttons, before it opens system settings:

> Island uses the Accessibility Service API to draw the island over your status bar, at the camera cutout, which normal overlays can't do. It also lists which windows are on screen (which app is in front, whether the status bar or notification shade is showing) so the island can stay out of your way.
>
> Island does not use it to read, collect or share what's on your screen, what you type, or any personal data, and Island has no internet access. You can turn it off any time in Settings → Accessibility.

Source: `ACCESSIBILITY_DISCLOSURE` in `ui/settings/.../OnboardingScreen.kt`.

## Demo video

Required. See [demo-video-script.md](demo-video-script.md). Upload it unlisted to YouTube and paste the link into the declaration.
