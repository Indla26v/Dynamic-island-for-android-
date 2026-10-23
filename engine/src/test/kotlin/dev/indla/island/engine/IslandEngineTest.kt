package dev.indla.island.engine

import dev.indla.island.model.AppRule
import dev.indla.island.model.Environment
import dev.indla.island.model.IslandGesture
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.ItemKind
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
    }

    private class Harness(scope: TestScope) {
        val settings = MutableStateFlow(IslandSettings(autoHideSeconds = 5))
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

        h.settings.value = h.settings.value.copy(whenToShow = WhenToShow(hideInLandscape = false))
        runCurrent()
        h.engine.updateEnvironment(Environment(landscape = true))
        assertTrue(h.state.visible)
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
}
