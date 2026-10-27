package dev.indla.island.engine

import dev.indla.island.model.AppRule
import dev.indla.island.model.Environment
import dev.indla.island.model.IslandGesture
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.ItemKind
import dev.indla.island.model.LiveContent
import dev.indla.island.model.MediaCommand
import dev.indla.island.model.SystemEvent
import dev.indla.island.model.Priority
import dev.indla.island.model.WhenToShow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IslandEngineTest {
    private class RecordingEffects : IslandEffects {
        val calls = mutableListOf<String>()
        override fun openItem(key: String) { calls += "open:$key" }
        override fun invokeAction(key: String, actionIndex: Int) { calls += "action:$key:$actionIndex" }
        override fun reply(key: String, actionIndex: Int, text: String) { calls += "reply:$key:$actionIndex:$text" }
        override fun dismissNotification(key: String) { calls += "dismiss:$key" }
        override fun muteApp(packageName: String) { calls += "mute:$packageName" }
        override fun mediaCommand(packageName: String, command: MediaCommand) { calls += "media:$packageName:$command" }
    }

    private class Harness(scope: TestScope) {
        // Stacking and the missed pile have their own tests; the rest see one item per post.
        val settings = MutableStateFlow(
            IslandSettings(autoHideSeconds = 5, whenToShow = WhenToShow(keepMissed = false, stackByApp = false, hideWhenShadeOpen = true)),
        )
        val rules = MutableStateFlow<Map<String, AppRule>>(emptyMap())
        val effects = RecordingEffects()
        val engine = IslandEngine(scope.backgroundScope, settings, rules, clock = { scope.testScheduler.currentTime })
            .also { it.effects = effects }
        val state get() = engine.state.value
    }

    private fun test(block: suspend TestScope.(Harness) -> Unit) = runTest {
        val h = Harness(this)
        runCurrent()
        block(h)
        backgroundScope.cancel()
    }

    @Test
    fun messagePops_thenAutoHides() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(IslandMode.COMPACT, h.state.mode)
        assertEquals("2 new", h.state.current?.glance)

        advanceTimeBy(4_900); runCurrent()
        assertEquals(IslandMode.COMPACT, h.state.mode)
        advanceTimeBy(200); runCurrent()
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun sameKeySameContent_isIgnored() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onGesture(IslandGesture.SWIPE_UP) // hide until next event
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.onPosted(Fixtures.whatsAppDirect().copy(postTime = 5_000))
        assertEquals("duplicate must not pop again", IslandMode.REST, h.state.mode)
        h.engine.onPosted(Fixtures.whatsAppDirect(count = 3))
        assertEquals(IslandMode.COMPACT, h.state.mode)
        assertEquals("3 new", h.state.current?.glance)
    }

    @Test
    fun onlyAlertOnceUpdate_refreshesInPlaceWithoutPopping() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onGesture(IslandGesture.TAP)
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        h.engine.onPosted(Fixtures.whatsAppDirect(count = 3).copy(onlyAlertOnce = true))
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        assertEquals("3 new", h.state.current?.glance)
        assertEquals(1, h.state.items.size)
    }

    @Test
    fun groupSummary_neverPopsWhenChildrenPresent() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppSummary())
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onPosted(Fixtures.whatsAppSummary().copy(text = "4 messages", groupHasChildren = false))
        assertEquals(1, h.state.items.size)
        assertEquals(ItemKind.MESSAGE, h.state.current?.kind)
    }

    @Test
    fun silentNotifications_neverPop() = test { h ->
        h.engine.onPosted(Fixtures.silent())
        assertEquals(IslandMode.REST, h.state.mode)
        h.rules.value = mapOf("com.whatsapp" to AppRule("com.whatsapp", show = true, priority = Priority.SILENT))
        runCurrent()
        h.engine.onPosted(Fixtures.whatsAppDirect(count = 4))
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun appsOffByDefault_unlessMessaging() = test { h ->
        h.engine.onPosted(Fixtures.gmail().copy(packageName = "com.example.shop"))
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.onPosted(Fixtures.gmail())
        assertEquals(IslandMode.COMPACT, h.state.mode)
    }

    @Test
    fun hiddenInForegroundApp() = test { h ->
        h.engine.updateEnvironment(Environment(foregroundPackage = "com.whatsapp"))
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(IslandMode.REST, h.state.mode)

        h.engine.updateEnvironment(Environment(foregroundPackage = "com.android.launcher"))
        h.engine.onPosted(Fixtures.instagramGroupReels())
        assertEquals(IslandMode.COMPACT, h.state.mode)
        h.engine.updateEnvironment(Environment(foregroundPackage = "com.instagram.android"))
        assertEquals("opening the app clears its item", IslandMode.REST, h.state.mode)
    }

    @Test
    fun whenToShowRules_hideTheIsland() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.updateEnvironment(Environment(landscape = true))
        assertFalse(h.state.visible)
        h.engine.updateEnvironment(Environment(fullscreen = true))
        assertFalse(h.state.visible)
        h.engine.updateEnvironment(Environment(shadeOpen = true))
        assertFalse(h.state.visible)
        h.engine.updateEnvironment(Environment())
        assertTrue(h.state.visible)

        h.settings.value = h.settings.value.copy(whenToShow = h.settings.value.whenToShow.copy(hideInLandscape = false))
        runCurrent()
        h.engine.updateEnvironment(Environment(landscape = true))
        assertTrue(h.state.visible)
    }

    @Test
    fun byDefault_staysOnTopOfTheOpenPanel() = test { h ->
        h.settings.value = h.settings.value.copy(whenToShow = h.settings.value.whenToShow.copy(hideWhenShadeOpen = false))
        runCurrent()
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.updateEnvironment(Environment(shadeOpen = true))
        assertTrue("pulling the panel down keeps the island", h.state.visible)
        assertTrue(WhenToShow().hideWhenShadeOpen.not())
    }

    @Test
    fun lockscreen_hidesContentPerApp() = test { h ->
        h.rules.value = mapOf("com.whatsapp" to AppRule("com.whatsapp", show = true, hideContentOnLock = true))
        runCurrent()
        h.engine.updateEnvironment(Environment(locked = true))
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(ItemKind.HIDDEN_ON_LOCKSCREEN, h.state.current?.kind)
        assertTrue(h.state.current!!.actions.isEmpty())
        h.engine.updateEnvironment(Environment(locked = false))
        assertEquals(ItemKind.MESSAGE, h.state.current?.kind)
    }

    @Test
    fun defaultGestures() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onGesture(IslandGesture.SWIPE_DOWN)
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        h.engine.onGesture(IslandGesture.SWIPE_UP)
        assertEquals(IslandMode.COMPACT, h.state.mode)
        h.engine.onGesture(IslandGesture.LONG_PRESS)
        assertTrue(h.state.quickActionsOpen)
        h.engine.onOutsideTouch()
        assertEquals(IslandMode.COMPACT, h.state.mode)
        h.engine.onGesture(IslandGesture.TAP)
        h.engine.onGesture(IslandGesture.TAP) // expanded tap opens the chat
        assertEquals(listOf("open:${Fixtures.whatsAppDirect().key}"), h.effects.calls)
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun swipeDownOnOpenMessage_readsMore() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onGesture(IslandGesture.SWIPE_DOWN)
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        assertFalse(h.state.readMore)
        h.engine.onGesture(IslandGesture.SWIPE_DOWN)
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        assertTrue(h.state.readMore)
        h.engine.onGesture(IslandGesture.SWIPE_UP)
        assertEquals(IslandMode.COMPACT, h.state.mode)
        assertFalse(h.state.readMore)
    }

    @Test
    fun twoItems_splitAndSwipeSwitches() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onPosted(Fixtures.telegramPhotos())
        assertEquals(IslandMode.SPLIT, h.state.mode)
        assertEquals("org.telegram.messenger", h.state.current?.packageName)
        assertEquals("com.whatsapp", h.state.secondary?.packageName)
        h.engine.onGesture(IslandGesture.SWIPE_LEFT)
        assertEquals("com.whatsapp", h.state.current?.packageName)
        val bubbleIndex = h.state.items.indexOf(h.state.secondary)
        h.engine.onSelectItem(bubbleIndex)
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        assertEquals("org.telegram.messenger", h.state.current?.packageName)
    }

    @Test
    fun inlineReply_sendsThroughRemoteInputAction() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onGesture(IslandGesture.TAP)
        h.engine.onActionTapped(0)
        assertTrue(h.state.replyOpen)

        // A new message while typing must not steal the card.
        h.engine.onPosted(Fixtures.telegramPhotos())
        assertEquals("com.whatsapp", h.state.current?.packageName)
        advanceTimeBy(60_000); runCurrent()
        assertTrue("no timeout while replying", h.state.replyOpen)

        h.engine.onReplySent("  On my way  ")
        assertEquals("reply:${Fixtures.whatsAppDirect().key}:0:On my way", h.effects.calls.single())
        assertFalse(h.state.replyOpen)
        assertEquals("org.telegram.messenger", h.state.current?.packageName)
    }

    @Test
    fun appEchoingTheUsersReply_doesNotPopAgain() = test { h ->
        h.engine.onPosted(Fixtures.telegramPhotos())
        h.engine.onGesture(IslandGesture.TAP)
        h.engine.onActionTapped(0)
        h.engine.onReplySent("haha")
        assertEquals(IslandMode.REST, h.state.mode)
        val raw = Fixtures.telegramPhotos()
        val echoed = raw.copy(
            onlyAlertOnce = true,
            messages = raw.messages + dev.indla.island.model.RawMessage("haha", 10, sender = null),
        )
        h.engine.onPosted(echoed)
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun snapshotOnConnect_showsNothing_butDedupesLaterRepost() = test { h ->
        h.engine.onSnapshot(listOf(Fixtures.whatsAppDirect()))
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun plainActionAndQuickActions() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onPosted(Fixtures.instagramGroupReels())
        h.engine.onGesture(IslandGesture.TAP)
        h.engine.onMuteApp()
        assertEquals("mute:com.instagram.android", h.effects.calls.last())
        assertEquals(listOf("com.whatsapp"), h.state.items.map { it.packageName })

        h.engine.onActionTapped(1) // Mark as read
        assertEquals("action:${Fixtures.whatsAppDirect().key}:1", h.effects.calls.last())
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun expandedCollapsesAfterIdle_thenHides() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onGesture(IslandGesture.TAP)
        advanceTimeBy(IslandEngine.EXPANDED_IDLE_MS + 10); runCurrent()
        assertEquals(IslandMode.COMPACT, h.state.mode)
        advanceTimeBy(5_100); runCurrent()
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun autoExpandRule_popsStraightToExpanded() = test { h ->
        h.rules.value = mapOf("com.whatsapp" to AppRule("com.whatsapp", show = true, autoExpand = true))
        runCurrent()
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(IslandMode.EXPANDED, h.state.mode)
    }

    @Test
    fun removedNotification_leavesIsland_andCanPopAgain() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onRemoved(Fixtures.whatsAppDirect().key)
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(IslandMode.COMPACT, h.state.mode)
    }

    @Test
    fun masterSwitchOff_hidesEverything() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.settings.value = h.settings.value.copy(enabled = false)
        runCurrent()
        assertFalse(h.state.visible)
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.onPosted(Fixtures.telegramPhotos())
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun selfTest_alwaysShows_evenInOwnApp() = test { h ->
        h.engine.updateEnvironment(Environment(foregroundPackage = "dev.indla.island"))
        h.engine.onPosted(
            Fixtures.whatsAppDirect().copy(packageName = "dev.indla.island", key = "self", isSelfTest = true),
        )
        assertEquals(IslandMode.COMPACT, h.state.mode)
    }

    // ---- Live activities --------------------------------------------------------------------

    @Test
    fun ongoingCall_staysUntilRemoved() = test { h ->
        h.engine.onPosted(Fixtures.phoneCall())
        assertEquals(IslandMode.COMPACT, h.state.mode)
        advanceTimeBy(120_000); runCurrent()
        assertEquals("live activities never auto-hide", ItemKind.CALL, h.state.current?.kind)
        h.engine.onRemoved(Fixtures.phoneCall().key)
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun notificationTakesOver_thenIslandReturnsToCall() = test { h ->
        h.engine.onPosted(Fixtures.phoneCall())
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(IslandMode.SPLIT, h.state.mode)
        assertEquals(ItemKind.MESSAGE, h.state.current?.kind)
        assertEquals(ItemKind.CALL, h.state.secondary?.kind)
        advanceTimeBy(6_000); runCurrent()
        assertEquals(IslandMode.COMPACT, h.state.mode)
        assertEquals(ItemKind.CALL, h.state.current?.kind)
    }

    @Test
    fun progressUpdates_inPlace_withoutPopping() = test { h ->
        h.engine.onPosted(Fixtures.downloadProgress(10))
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(ItemKind.MESSAGE, h.state.current?.kind)
        h.engine.onPosted(Fixtures.downloadProgress(55))
        assertEquals("tick must not steal the island", ItemKind.MESSAGE, h.state.current?.kind)
        assertEquals(0.55f, (h.state.secondary?.live as LiveContent.Progress).fraction!!, 0.001f)
    }

    @Test
    fun media_playingStays_pausedLingersThenLeaves() = test { h ->
        h.engine.onMedia(Fixtures.music(playing = true))
        assertEquals(ItemKind.MEDIA, h.state.current?.kind)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(ItemKind.MEDIA, h.state.current?.kind)
        h.engine.onMedia(Fixtures.music(playing = false))
        advanceTimeBy(IslandEngine.LIVE_LINGER_MS + 100); runCurrent()
        assertEquals(IslandMode.REST, h.state.mode)
    }

    private fun Harness.stackAndKeep() {
        settings.value = settings.value.copy(whenToShow = settings.value.whenToShow.copy(keepMissed = true, stackByApp = true))
    }

    private fun otherChat() = Fixtures.whatsAppDirect().copy(
        key = "0|com.whatsapp|1|918888888888@s.whatsapp.net|10123",
        title = "Arjun",
        text = "Running late",
        messages = listOf(dev.indla.island.model.RawMessage("Running late", 2_000, dev.indla.island.model.RawPerson("Arjun", key = "arjun"))),
    )

    @Test
    fun sameApp_stacksIntoOneSlot_swipePagesThroughIt() = test { h ->
        h.stackAndKeep(); runCurrent()
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onPosted(otherChat())
        assertEquals("one slot for the app", 1, h.state.items.size)
        assertEquals(IslandMode.COMPACT, h.state.mode)
        assertEquals("Arjun", h.state.current?.title)
        assertEquals(2, h.state.current?.stack?.size)
        assertEquals(1, h.state.current?.stack?.index)

        h.engine.onGesture(IslandGesture.SWIPE_UP)
        assertEquals("up takes the next card: the older message", "Priya Rao", h.state.current?.title)
        assertEquals(1, h.state.pageTurn)
        h.engine.onGesture(IslandGesture.SWIPE_DOWN)
        assertEquals("down brings the newer one back", "Arjun", h.state.current?.title)
        h.engine.onGesture(IslandGesture.SWIPE_LEFT)
        assertEquals("sideways doesn't turn pages", "Arjun", h.state.current?.title)
    }

    @Test
    fun openingOnePage_keepsTheRestOfTheStack() = test { h ->
        h.stackAndKeep(); runCurrent()
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onPosted(otherChat())
        h.engine.onGesture(IslandGesture.TAP)
        h.engine.onGesture(IslandGesture.TAP) // expanded tap opens the chat
        assertEquals(listOf("open:${otherChat().key}"), h.effects.calls)
        assertEquals("Priya Rao", h.state.current?.title)
        assertEquals(null, h.state.current?.stack)
    }

    @Test
    fun unopened_staysAsMissedPile_untilLookedAt() = test { h ->
        h.stackAndKeep(); runCurrent()
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onPosted(Fixtures.instagramGroupReels())
        advanceTimeBy(5_100); runCurrent()
        assertEquals("both wait in one pile", 1, h.state.items.size)
        val pile = h.state.current?.stack
        assertTrue(pile?.missed == true)
        assertEquals(2, pile?.size)

        h.engine.onGesture(IslandGesture.TAP)
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        assertEquals("starts at the oldest", 0, h.state.current?.stack?.index)
        h.engine.onGesture(IslandGesture.SWIPE_UP)
        assertEquals("up goes forward through the pile", 1, h.state.current?.stack?.index)
        h.engine.onGesture(IslandGesture.SWIPE_UP) // past the end: collapse; both pages were seen
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun postedWhileHidden_waitsInPile_andLeavesWhenRead() = test { h ->
        h.stackAndKeep(); runCurrent()
        h.engine.updateEnvironment(Environment(fullscreen = true))
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertFalse(h.state.visible)
        assertTrue(h.state.current?.stack?.missed == true)
        h.engine.updateEnvironment(Environment(fullscreen = false))
        assertTrue(h.state.visible)
        assertEquals(IslandMode.COMPACT, h.state.mode)

        h.engine.onRemoved(Fixtures.whatsAppDirect().key) // read in the shade or the app
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun media_cardStaysOpenWhileScrubbing() = test { h ->
        h.engine.onMedia(Fixtures.music(playing = true))
        h.engine.onGesture(IslandGesture.TAP)
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        h.engine.onHold(true)
        advanceTimeBy(IslandEngine.EXPANDED_IDLE_MS * 2); runCurrent()
        assertEquals("a finger on the bar keeps the card", IslandMode.EXPANDED, h.state.mode)
        h.engine.onHold(false)
        advanceTimeBy(IslandEngine.EXPANDED_IDLE_MS + 10); runCurrent()
        assertEquals(IslandMode.COMPACT, h.state.mode)
    }

    @Test
    fun media_pausedSessionNobodySawStart_isNotNews() = test { h ->
        h.engine.onMedia(Fixtures.music(playing = false))
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun media_controlsGoToTheSession() = test { h ->
        h.engine.onMedia(Fixtures.music(playing = true))
        h.engine.onGesture(IslandGesture.TAP)
        h.engine.onMediaCommand(MediaCommand.Next)
        assertEquals("media:com.spotify.music:Next", h.effects.calls.single())
    }

    @Test
    fun swipeUpHidesLive_untilItChanges() = test { h ->
        h.engine.onMedia(Fixtures.music(playing = true))
        h.engine.onGesture(IslandGesture.SWIPE_UP)
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.onMedia(Fixtures.music(playing = true).let { it.copy(media = it.media.copy(positionMs = 180_000)) })
        assertEquals("a seek isn't news", IslandMode.REST, h.state.mode)
        h.engine.onMedia(Fixtures.music(playing = true, track = "Sports"))
        assertEquals("a new track is", ItemKind.MEDIA, h.state.current?.kind)
    }

    @Test
    fun liveActivity_isParkedWhileItsAppIsOpen() = test { h ->
        h.engine.onMedia(Fixtures.music(playing = true))
        h.engine.updateEnvironment(Environment(foregroundPackage = "com.spotify.music"))
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.onMedia(Fixtures.music(playing = true, track = "Sports"))
        assertEquals(IslandMode.REST, h.state.mode)
        h.engine.updateEnvironment(Environment(foregroundPackage = "com.android.launcher"))
        assertEquals("Sports", h.state.current?.title)
    }

    @Test
    fun callAnswer_invokesExtrasIntent_andKeepsTheActivity() = test { h ->
        h.engine.onPosted(Fixtures.incomingCallStyle())
        h.engine.onGesture(IslandGesture.TAP)
        h.engine.onActionTapped(dev.indla.island.model.ActionRole.ANSWER_INDEX)
        assertEquals("action:${Fixtures.incomingCallStyle().key}:-1", h.effects.calls.single())
        assertEquals(ItemKind.CALL, h.state.current?.kind)
    }

    @Test
    fun headphones_openBriefly_batteryUpdatesInPlace_thenLeave() = test { h ->
        val card = LiveContent.System(SystemEvent.HEADPHONES_CONNECTED, null, "Buds Pro", device = dev.indla.island.model.AudioKind.EARBUDS)
        h.engine.onSystemEvent(card)
        assertEquals("shown open, like AirPods", IslandMode.EXPANDED, h.state.mode)
        advanceTimeBy(1_000); runCurrent()
        h.engine.onSystemEvent(card.copy(level = 75))
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        assertEquals(75, (h.state.current?.live as LiveContent.System).level)
        advanceTimeBy(IslandEngine.SHOWCASE_MS); runCurrent()
        assertEquals("the card closes into the pill", IslandMode.COMPACT, h.state.mode)
        advanceTimeBy(IslandEngine.COMPACT_TAIL_MS); runCurrent()
        assertEquals("then it leaves", IslandMode.REST, h.state.mode)
    }

    @Test
    fun modeGlance_showsOverTheShade_notificationsDont() = test { h ->
        h.engine.updateEnvironment(Environment(shadeOpen = true))
        h.engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals("a notification waits while the shade is open", IslandMode.REST, h.state.mode)
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.AIRPLANE_ON, null, "On"))
        assertTrue("toggled in quick settings: shown over the shade", h.state.visible)
        assertEquals(IslandMode.EXPANDED, h.state.mode)
    }

    @Test
    fun flashlight_staysUntilOff_withATurnOffButton() = test { h ->
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.FLASHLIGHT_ON, null, "On"))
        assertEquals(IslandMode.EXPANDED, h.state.mode)
        assertEquals(listOf(dev.indla.island.model.ActionRole.TORCH_OFF_INDEX), h.state.current?.actions?.map { it.index })
        advanceTimeBy(60_000); runCurrent()
        assertEquals("still lit, still on the island", SystemEvent.FLASHLIGHT_ON, (h.state.current?.live as LiveContent.System).event)
        h.engine.onActionTapped(dev.indla.island.model.ActionRole.TORCH_OFF_INDEX)
        assertEquals(listOf("action:system:flashlight:-6"), h.effects.calls)
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.FLASHLIGHT_OFF, null, "Off"))
        advanceTimeBy(IslandEngine.SHOWCASE_MS + IslandEngine.COMPACT_TAIL_MS + 100); runCurrent()
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun wellbeingBedtime_becomesAGlance_notACard() = test { h ->
        val bedtime = Fixtures.whatsAppDirect().copy(
            key = "0|com.google.android.apps.wellbeing|1|null|10100",
            packageName = "com.google.android.apps.wellbeing",
            appLabel = "Digital Wellbeing",
            ongoing = true,
            title = "Bedtime mode is on",
            text = "Until 7:00 AM",
            messages = emptyList(),
        )
        h.engine.onPosted(bedtime)
        assertEquals(SystemEvent.BEDTIME_ON, (h.state.current?.live as LiveContent.System).event)
        h.engine.onRemoved(bedtime.key)
        assertEquals(SystemEvent.BEDTIME_OFF, (h.state.current?.live as LiveContent.System).event)
    }

    @Test
    fun bedtime_hidesWhatItSwitches_inEitherOrder() = test { h ->
        // Side effects first, then the cause: they make way for it.
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.RINGER_SILENT, null, "Sounds off"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.DND_ON, null, "Priority only"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.BEDTIME_ON, null, "Until 7:00 AM"))
        assertEquals(listOf(SystemEvent.BEDTIME_ON), h.state.items.map { (it.live as LiveContent.System).event })
        // Then more side effects: swallowed.
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.EYE_ON, null, "On"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.DARK_MODE, null, "On"))
        assertEquals(listOf(SystemEvent.BEDTIME_ON), h.state.items.map { (it.live as LiveContent.System).event })
    }

    @Test
    fun airplane_hidesWifiAndData_butNotLater() = test { h ->
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.AIRPLANE_ON, null, "On"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.WIFI_DISCONNECTED, null, "Disconnected"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.DATA_OFF, null, "Off"))
        assertEquals(listOf(SystemEvent.AIRPLANE_ON), h.state.items.map { (it.live as LiveContent.System).event })
        advanceTimeBy(GlanceCascade.WINDOW_MS + 100); runCurrent()
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.WIFI_CONNECTED, null, "Connected"))
        assertEquals("a later, unrelated change still shows", SystemEvent.WIFI_CONNECTED, (h.state.current?.live as LiveContent.System).event)
    }

    @Test
    fun wifiSwitch_off_replacesTheDisconnect_on_givesWayToTheNetwork() = test { h ->
        // Turning Wi-Fi off drops the network first, then the radio settles off.
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.WIFI_DISCONNECTED, null, "Disconnected"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.WIFI_OFF, null, "Off"))
        assertEquals(listOf(SystemEvent.WIFI_OFF), h.state.items.map { (it.live as LiveContent.System).event })
        advanceTimeBy(GlanceCascade.WINDOW_MS + 100); runCurrent()
        // Turning it on: "On", then the network's name in the same slot.
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.WIFI_ON, null, "On"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.WIFI_CONNECTED, null, "Home"))
        assertEquals(listOf(SystemEvent.WIFI_CONNECTED), h.state.items.map { (it.live as LiveContent.System).event })
    }

    @Test
    fun bluetoothOff_hidesTheEarbudsLeaving_andTwoSourcesMakeOneGlance() = test { h ->
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.BLUETOOTH_OFF, null, "Off"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.HEADPHONES_DISCONNECTED, null, "Buds"))
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.BLUETOOTH_OFF, null, "Off"))
        assertEquals(listOf(SystemEvent.BLUETOOTH_OFF), h.state.items.map { (it.live as LiveContent.System).event })
    }

    @Test
    fun timerPause_keepsTheCardOpen() = test { h ->
        h.engine.onPosted(Fixtures.clockTimer())
        h.engine.onGesture(IslandGesture.TAP)
        h.engine.onActionTapped(0) // Pause
        assertEquals(listOf("action:${Fixtures.clockTimer().key}:0"), h.effects.calls)
        assertEquals("pause shows its effect in place", IslandMode.EXPANDED, h.state.mode)
    }

    @Test
    fun systemEvent_isBrief_andRespectsSettings() = test { h ->
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.CHARGING, 54, "54%"))
        assertEquals(ItemKind.SYSTEM, h.state.current?.kind)
        assertEquals("system glances open by default", IslandMode.EXPANDED, h.state.mode)
        advanceTimeBy(IslandEngine.SHOWCASE_MS + IslandEngine.COMPACT_TAIL_MS + 100); runCurrent()
        assertEquals(IslandMode.REST, h.state.mode)
        h.settings.value = h.settings.value.copy(live = h.settings.value.live.copy(charging = false))
        runCurrent()
        h.engine.onSystemEvent(LiveContent.System(SystemEvent.CHARGING, 55, "55%"))
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun liveKindsCanBeTurnedOff() = test { h ->
        h.settings.value = h.settings.value.copy(live = h.settings.value.live.copy(calls = false, media = false))
        runCurrent()
        h.engine.onPosted(Fixtures.phoneCall())
        h.engine.onMedia(Fixtures.music(playing = true))
        assertEquals(IslandMode.REST, h.state.mode)
    }

    @Test
    fun autoColour_fromAppIcon_unlessRuleSetsOne() = test { h ->
        h.engine.onPosted(Fixtures.whatsAppDirect().copy(appAccentArgb = 0xFF25D366))
        assertEquals(0xFF25D366, h.state.current?.accentArgb)
        h.rules.value = mapOf("com.whatsapp" to AppRule("com.whatsapp", show = true, accentArgb = 0xFFFF0000))
        runCurrent()
        h.engine.onPosted(Fixtures.whatsAppDirect(count = 3).copy(appAccentArgb = 0xFF25D366))
        assertEquals(0xFFFF0000, h.state.current?.accentArgb)
    }

    @Test
    fun postsBeforeRulesLoad_waitForThem() = runTest {
        val rules = kotlinx.coroutines.flow.MutableSharedFlow<Map<String, AppRule>>()
        val engine = IslandEngine(backgroundScope, MutableStateFlow(IslandSettings()), rules, clock = { testScheduler.currentTime })
        runCurrent()
        engine.onPosted(Fixtures.whatsAppDirect())
        assertEquals(IslandMode.REST, engine.state.value.mode)
        rules.emit(mapOf("com.whatsapp" to AppRule("com.whatsapp", show = false)))
        runCurrent()
        assertEquals("the user turned WhatsApp off", IslandMode.REST, engine.state.value.mode)
        backgroundScope.cancel()
    }

    @Test
    fun liveActivities_ignoreTheNotificationSwitch_butMuteBlocksThem() = test { h ->
        // Onboarding used to write show=false for every unticked app; that must not hide music.
        h.rules.value = mapOf(
            "com.spotify.music" to AppRule("com.spotify.music", show = false),
            "com.google.android.deskclock" to AppRule("com.google.android.deskclock", show = false),
        )
        runCurrent()
        h.engine.onMedia(Fixtures.music(playing = true))
        h.engine.onPosted(Fixtures.clockTimer())
        assertEquals(setOf(ItemKind.MEDIA, ItemKind.TIMER), h.state.items.map { it.kind }.toSet())

        h.rules.value = mapOf("com.spotify.music" to AppRule("com.spotify.music", show = false, muted = true))
        runCurrent()
        h.engine.onMedia(Fixtures.music(playing = true, track = "Sports"))
        assertEquals(listOf(ItemKind.TIMER), h.state.items.map { it.kind })
    }

    @Test
    fun timerTicks_updateInPlace() = test { h ->
        h.engine.onPosted(Fixtures.clockTimer())
        h.engine.onPosted(Fixtures.whatsAppDirect())
        h.engine.onPosted(Fixtures.clockTimer().copy(title = "14:03"))
        assertEquals("a tick never steals the island", ItemKind.MESSAGE, h.state.current?.kind)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(ItemKind.TIMER, h.state.current?.kind)
    }
}
