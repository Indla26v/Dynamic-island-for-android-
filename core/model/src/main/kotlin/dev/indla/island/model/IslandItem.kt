package dev.indla.island.model

/** One thing the island can show, produced by the parser from a [RawNotification]. */
data class IslandItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val kind: ItemKind,
    /** Conversation title, sender, or notification title. */
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
) {
    /** Two posts with the same key and hash are the same content; the second is ignored. */
    val contentHash: Int = listOf(kind, title, glance, lines, actions).hashCode()

    val replyAction: IslandAction? get() = actions.firstOrNull { it.isReply }
}

enum class ItemKind {
    MESSAGE,
    GENERIC,
    /** Android 15+ redacted the content for untrusted listeners (OTPs and similar). */
    HIDDEN_BY_ANDROID,
    /** The user asked to hide this app's content while the phone is locked. */
    HIDDEN_ON_LOCKSCREEN,
}

data class MessageLine(
    val sender: String?,
    val text: String,
    val avatarKey: String? = null,
    /** How many original messages this line stands for (collapsed media runs). */
    val count: Int = 1,
)

data class IslandAction(
    val index: Int,
    val title: String,
    val isReply: Boolean,
)

enum class Priority { HIGH, NORMAL, SILENT }
