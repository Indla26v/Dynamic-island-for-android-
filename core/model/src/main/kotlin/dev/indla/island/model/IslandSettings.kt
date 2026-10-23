package dev.indla.island.model

data class IslandSettings(
    val enabled: Boolean = true,
    val appearance: IslandAppearance = IslandAppearance(),
    val whenToShow: WhenToShow = WhenToShow(),
    val gestures: GestureMap = GestureMap(),
    val autoHideSeconds: Int = 5,
    val onboardingDone: Boolean = false,
    val accessibilityDisclosureAccepted: Boolean = false,
)

data class IslandAppearance(
    val preset: StylePreset = StylePreset.CLASSIC_PILL,
    val accentArgb: Long = 0xFF8AB4F8,
    /** Multiplies every island dimension; 0.8..1.3. */
    val sizeScale: Float = 1f,
    /** Fine-tune on top of the auto-aligned cutout position. Stored in dp to survive resolution changes. */
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val showRestPill: Boolean = false,
    /** 0 = clear, 1 = tinted. */
    val glassIntensity: Float = 0.6f,
)

enum class StylePreset { CLASSIC_PILL, MINIMAL, NOTCH_ATTACHED }

/** The plain-language "When to show" card. */
data class WhenToShow(
    val hideInForegroundApp: Boolean = true,
    val hideInFullscreen: Boolean = true,
    val hideInLandscape: Boolean = true,
    val showOnLockscreen: Boolean = true,
    val hideWhenShadeOpen: Boolean = true,
)

enum class IslandGesture { TAP, SWIPE_DOWN, SWIPE_UP, SWIPE_LEFT, SWIPE_RIGHT, LONG_PRESS }

enum class GestureAction { EXPAND, OPEN_APP, HIDE, COLLAPSE, SWITCH_ITEM, QUICK_ACTIONS, NONE }

/** Fixed sensible defaults, remappable on one screen. Left and right swipes share a slot. */
data class GestureMap(
    val compactTap: GestureAction = GestureAction.EXPAND,
    val compactSwipeDown: GestureAction = GestureAction.EXPAND,
    val compactSwipeUp: GestureAction = GestureAction.HIDE,
    val compactSwipeSide: GestureAction = GestureAction.SWITCH_ITEM,
    val compactLongPress: GestureAction = GestureAction.QUICK_ACTIONS,
    val expandedTap: GestureAction = GestureAction.OPEN_APP,
    val expandedSwipeDown: GestureAction = GestureAction.NONE,
    val expandedSwipeUp: GestureAction = GestureAction.COLLAPSE,
    val expandedSwipeSide: GestureAction = GestureAction.SWITCH_ITEM,
    val expandedLongPress: GestureAction = GestureAction.QUICK_ACTIONS,
) {
    fun resolve(gesture: IslandGesture, expanded: Boolean): GestureAction = if (expanded) {
        when (gesture) {
            IslandGesture.TAP -> expandedTap
            IslandGesture.SWIPE_DOWN -> expandedSwipeDown
            IslandGesture.SWIPE_UP -> expandedSwipeUp
            IslandGesture.SWIPE_LEFT, IslandGesture.SWIPE_RIGHT -> expandedSwipeSide
            IslandGesture.LONG_PRESS -> expandedLongPress
        }
    } else {
        when (gesture) {
            IslandGesture.TAP -> compactTap
            IslandGesture.SWIPE_DOWN -> compactSwipeDown
            IslandGesture.SWIPE_UP -> compactSwipeUp
            IslandGesture.SWIPE_LEFT, IslandGesture.SWIPE_RIGHT -> compactSwipeSide
            IslandGesture.LONG_PRESS -> compactLongPress
        }
    }
}

data class AppRule(
    val packageName: String,
    val show: Boolean,
    val priority: Priority = Priority.NORMAL,
    val autoExpand: Boolean = false,
    val hideContentOnLock: Boolean = false,
    val accentArgb: Long? = null,
)

/** What the overlay service observes about the device right now. */
data class Environment(
    val foregroundPackage: String? = null,
    val fullscreen: Boolean = false,
    val landscape: Boolean = false,
    val locked: Boolean = false,
    val shadeOpen: Boolean = false,
)

object DefaultApps {
    /** Pre-selected in onboarding and used as the default rule for apps the user never configured. */
    val messaging: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "com.instagram.android",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "org.thoughtcrime.securesms",
        "com.discord",
        "com.Slack",
        "com.snapchat.android",
        "com.google.android.gm",
        "com.microsoft.teams",
        "jp.naver.line.android",
        "com.viber.voip",
        "com.kakao.talk",
    )

    fun defaultRule(packageName: String) = AppRule(packageName, show = packageName in messaging)
}
