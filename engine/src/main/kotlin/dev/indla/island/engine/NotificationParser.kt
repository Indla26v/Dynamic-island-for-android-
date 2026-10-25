package dev.indla.island.engine

import dev.indla.island.model.ActionRole
import dev.indla.island.model.AppRule
import dev.indla.island.model.CallState
import dev.indla.island.model.IslandAction
import dev.indla.island.model.IslandItem
import dev.indla.island.model.ItemKind
import dev.indla.island.model.LiveContent
import dev.indla.island.model.MessageLine
import dev.indla.island.model.RawMessage
import dev.indla.island.model.RawNotification
import dev.indla.island.model.Tracking
import kotlin.math.roundToInt

sealed interface ParseResult {
    data class Show(val item: IslandItem) : ParseResult
    data class Skip(val reason: SkipReason) : ParseResult
}

enum class SkipReason {
    /** Ongoing but not a call or progress (foreground services, system status). */
    ONGOING,
    /** Media is read from MediaSession instead, so its notification would be a duplicate. */
    MEDIA_SESSION,
    SYSTEM_CATEGORY,
    EMPTY,
}

/**
 * Turns a [RawNotification] into an [IslandItem]. Chats are read from MessagingStyle (sender,
 * timestamp, avatar), never by joining text lines, and repeated media is collapsed. Calls
 * (CallStyle or a dialer's ongoing notification) and progress (downloads, deliveries, rides,
 * Android 16 ProgressStyle) become live activities.
 *
 * @param redactedMarkers texts Android substitutes when it hides content from untrusted
 *   listeners (Android 15+). The listener adds the device's localized system string.
 */
