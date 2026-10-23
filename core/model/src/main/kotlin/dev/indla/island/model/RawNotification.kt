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
    val hasProgress: Boolean = false,
    /** Key into the icon store for `largeIcon`, if the listener cached one. */
    val largeIconKey: String? = null,
    /** Posted by Island's own "Send test notification" button. */
    val isSelfTest: Boolean = false,
)

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
