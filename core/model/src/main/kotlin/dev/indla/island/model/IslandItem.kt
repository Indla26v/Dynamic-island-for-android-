package dev.indla.island.model

/** One thing the island can show: a notification, or a live activity (media, call, progress, event). */
data class IslandItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val kind: ItemKind,
    /** Conversation title, sender, track, caller, or notification title. */
    val title: String,
    /** Short text on the right of the compact pill ("2 new", "Arpit"). */
    val glance: String,
    /** At most three lines for the expanded card, oldest first. */
    val lines: List<MessageLine>,
    val actions: List<IslandAction>,
    /** Icon store key for the compact avatar; the app icon is the fallback. */
    val avatarKey: String?,
    val postTime: Long,
    val groupKey: String?,
    val isGroupSummary: Boolean,
    val priority: Priority = Priority.NORMAL,
    val accentArgb: Long? = null,
    val hideContentOnLock: Boolean = false,
    /** Set for live activities; drives their compact and expanded layouts. */
    val live: LiveContent? = null,
    /** Set when this item is one page of several sharing the island (not part of [contentHash]). */
    val stack: StackInfo? = null,
) {
    /**
     * Two posts with the same key and hash are the same content; the second is ignored. Live
     * content contributes only its stable part, so a progress tick or seek position isn't "new".
     */
    val contentHash: Int = listOf(kind, title, glance, lines, actions, live?.stableKey).hashCode()

    val replyAction: IslandAction? get() = actions.firstOrNull { it.isReply }

    val isLive: Boolean get() = live != null
}

/**
 * Several notifications in one island slot: one app's notifications, or the missed pile (what
 * came and went while nobody looked). Pages run oldest to newest; a swipe moves between them.
 */
data class StackInfo(
    val size: Int,
    /** The page shown, 0 = oldest. */
    val index: Int,
    val missed: Boolean = false,
    /** Other apps in the pile, newest first, at most three: drawn as overlapping icons. */
    val otherPackages: List<String> = emptyList(),
)

enum class ItemKind {
    MESSAGE,
    GENERIC,
    /** Android 15+ redacted the content for untrusted listeners (OTPs and similar). */
    HIDDEN_BY_ANDROID,
    /** The user asked to hide this app's content while the phone is locked. */
    HIDDEN_ON_LOCKSCREEN,
    MEDIA,
    CALL,
    PROGRESS,
    SYSTEM,
    TIMER,
}

data class MessageLine(
    val sender: String?,
    val text: String,
    val avatarKey: String? = null,
    /** How many original messages this line stands for (collapsed media runs). */
    val count: Int = 1,
)

data class IslandAction(
    /** Index into `Notification.actions`; negative for [ActionRole]s carried in extras (calls). */
    val index: Int,
    val title: String,
    val isReply: Boolean,
    val role: ActionRole = if (isReply) ActionRole.REPLY else ActionRole.NORMAL,
)

enum class ActionRole {
    NORMAL,
    REPLY,
    ANSWER,
    DECLINE,
    HANG_UP,
    /** Timer, stopwatch and recorder controls: one play/pause toggle and a stop button. */
    PAUSE,
    RESUME,
    STOP;

    companion object {
        /** Synthetic action indices for CallStyle intents that live in extras, not in `actions`. */
        const val ANSWER_INDEX = -1
        const val DECLINE_INDEX = -2
        const val HANG_UP_INDEX = -3
        /** Play/pause and stop buttons drawn inside an app's custom notification view. */
        const val CUSTOM_TOGGLE_INDEX = -4
        const val CUSTOM_STOP_INDEX = -5
    }
}

enum class Priority { HIGH, NORMAL, SILENT }

/** Live activities: content that stays on the island while it's happening. */
sealed interface LiveContent {
    /** What must change for an update to count as a new event (and pop the island again). */
    val stableKey: Any

    data class Media(
        val track: String,
        val artist: String?,
        val playing: Boolean,
        val durationMs: Long,
        /** Position at [positionAtElapsedMs] (SystemClock.elapsedRealtime), advancing at [speed]. */
        val positionMs: Long,
        val positionAtElapsedMs: Long,
        val speed: Float,
        val artKey: String?,
        val canPrevious: Boolean,
        val canNext: Boolean,
        val canSeek: Boolean,
    ) : LiveContent {
        override val stableKey get() = listOf(track, artist, playing)
    }

    data class Call(
        val caller: String,
        val state: CallState,
        /** Wall-clock time the call started (chronometer base), for the running timer. */
        val startedAtMs: Long,
    ) : LiveContent {
        override val stableKey get() = listOf(caller, state)
    }

    data class Progress(
        /** 0..1, or null while indeterminate. */
        val fraction: Float?,
        /** "Arriving in 12 min", "45%", "Downloading"; the road, for navigation. */
        val status: String,
        /** Turn-by-turn directions: the title is the distance, the large icon the turn. */
        val navigation: Boolean = false,
        /** "9 min · 3.8 km · 16:41 ETA". */
        val eta: String? = null,
    ) : LiveContent {
        override val stableKey get() = status.isNotEmpty()
    }

    /** A Clock timer or stopwatch. */
    data class Timer(
        /** "Timer", "Stopwatch", or the timer's own label. */
        val label: String,
        /** Chronometer base: when a countdown ends, or when a stopwatch started. Null when paused. */
        val baseMs: Long?,
        val countDown: Boolean,
        /** What the app shows while paused ("14:04"). */
        val staticTime: String,
        /** A voice or screen recording: counts up, drawn as a live waveform. */
        val recording: Boolean = false,
    ) : LiveContent {
        override val stableKey get() = listOf(label, baseMs == null)
    }

    data class System(
        val event: SystemEvent,
        /** Battery percent: the phone's for charging events, the pair's for headphones. */
        val level: Int?,
        /** What to say: a percentage, a ringer mode, or the headphones' name. */
        val label: String,
        /** Headphones that are earbuds (drawn as earbuds, not a headband). */
        val earbuds: Boolean = false,
    ) : LiveContent {
        // A battery report arriving after the connection updates the card; it isn't a new event.
        override val stableKey get() = if (event == SystemEvent.HEADPHONES_CONNECTED) listOf(event, label) else listOf(event, level, label)
    }
}

enum class CallState { INCOMING, ONGOING, SCREENING }

enum class SystemEvent {
    CHARGING,
    UNPLUGGED,
    BATTERY_LOW,
    RINGER_NORMAL,
    RINGER_VIBRATE,
    RINGER_SILENT,
    HEADPHONES_CONNECTED,
    HEADPHONES_DISCONNECTED,
}

/** A media session as the listener sees it; the engine turns it into a MEDIA item. */
data class MediaSnapshot(
    val packageName: String,
    val appLabel: String,
    val media: LiveContent.Media,
    val appAccentArgb: Long? = null,
    /** Island's own "Try music" session: exempt from the foreground rule. */
    val isSelfTest: Boolean = false,
)

sealed interface MediaCommand {
    data object PlayPause : MediaCommand
    data object Next : MediaCommand
    data object Previous : MediaCommand
    data class SeekTo(val positionMs: Long) : MediaCommand
}