class NotificationParser(
    redactedMarkers: Set<String> = DEFAULT_REDACTED_MARKERS,
) {
    private val redactedMarkers = redactedMarkers.map { it.trim().lowercase() }.toSet()

    fun parse(raw: RawNotification, rule: AppRule): ParseResult {
        if (isMedia(raw)) return ParseResult.Skip(SkipReason.MEDIA_SESSION)

        val item = when {
            isCall(raw) -> callItem(raw)
            // Before timers: a ride or delivery often runs a clock ("arriving in") too.
            trackingKind(raw) != null -> trackingItem(raw, trackingKind(raw)!!)
            isTimer(raw) -> timerItem(raw)
            raw.category in SYSTEM_CATEGORIES -> return ParseResult.Skip(SkipReason.SYSTEM_CATEGORY)
            isNavigation(raw) -> navigationItem(raw)
            isProgress(raw) -> progressItem(raw)
            raw.ongoing -> return ParseResult.Skip(SkipReason.ONGOING)
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

    fun isMedia(raw: RawNotification): Boolean =
        raw.category == "transport" || raw.template?.contains("MediaStyle") == true

    fun isCall(raw: RawNotification): Boolean =
        raw.template?.endsWith("CallStyle") == true || raw.callType != 0 || raw.category == "call"

    /**
     * Clock timers and stopwatches: a running chronometer that isn't a call, the stopwatch
     * category, an ongoing alarm-category notification, or anything ongoing from a clock app.
     */
    fun isTimer(raw: RawNotification): Boolean =
        raw.category == "stopwatch" ||
            (raw.chronometerBase != null && raw.ongoing) ||
            (raw.ongoing && (raw.category == "alarm" || raw.packageName in CLOCK_PACKAGES || isRecording(raw)))

    /** Voice recorders and screen recorders: their chronometer is the recording's length. */
    fun isRecording(raw: RawNotification): Boolean =
        RECORDER_PACKAGE.containsMatchIn(raw.packageName) ||
            listOfNotNull(raw.title, raw.text, raw.appLabel).any { RECORDING.containsMatchIn(it) }

    private fun timerItem(raw: RawNotification): IslandItem {
        val title = raw.title?.trim().orEmpty()
        val text = raw.text?.trim().orEmpty()
        // Clock puts the time in the title and "Timer" below it; other apps do the reverse.
        val (time, label) = when {
            TIME.matches(title) -> title to text.ifEmpty { "Timer" }
            TIME.matches(text) -> text to title.ifEmpty { "Timer" }
            else -> "" to title.ifEmpty { text.ifEmpty { "Timer" } }
        }
        val recording = isRecording(raw)
        // Counting down only when the app says so, the base is still ahead (a timer's end), or
        // it's a clock app's timer. Recorders and stopwatches count up.
        val countDown = !recording && (
            raw.chronometerCountDown ||
                (raw.chronometerBase?.let { it > raw.postTime + 1_000 } == true) ||
                (raw.packageName in CLOCK_PACKAGES && raw.category != "stopwatch" && !label.contains("stopwatch", ignoreCase = true))
            )
        val base = baseItem(raw)
        val actions = base.actions.map { a ->
            val role = when {
                PAUSE.containsMatchIn(a.title) -> ActionRole.PAUSE
                RESUME.containsMatchIn(a.title) -> ActionRole.RESUME
                STOP.containsMatchIn(a.title) -> ActionRole.STOP
                else -> ActionRole.NORMAL
            }
            a.copy(role = role)
        }
        // Buttons drawn inside the app's own view (no notification actions): pressed there.
        val custom = buildList {
            if (raw.customToggle && actions.none { it.role == ActionRole.PAUSE || it.role == ActionRole.RESUME }) {
                val running = raw.chronometerBase != null
                add(IslandAction(ActionRole.CUSTOM_TOGGLE_INDEX, if (running) "Pause" else "Resume", false, if (running) ActionRole.PAUSE else ActionRole.RESUME))
            }
            if (raw.customStop && actions.none { it.role == ActionRole.STOP }) {
                add(IslandAction(ActionRole.CUSTOM_STOP_INDEX, "Stop", false, ActionRole.STOP))
            }
        }
        return base.copy(
            kind = ItemKind.TIMER,
            title = label,
            glance = time,
            lines = emptyList(),
            actions = actions + custom,
            live = LiveContent.Timer(label, raw.chronometerBase, countDown, time, recording),
        )
    }

    /** Ongoing work with progress, or the categories delivery, ride and navigation apps use. */
    fun isProgress(raw: RawNotification): Boolean =
        raw.template?.endsWith("ProgressStyle") == true ||
            (raw.hasProgress && (raw.ongoing || raw.onlyAlertOnce)) ||
            (raw.ongoing && raw.category in PROGRESS_CATEGORIES)

    private fun callItem(raw: RawNotification): IslandItem {
        val actions = raw.actions.filter { !it.isContextual && it.title.isNotBlank() }
        val state = when (raw.callType) {
            CALL_TYPE_INCOMING -> CallState.INCOMING
            CALL_TYPE_SCREENING -> CallState.SCREENING
            CALL_TYPE_ONGOING -> CallState.ONGOING
            else -> when {
                raw.chronometerBase != null -> CallState.ONGOING
                actions.any { ANSWER.containsMatchIn(it.title) } -> CallState.INCOMING
                else -> CallState.ONGOING
            }
        }
        val caller = raw.title?.trim()?.takeIf { it.isNotEmpty() } ?: raw.appLabel
        // CallStyle keeps answer/decline/hang-up as intents in extras; older dialers use actions.
        val callActions = if (raw.callType != 0) {
            buildList {
                if (raw.callCanDecline) add(IslandAction(ActionRole.DECLINE_INDEX, "Decline", false, ActionRole.DECLINE))
                if (raw.callCanAnswer) add(IslandAction(ActionRole.ANSWER_INDEX, "Answer", false, ActionRole.ANSWER))
                if (raw.callCanHangUp) add(IslandAction(ActionRole.HANG_UP_INDEX, "Hang up", false, ActionRole.HANG_UP))
            }
        } else {
            actions.take(MAX_ACTIONS).map { a ->
                val role = when {
                    ANSWER.containsMatchIn(a.title) -> ActionRole.ANSWER
                    DECLINE.containsMatchIn(a.title) -> ActionRole.DECLINE
                    HANG_UP.containsMatchIn(a.title) -> ActionRole.HANG_UP
                    else -> ActionRole.NORMAL
                }
                IslandAction(a.index, a.title.trim(), isReply = false, role = role)
            }
        }
        return baseItem(raw).copy(
            kind = ItemKind.CALL,
            title = caller,
            glance = when (state) {
                CallState.INCOMING -> "Incoming"
                CallState.SCREENING -> "Screening"
                CallState.ONGOING -> ""
            },
            lines = listOfNotNull(raw.text?.trim()?.takeIf { it.isNotEmpty() }?.let { MessageLine(null, it) }),
            actions = callActions,
            avatarKey = raw.largeIconKey,
            live = LiveContent.Call(caller, state, raw.chronometerBase ?: raw.postTime),
        )
    }

    /**
     * A delivery, ride, bus or parcel being tracked: an ongoing notification from an app known to
     * track (Zomato, Swiggy and Instamart, Blinkit, Zepto, Uber, Ola, Rapido, redBus...), or any
     * ongoing progress/status notification whose text gives an arrival time. Null otherwise.
     */
    fun trackingKind(raw: RawNotification): Tracking? {
        if (!raw.ongoing) return null
        val known = TRACKING_APPS[raw.packageName]
        val text = listOfNotNull(raw.title, raw.text, raw.bigText, raw.subText).joinToString(" ")
        val byWords = when {
            BUS_WORDS.containsMatchIn(text) -> Tracking.BUS
            RIDE_WORDS.containsMatchIn(text) -> Tracking.RIDE
            PARCEL_WORDS.containsMatchIn(text) -> Tracking.PARCEL
            else -> null
        }
        return when {
            known != null -> byWords?.takeIf { known == Tracking.PARCEL || it == known } ?: known
            // Other apps: a status / location-sharing notification that talks like a delivery
            // or ride and gives an arrival time. (Downloads say "2 min left" too; they aren't.)
            raw.category in TRACKING_CATEGORIES && eta(text) != null && (byWords != null || ON_ITS_WAY.containsMatchIn(text)) ->
                byWords ?: Tracking.FOOD
            else -> null
        }
    }

    private fun trackingItem(raw: RawNotification, kind: Tracking): IslandItem {
        val texts = listOfNotNull(raw.title, raw.text, raw.bigText, raw.subText).map { it.trim() }.filter { it.isNotEmpty() }
        val eta = eta(texts.joinToString(" "))
        val fraction = if (raw.hasProgress && raw.progressMax > 0 && !raw.progressIndeterminate) {
            (raw.progress.toFloat() / raw.progressMax).coerceIn(0f, 1f)
        } else {
            null
        }
        val title = raw.title?.trim()?.takeIf { it.isNotEmpty() } ?: raw.appLabel
        val status = texts.firstOrNull { it != title }.orEmpty()
        return baseItem(raw).copy(
            kind = ItemKind.PROGRESS,
            title = title,
            glance = eta ?: fraction?.let { "${(it * 100).roundToInt()}%" } ?: status.takeIf { it.length <= 12 }.orEmpty(),
            lines = listOfNotNull(status.takeIf { it.isNotEmpty() }?.let { MessageLine(null, it) }),
            avatarKey = raw.largeIconKey,
            live = LiveContent.Progress(fraction, status, eta = eta, tracking = kind),
        )
    }

    /** "12 min" from "Arriving in 12 mins", "in 8 minutes", "12 min away"; or "7:45 PM", or "3 km away". */
    fun eta(text: String): String? {
        MINUTES.find(text)?.let { return "${it.groupValues[1]} min" }
        CLOCK_TIME.find(text)?.let { return it.value.trim() }
        DISTANCE.find(text)?.let { return "${it.groupValues[1]} ${it.groupValues[2].lowercase()}" }
        return null
    }

    /** Google Maps, Waze and other turn-by-turn apps while navigating. */
    fun isNavigation(raw: RawNotification): Boolean =
        raw.ongoing && (raw.category == "navigation" || raw.packageName in NAVIGATION_PACKAGES)

    private fun navigationItem(raw: RawNotification): IslandItem {
        val distance = raw.title?.trim()?.takeIf { it.isNotEmpty() } ?: raw.appLabel
        val road = raw.text?.trim().orEmpty()
        return baseItem(raw).copy(
            kind = ItemKind.PROGRESS,
            title = distance,
            glance = distance,
            lines = listOfNotNull(road.takeIf { it.isNotEmpty() }?.let { MessageLine(null, it) }),
            avatarKey = raw.largeIconKey,
            live = LiveContent.Progress(null, road, navigation = true, eta = raw.subText?.trim()?.takeIf { it.isNotEmpty() }),
        )
    }

    private fun progressItem(raw: RawNotification): IslandItem {
        val fraction = if (raw.progressIndeterminate || raw.progressMax <= 0) {
            null
        } else {
            (raw.progress.toFloat() / raw.progressMax).coerceIn(0f, 1f)
        }
        val percent = fraction?.let { "${(it * 100).roundToInt()}%" }
        val status = listOfNotNull(raw.text, raw.subText).map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: percent.orEmpty()
        val title = raw.title?.trim()?.takeIf { it.isNotEmpty() } ?: raw.appLabel
        return baseItem(raw).copy(
            kind = ItemKind.PROGRESS,
            title = title,
            glance = percent ?: status.takeIf { it.length <= 14 }.orEmpty(),
            lines = listOfNotNull(status.takeIf { it.isNotEmpty() }?.let { MessageLine(null, it) }),
            avatarKey = raw.largeIconKey,
            live = LiveContent.Progress(fraction, status),
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

        /** System plumbing that never belongs on the island. */
        val SYSTEM_CATEGORIES = setOf("service", "sys", "alarm", "stopwatch")

        val PAUSE = Regex("(?i)^\\s*pause")
        val RESUME = Regex("(?i)^\\s*(resume|continue|start|play)")
        val STOP = Regex("(?i)^\\s*(stop|save|done|finish|end|dismiss|cancel)")
        val RECORDER_PACKAGE = Regex("(?i)recorder|voicenote|voicememo")
        val RECORDING = Regex("(?i)\\brecord(ing|er)\\b")

        /** Clock apps whose ongoing notifications are timers or stopwatches. */
        val CLOCK_PACKAGES = setOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.android.BBKClock",
            "com.sec.android.app.clockpackage",
            "com.oneplus.deskclock",
            "com.coloros.alarmclock",
            "com.oplus.alarmclock",
            "com.android.alarmclock",
            "com.huawei.deskclock",
            "com.miui.clock",
        )

        private val TIME = Regex("""-?\d{1,2}(:\d{2}){1,2}""")

        /** Ongoing notifications in these categories are deliveries, rides, navigation. */
        /** Apps whose ongoing notifications track something on its way, and what it is. */
        val TRACKING_APPS = mapOf(
            "com.application.zomato" to Tracking.FOOD,
            "in.swiggy.android" to Tracking.FOOD,
            "com.done.faasos" to Tracking.FOOD,
            "com.global.foodpanda.android" to Tracking.FOOD,
            "com.grofers.customerapp" to Tracking.GROCERY,
            "in.swiggy.android.instamart" to Tracking.GROCERY,
            "com.zeptoconsumerapp" to Tracking.GROCERY,
            "com.bigbasket.mobileapp" to Tracking.GROCERY,
            "com.dunzo.user" to Tracking.GROCERY,
            "com.jio.jiomart" to Tracking.GROCERY,
            "com.ubercab" to Tracking.RIDE,
            "com.olacabs.customer" to Tracking.RIDE,
            "com.rapido.passenger" to Tracking.RIDE,
            "com.bluesmart.blusmartapp" to Tracking.RIDE,
            "com.theporter.android.customerapp" to Tracking.PARCEL,
            "in.redbus.android" to Tracking.BUS,
            "com.abhibus.mobile" to Tracking.BUS,
            "in.amazon.mShop.android.shopping" to Tracking.PARCEL,
            "com.flipkart.android" to Tracking.PARCEL,
            "com.myntra.android" to Tracking.PARCEL,
            "com.meesho.supply" to Tracking.PARCEL,
        )
        val TRACKING_CATEGORIES = setOf("status", "location_sharing", "transport_tracking")
        val ON_ITS_WAY = Regex("(?i)arriv|on (its|the) way|deliver|your order|picked up|rider|eta")
        val BUS_WORDS = Regex("(?i)\\bbus\\b|boarding point|bus stop")
        val RIDE_WORDS = Regex("(?i)\\b(driver|captain|cab|auto|ride|pickup|pick-up|bike taxi)\\b")
        val PARCEL_WORDS = Regex("(?i)out for delivery|shipped|package|parcel|courier")
        val MINUTES = Regex("(?i)(\\d{1,3})\\s*(?:-\\s*\\d{1,3}\\s*)?(?:min|mins|minute|minutes)\\b")
        val CLOCK_TIME = Regex("(?i)\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b")
        val DISTANCE = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(km|m)\\s+away")
        val NAVIGATION_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.google.android.apps.mapslite",
            "com.waze",
            "com.here.app.maps",
            "com.sygic.aura",
            "net.osmand",
            "com.mapmyindia.app",
        )
        val PROGRESS_CATEGORIES = setOf("progress", "navigation", "status", "location_sharing")

        const val CALL_TYPE_INCOMING = 1
        const val CALL_TYPE_ONGOING = 2
        const val CALL_TYPE_SCREENING = 3

        private val ANSWER = Regex("""(?i)\b(answer|accept|pick up)\b""")
        private val DECLINE = Regex("""(?i)\b(decline|reject|dismiss|ignore)\b""")
        private val HANG_UP = Regex("""(?i)\b(hang ?up|end( call)?|disconnect)\b""")

        private val LEADING_SYMBOLS = Regex("""^[^\p{L}\p{N}]+""")
        private val MEDIA_TEXT = Regex(
            """^(?:you )?(?:(?:sent|shared)(?: you)? )?(?:a |an )?""" +
                """(reel|photo|image|picture|video|voice message|voice note|audio|sticker|gif|attachment|file|post)s?$""",
        )

        internal fun firstName(name: String): String = name.trim().substringBefore(' ').ifEmpty { name }
    }
}
