package dev.indla.island.model

/**
 * A notification flattened out of `StatusBarNotification` + `RankingMap` by the listener.
 * Plain data, so the parser and engine can be tested on the JVM with fixtures.
 */
data class RawNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val postTime: Long,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
    /** Another notification in the same group is still posted (so a summary must not pop). */
    val groupHasChildren: Boolean = false,
    val onlyAlertOnce: Boolean = false,
    val ongoing: Boolean = false,
    val category: String? = null,
    val channelId: String? = null,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val conversationTitle: String? = null,
    val isGroupConversation: Boolean = false,
    /** From `NotificationCompat.MessagingStyle`, oldest first. Empty for non-chat notifications. */
    val messages: List<RawMessage> = emptyList(),
    val actions: List<RawAction> = emptyList(),
    val ranking: RawRanking = RawRanking(),
    /** `android.template`, e.g. `android.app.Notification$CallStyle`. */
    val template: String? = null,
    /** EXTRA_PROGRESS / EXTRA_PROGRESS_MAX / EXTRA_PROGRESS_INDETERMINATE (ProgressStyle sets them too). */
    val progress: Int = 0,
    val progressMax: Int = 0,
    val progressIndeterminate: Boolean = false,
    /** CallStyle: EXTRA_CALL_TYPE (1 incoming, 2 ongoing, 3 screening), 0 when absent. */
    val callType: Int = 0,
    /** CallStyle answer/decline/hang-up intents present in extras. */
    val callCanAnswer: Boolean = false,
    val callCanDecline: Boolean = false,
    val callCanHangUp: Boolean = false,
    /** Chronometer base (`Notification.when`) when the notification shows a running timer. */
    val chronometerBase: Long? = null,
    /** EXTRA_CHRONOMETER_COUNT_DOWN: the chronometer counts down to [chronometerBase]. */
    val chronometerCountDown: Boolean = false,
    /** Key into the icon store for `largeIcon`, if the listener cached one. */
    val largeIconKey: String? = null,
    /** A vivid colour picked from the app icon, for "Colour from app icon". */
    val appAccentArgb: Long? = null,
    /** Posted by Island's own "Send test notification" button. */
    val isSelfTest: Boolean = false,
) {
    val hasProgress: Boolean get() = progressMax > 0 || progressIndeterminate
}

data class RawMessage(
    val text: String?,
    val timestamp: Long,
    /** Null means the message was sent by the device user ("You"), as in MessagingStyle. */
    val sender: RawPerson?,
    val dataMimeType: String? = null,
)

data class RawPerson(
    val name: String?,
    val key: String? = null,
    val iconKey: String? = null,
)

data class RawAction(
    val index: Int,
    val title: String,
    val hasRemoteInput: Boolean = false,
    /** System-generated smart actions; not shown in the island. */
    val isContextual: Boolean = false,
)

data class RawRanking(
    val importance: Int = Importance.DEFAULT,
    val isAmbient: Boolean = false,
    val matchesInterruptionFilter: Boolean = true,
    val isSuspended: Boolean = false,
)

/** Mirrors `NotificationManager.IMPORTANCE_*` so the engine needs no Android imports. */
object Importance {
    const val UNSPECIFIED = -1000
    const val NONE = 0
    const val MIN = 1
    const val LOW = 2
    const val DEFAULT = 3
    const val HIGH = 4
}
