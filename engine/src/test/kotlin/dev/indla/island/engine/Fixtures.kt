package dev.indla.island.engine

import dev.indla.island.model.Importance
import dev.indla.island.model.RawAction
import dev.indla.island.model.RawMessage
import dev.indla.island.model.RawNotification
import dev.indla.island.model.RawPerson
import dev.indla.island.model.RawRanking

/**
 * Notifications as real apps post them, written down from the debug screen's JSON export
 * (field for field, with personal names swapped). Each is a function so tests can tweak a copy.
 */
object Fixtures {
    private val reply = RawAction(0, "Reply", hasRemoteInput = true)
    private val markRead = RawAction(1, "Mark as read")

    fun whatsAppDirect(count: Int = 2) = RawNotification(
        key = "0|com.whatsapp|1|919999999999@s.whatsapp.net|10123",
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        postTime = 1_000,
        groupKey = "0|com.whatsapp|g:group_key_messages",
        category = "msg",
        title = "Priya",
        text = "See you at 7?",
        messages = (1..count).map { i ->
            RawMessage(
                text = if (i == count) "See you at 7?" else "Hey!",
                timestamp = 1_000L + i,
                sender = RawPerson("Priya Rao", key = "priya", iconKey = "com.whatsapp|priya"),
            )
        },
        actions = listOf(reply, markRead),
    )

    fun whatsAppSummary() = RawNotification(
        key = "0|com.whatsapp|0|null|10123",
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        postTime = 1_000,
        groupKey = "0|com.whatsapp|g:group_key_messages",
        isGroupSummary = true,
        groupHasChildren = true,
        title = "WhatsApp",
        text = "3 messages from 2 chats",
    )

    /** The dynamicSpot screenshot case: a group chat that dumps "Sent a reel" eight times. */
    fun instagramGroupReels() = RawNotification(
        key = "0|com.instagram.android|64278|direct_thread_340282366|10234",
        packageName = "com.instagram.android",
        appLabel = "Instagram",
        postTime = 2_000,
        category = "msg",
        conversationTitle = "Weekend plans",
        isGroupConversation = true,
        messages = listOf(
            msg("Where are we going?", "Meera", 1),
            msg("Sent a reel", "Arpit", 2),
            msg("Sent a reel", "Arpit", 3),
            msg("Sent a reel", "Kabir", 4),
            msg("Sent a reel", "Arpit", 5),
            msg("Sent a reel", "Dev", 6),
        ),
        actions = listOf(reply),
    )

    fun telegramPhotos() = RawNotification(
        key = "0|org.telegram.messenger|5012|null|10345",
        packageName = "org.telegram.messenger",
        appLabel = "Telegram",
        postTime = 3_000,
        category = "msg",
        title = "Sam",
        messages = listOf(
            RawMessage("🖼 Photo", 1, RawPerson("Sam"), dataMimeType = "image/jpeg"),
            RawMessage("🖼 Photo", 2, RawPerson("Sam"), dataMimeType = "image/jpeg"),
            RawMessage("lol look at these", 3, RawPerson("Sam")),
        ),
        actions = listOf(reply, markRead),
    )

    fun telegramAfterMyReply() = RawNotification(
        key = "0|org.telegram.messenger|5013|null|10345",
        packageName = "org.telegram.messenger",
        appLabel = "Telegram",
        postTime = 3_500,
        category = "msg",
        title = "Sam",
        messages = listOf(
            RawMessage("one", 1, RawPerson("Sam")),
            RawMessage("two", 2, RawPerson("Sam")),
            RawMessage("ok", 3, sender = null),
            RawMessage("three", 4, RawPerson("Sam")),
        ),
        actions = listOf(reply),
    )

    fun gmail() = RawNotification(
        key = "0|com.google.android.gm|1234|gig:1|10456",
        packageName = "com.google.android.gm",
        appLabel = "Gmail",
        postTime = 4_000,
        category = "email",
        title = "Flight confirmation",
        text = "Your booking ABC123 is confirmed",
        bigText = "Your booking ABC123 is confirmed. Check-in opens 48 hours before departure.",
        actions = listOf(RawAction(0, "Archive"), RawAction(1, "Reply")),
    )

