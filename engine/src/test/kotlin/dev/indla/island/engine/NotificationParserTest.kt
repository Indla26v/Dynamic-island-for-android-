package dev.indla.island.engine

import dev.indla.island.model.AppRule
import dev.indla.island.model.IslandItem
import dev.indla.island.model.ItemKind
import dev.indla.island.model.MessageLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private val parser = NotificationParser()

    private fun show(raw: dev.indla.island.model.RawNotification): IslandItem {
        val result = parser.parse(raw, AppRule(raw.packageName, show = true))
        assertTrue("expected Show, got $result", result is ParseResult.Show)
        return (result as ParseResult.Show).item
    }

    private fun skip(raw: dev.indla.island.model.RawNotification): SkipReason {
        val result = parser.parse(raw, AppRule(raw.packageName, show = true))
        assertTrue("expected Skip, got $result", result is ParseResult.Skip)
        return (result as ParseResult.Skip).reason
    }

    @Test
    fun whatsAppDirect_readsMessagingStyle() {
        val item = show(Fixtures.whatsAppDirect(count = 2))
        assertEquals(ItemKind.MESSAGE, item.kind)
        assertEquals("Priya Rao", item.title)
        assertEquals("2 new", item.glance)
        assertEquals("com.whatsapp|priya", item.avatarKey)
        assertEquals(listOf("Hey!", "See you at 7?"), item.lines.map { it.text })
        assertEquals("Priya Rao", item.lines.last().sender)
        assertEquals(0, item.replyAction?.index)
    }

    @Test
    fun singleMessage_glanceIsSenderFirstName() {
        assertEquals("Priya", show(Fixtures.whatsAppDirect(count = 1)).glance)
    }

    @Test
    fun instagramGroup_collapsesRepeatedReels() {
        val item = show(Fixtures.instagramGroupReels())
        assertEquals("Weekend plans", item.title)
        assertEquals(
            listOf(
                MessageLine("Meera", "Where are we going?", "ig|Meera"),
                MessageLine("Arpit + 2", "sent 5 reels", "ig|Arpit", count = 5),
            ),
            item.lines,
        )
    }

    @Test
    fun telegram_collapsesPhotosByMimeType_andKeepsLastThreeLines() {
        val item = show(Fixtures.telegramPhotos())
        assertEquals(listOf("sent 2 photos", "lol look at these"), item.lines.map { it.text })
        assertEquals("Sam", item.lines.first().sender)
    }

    @Test
    fun unreadCount_resetsAfterUsersOwnReply() {
        val item = show(Fixtures.telegramAfterMyReply())
        assertEquals("Sam", item.glance)
        assertEquals(listOf("two", "ok", "three"), item.lines.map { it.text })
        assertEquals("You", item.lines[1].sender)
    }

    @Test
    fun gmail_isGenericWithBigText() {
        val item = show(Fixtures.gmail())
        assertEquals(ItemKind.GENERIC, item.kind)
        assertEquals("Flight confirmation", item.title)
        assertTrue(item.lines.single().text.startsWith("Your booking ABC123 is confirmed. Check-in"))
        assertNull(item.replyAction)
        assertEquals(listOf("Archive", "Reply"), item.actions.map { it.title })
    }

    @Test
    fun redactedOtp_showsHiddenByAndroid_neverBlank() {
        val item = show(Fixtures.otpSmsRedacted())
        assertEquals(ItemKind.HIDDEN_BY_ANDROID, item.kind)
        assertEquals("Messages", item.title)
        assertEquals("Hidden by Android", item.glance)
        assertTrue(item.actions.isEmpty())
    }

    @Test
    fun localizedRedactionMarker_isDetected() {
        val german = NotificationParser(setOf("Vertrauliche Benachrichtigungsinhalte ausgeblendet"))
        val raw = Fixtures.otpSmsRedacted().copy(text = "Vertrauliche Benachrichtigungsinhalte ausgeblendet")
        assertTrue(german.isRedacted(raw))
        assertTrue(!parser.isRedacted(raw))
    }

    @Test
    fun liveActivities_areLaterPhase() {
        assertEquals(SkipReason.LATER_PHASE_CATEGORY, skip(Fixtures.spotifyNowPlaying()))
        assertEquals(SkipReason.LATER_PHASE_CATEGORY, skip(Fixtures.youtubeMusic()))
        assertEquals(SkipReason.LATER_PHASE_CATEGORY, skip(Fixtures.phoneCall()))
        assertEquals(SkipReason.LATER_PHASE_CATEGORY, skip(Fixtures.cabRide()))
        assertEquals(SkipReason.ONGOING, skip(Fixtures.foodDeliveryProgress()))
    }

    @Test
    fun emptyNotification_isSkipped() {
        assertEquals(SkipReason.EMPTY, skip(Fixtures.gmail().copy(title = null, text = null, bigText = null)))
    }

    @Test
    fun contentHash_isStableForSameContent_andChangesWithIt() {
        val a = show(Fixtures.whatsAppDirect())
        val b = show(Fixtures.whatsAppDirect().copy(postTime = 99_999))
        val c = show(Fixtures.whatsAppDirect(count = 3))
        assertEquals(a.contentHash, b.contentHash)
        assertTrue(a.contentHash != c.contentHash)
    }

    @Test
    fun mediaText_variantsAreRecognised() {
        fun kind(text: String) = parser.mediaKind(dev.indla.island.model.RawMessage(text, 0, null))
        assertEquals(NotificationParser.MediaKind.REEL, kind("Sent a reel"))
        assertEquals(NotificationParser.MediaKind.REEL, kind("sent you a reel."))
        assertEquals(NotificationParser.MediaKind.PHOTO, kind("📷 Photo"))
        assertEquals(NotificationParser.MediaKind.VOICE, kind("🎤 Voice message"))
        assertEquals(NotificationParser.MediaKind.STICKER, kind("Sticker"))
        assertNull(kind("I sent a reel yesterday, did you see it?"))
    }
}
