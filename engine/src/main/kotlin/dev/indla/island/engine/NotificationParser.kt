package dev.indla.island.engine

import dev.indla.island.model.AppRule
import dev.indla.island.model.IslandAction
import dev.indla.island.model.IslandItem
import dev.indla.island.model.ItemKind
import dev.indla.island.model.MessageLine
import dev.indla.island.model.RawMessage
import dev.indla.island.model.RawNotification

sealed interface ParseResult {
    data class Show(val item: IslandItem) : ParseResult
    data class Skip(val reason: SkipReason) : ParseResult
}

enum class SkipReason { ONGOING, LATER_PHASE_CATEGORY, PROGRESS, EMPTY }

/**
 * Turns a [RawNotification] into an [IslandItem]. Chats are read from MessagingStyle (sender,
 * timestamp, avatar), never by joining text lines, and repeated media is collapsed.
 *
 * @param redactedMarkers texts Android substitutes when it hides content from untrusted
 *   listeners (Android 15+). The listener adds the device's localized system string.
 */
class NotificationParser(
    redactedMarkers: Set<String> = DEFAULT_REDACTED_MARKERS,
) {
    private val redactedMarkers = redactedMarkers.map { it.trim().lowercase() }.toSet()

    fun parse(raw: RawNotification, rule: AppRule): ParseResult {
        if (!raw.isSelfTest) {
            // Calls, media, timers and navigation are live activities: v1 scope, not MVP.
            if (raw.category in LATER_PHASE_CATEGORIES) return ParseResult.Skip(SkipReason.LATER_PHASE_CATEGORY)
            if (raw.ongoing) return ParseResult.Skip(SkipReason.ONGOING)
            if (raw.hasProgress) return ParseResult.Skip(SkipReason.PROGRESS)
        }

        val item = when {
            isRedacted(raw) -> hiddenItem(raw)
            raw.messages.isNotEmpty() -> messagingItem(raw)
            else -> genericItem(raw) ?: return ParseResult.Skip(SkipReason.EMPTY)
        }
        return ParseResult.Show(
            item.copy(
                priority = rule.priority,
                accentArgb = rule.accentArgb,
                hideContentOnLock = rule.hideContentOnLock,
            ),
        )
    }

    fun isRedacted(raw: RawNotification): Boolean {
        val texts = buildList {
            add(raw.text)
            add(raw.bigText)
            raw.messages.forEach { add(it.text) }
        }
        return texts.any { t ->
            val n = t?.trim()?.lowercase() ?: return@any false
            n in redactedMarkers || CONTENT_HIDDEN.containsMatchIn(n)
        }
    }

    private fun hiddenItem(raw: RawNotification) = baseItem(raw).copy(
        kind = ItemKind.HIDDEN_BY_ANDROID,
        title = raw.appLabel,
        glance = HIDDEN_BY_ANDROID,
        lines = listOf(MessageLine(sender = null, text = HIDDEN_BY_ANDROID)),
        avatarKey = null,
        // Opening the app still works; replying to text you can't see makes no sense.
        actions = emptyList(),
    )

    private fun messagingItem(raw: RawNotification): IslandItem {
        val messages = raw.messages.sortedBy { it.timestamp }
        val incoming = messages.filter { it.sender != null }
        val lastFromUser = messages.indexOfLast { it.sender == null }
        val unread = messages.drop(lastFromUser + 1).count { it.sender != null }.coerceAtLeast(1)
        val lastIncoming = incoming.lastOrNull()

        val title = raw.conversationTitle?.takeIf { it.isNotBlank() }
            ?: lastIncoming?.sender?.name?.takeIf { it.isNotBlank() }
            ?: raw.title?.takeIf { it.isNotBlank() }
            ?: raw.appLabel

        val glance = if (unread > 1) "$unread new" else firstName(lastIncoming?.sender?.name ?: title)

        return baseItem(raw).copy(
            kind = ItemKind.MESSAGE,
            title = title,
            glance = glance,
            lines = collapseMedia(messages).takeLast(MAX_LINES),
            avatarKey = lastIncoming?.sender?.iconKey ?: raw.largeIconKey,
        )
    }

    private fun genericItem(raw: RawNotification): IslandItem? {
        val title = raw.title?.trim().orEmpty()
        val body = (raw.bigText ?: raw.text)?.trim().orEmpty()
        if (title.isEmpty() && body.isEmpty()) return null
        return baseItem(raw).copy(
            kind = ItemKind.GENERIC,
            title = title.ifEmpty { raw.appLabel },
            glance = title.ifEmpty { body },
            lines = if (body.isEmpty()) emptyList() else listOf(MessageLine(sender = null, text = body)),
            avatarKey = raw.largeIconKey,
        )
    }

    private fun baseItem(raw: RawNotification) = IslandItem(
        key = raw.key,
        packageName = raw.packageName,
        appLabel = raw.appLabel,
        kind = ItemKind.GENERIC,
        title = "",
        glance = "",
        lines = emptyList(),
        actions = raw.actions
            .filter { !it.isContextual && it.title.isNotBlank() }
            .take(MAX_ACTIONS)
            .map { IslandAction(it.index, it.title.trim(), isReply = it.hasRemoteInput) },
        avatarKey = null,
        postTime = raw.postTime,
        groupKey = raw.groupKey,
        isGroupSummary = raw.isGroupSummary,
    )

    /**
     * Consecutive media messages of the same kind become one line: "Arpit + 2" / "sent 3 reels".
     * Text messages are kept as they are.
     */
    internal fun collapseMedia(messages: List<RawMessage>): List<MessageLine> {
        val out = mutableListOf<MessageLine>()
        var run = mutableListOf<RawMessage>()
        var runKind: MediaKind? = null

        fun flush() {
            if (run.isEmpty()) return
            val kind = runKind
            if (kind == null || run.size == 1) {
                run.forEach { out += plainLine(it) }
            } else {
                val senders = run.map { senderName(it) }.distinct()
                val who = firstName(senders.first()) + if (senders.size > 1) " + ${senders.size - 1}" else ""
                out += MessageLine(
                    sender = who,
                    text = "sent ${run.size} ${kind.plural}",
                    avatarKey = run.first().sender?.iconKey,
                    count = run.size,
                )
            }
            run = mutableListOf()
            runKind = null
        }

        for (m in messages) {
            val kind = mediaKind(m)
            if (kind == null || kind != runKind) {
                flush()
            }
            run += m
            runKind = kind
            if (kind == null) flush()
        }
        flush()
        return out
    }

    private fun plainLine(m: RawMessage) = MessageLine(
        sender = senderName(m),
        text = m.text?.trim()?.takeIf { it.isNotEmpty() } ?: mediaKind(m)?.let { "sent ${it.singular}" } ?: "",
        avatarKey = m.sender?.iconKey,
    )

    private fun senderName(m: RawMessage): String = m.sender?.name?.takeIf { it.isNotBlank() } ?: YOU

    internal fun mediaKind(m: RawMessage): MediaKind? {
        m.dataMimeType?.let { mime ->
            when {
                mime.startsWith("image/gif") -> return MediaKind.GIF
                mime.startsWith("image/") -> return MediaKind.PHOTO
                mime.startsWith("video/") -> return MediaKind.VIDEO
                mime.startsWith("audio/") -> return MediaKind.VOICE
            }
        }
        val normalized = m.text
            ?.lowercase()
            ?.replace(LEADING_SYMBOLS, "")
            ?.trim()
            ?.trimEnd('.', '!')
            ?: return null
        val match = MEDIA_TEXT.matchEntire(normalized) ?: return null
        return MediaKind.fromWord(match.groupValues[1])
    }

    enum class MediaKind(val singular: String, val plural: String) {
        REEL("a reel", "reels"),
        PHOTO("a photo", "photos"),
        VIDEO("a video", "videos"),
        VOICE("a voice message", "voice messages"),
        STICKER("a sticker", "stickers"),
        GIF("a GIF", "GIFs"),
        ATTACHMENT("an attachment", "attachments"),
        POST("a post", "posts");

        companion object {
            fun fromWord(word: String): MediaKind? = when (word) {
                "reel" -> REEL
                "photo", "image", "picture" -> PHOTO
                "video" -> VIDEO
                "voice message", "audio", "voice note" -> VOICE
                "sticker" -> STICKER
                "gif" -> GIF
                "attachment", "file" -> ATTACHMENT
                "post" -> POST
                else -> null
            }
        }
    }

    companion object {
        const val MAX_LINES = 3
        const val MAX_ACTIONS = 3
        const val HIDDEN_BY_ANDROID = "Hidden by Android"
        const val YOU = "You"

        val DEFAULT_REDACTED_MARKERS = setOf(
            "Sensitive notification content hidden",
            "Content hidden",
            "Contents hidden",
        )

        private val CONTENT_HIDDEN = Regex("""\bcontents? hidden\b""")

        val LATER_PHASE_CATEGORIES = setOf(
            "call", "transport", "progress", "service", "stopwatch", "navigation", "alarm", "sys",
        )

        private val LEADING_SYMBOLS = Regex("""^[^\p{L}\p{N}]+""")
        private val MEDIA_TEXT = Regex(
            """^(?:you )?(?:(?:sent|shared)(?: you)? )?(?:a |an )?""" +
                """(reel|photo|image|picture|video|voice message|voice note|audio|sticker|gif|attachment|file|post)s?$""",
        )

        internal fun firstName(name: String): String = name.trim().substringBefore(' ').ifEmpty { name }
    }
}