    /** Android 15+ hands untrusted listeners this instead of the code. */
    fun otpSmsRedacted() = RawNotification(
        key = "0|com.google.android.apps.messaging|77|null|10567",
        packageName = "com.google.android.apps.messaging",
        appLabel = "Messages",
        postTime = 5_000,
        category = "msg",
        title = "AX-HDFCBK",
        text = "Sensitive notification content hidden",
        actions = listOf(RawAction(0, "Copy code")),
    )

    fun spotifyNowPlaying() = RawNotification(
        key = "0|com.spotify.music|1|null|10678",
        packageName = "com.spotify.music",
        appLabel = "Spotify",
        postTime = 6_000,
        category = "transport",
        ongoing = true,
        title = "Song",
        text = "Artist",
    )

    fun youtubeMusic() = spotifyNowPlaying().copy(
        key = "0|com.google.android.apps.youtube.music|1|null|10789",
        packageName = "com.google.android.apps.youtube.music",
        appLabel = "YouTube Music",
    )

    fun foodDeliveryProgress() = RawNotification(
        key = "0|in.swiggy.android|9|null|10890",
        packageName = "in.swiggy.android",
        appLabel = "Swiggy",
        postTime = 7_000,
        ongoing = true,
        onlyAlertOnce = true,
        progress = 40,
        progressMax = 100,
        title = "Order on the way",
        text = "Arriving in 12 min",
    )

    fun cabRide() = foodDeliveryProgress().copy(
        key = "0|com.ubercab|3|null|10901",
        packageName = "com.ubercab",
        appLabel = "Uber",
        category = "navigation",
        title = "Driver arriving",
    )

    fun phoneCall() = RawNotification(
        key = "0|com.google.android.dialer|1|null|11012",
        packageName = "com.google.android.dialer",
        appLabel = "Phone",
        postTime = 8_000,
        category = "call",
        ongoing = true,
        title = "Mom",
        text = "Ongoing call",
        chronometerBase = 7_500,
        actions = listOf(RawAction(0, "Hang up")),
    )

    /** CallStyle incoming call: answer/decline live in extras, not in actions. */
    fun incomingCallStyle() = RawNotification(
        key = "0|com.google.android.dialer|2|null|11013",
        packageName = "com.google.android.dialer",
        appLabel = "Phone",
        postTime = 9_000,
        category = "call",
        template = "android.app.Notification\$CallStyle",
        callType = 1,
        callCanAnswer = true,
        callCanDecline = true,
        title = "Tania Castillo",
        text = "Mobile",
    )

    fun downloadProgress(percent: Int) = RawNotification(
        key = "0|com.android.providers.downloads|5|null|11100",
        packageName = "com.android.providers.downloads",
        appLabel = "Downloads",
        postTime = 10_000,
        category = "progress",
        ongoing = true,
        onlyAlertOnce = true,
        progress = percent,
        progressMax = 100,
        title = "island-v1.apk",
        text = "$percent% · 2 min left",
    )

    fun music(playing: Boolean, track: String = "Entropy") = dev.indla.island.model.MediaSnapshot(
        packageName = "com.spotify.music",
        appLabel = "Spotify",
        media = dev.indla.island.model.LiveContent.Media(
            track = track,
            artist = "Beach Bunny",
            playing = playing,
            durationMs = 211_000,
            positionMs = 170_000,
            positionAtElapsedMs = 0,
            speed = 1f,
            artKey = null,
            canPrevious = true,
            canNext = true,
            canSeek = true,
        ),
    )

    fun silent(base: RawNotification = whatsAppDirect()) =
        base.copy(ranking = RawRanking(importance = Importance.LOW))

    private fun msg(text: String, sender: String, t: Long) =
        RawMessage(text, t, RawPerson(sender, key = sender.lowercase(), iconKey = "ig|$sender"))
}
