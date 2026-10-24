package dev.indla.island.ui.island

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.indla.island.model.ActionRole
import dev.indla.island.model.CallState
import dev.indla.island.model.IslandAction
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandItem
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandUiState
import dev.indla.island.model.ItemKind
import dev.indla.island.model.LiveContent
import dev.indla.island.model.MessageLine
import dev.indla.island.model.SystemEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every island state, short and long text, RTL and dark wallpaper, rendered on the JVM.
 * Golden images live in ui/island/src/test/snapshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class IslandScreenshotTest {
    private companion object {
        /** Every shot is taken at the same instant: 24 Oct 2026, 09:41. */
        const val NOW = 1_792_820_460_000L
    }

    private val layout = IslandLayout.compute(CutoutDp(194f, 17f, 218f, 41f), 412f, 40f, IslandAppearance())

    private fun item(
        kind: ItemKind = ItemKind.MESSAGE,
        title: String = "Weekend plans",
        glance: String = "3 new",
        lines: List<MessageLine> = listOf(MessageLine("Meera", "Where are we going?"), MessageLine("Arpit + 2", "sent 3 reels", count = 3)),
        actions: List<IslandAction> = listOf(IslandAction(0, "Reply", true), IslandAction(1, "Mark as read", false)),
        live: LiveContent? = null,
        key: String = "k",
    ) = IslandItem(
        key = key, packageName = "dev.indla.island", appLabel = "Island", kind = kind, title = title, glance = glance,
        lines = lines, actions = actions, avatarKey = null, postTime = NOW, groupKey = null,
        isGroupSummary = false, live = live,
    )

    private val longText = item(
        title = "A very long group conversation name that cannot fit",
        lines = listOf(MessageLine("Somebody with a long name", "This message is far too long to fit on one line of the island card")),
    )
    private val incoming = item(
        ItemKind.CALL, "Tania Castillo", "Incoming", listOf(MessageLine(null, "Mobile")),
        listOf(IslandAction(-2, "Decline", false, ActionRole.DECLINE), IslandAction(-1, "Answer", false, ActionRole.ANSWER)),
        LiveContent.Call("Tania Castillo", CallState.INCOMING, 0), key = "call",
    )
    private val ongoing = incoming.copy(
        glance = "",
        actions = listOf(IslandAction(-3, "Hang up", false, ActionRole.HANG_UP)),
        live = LiveContent.Call("Tania Castillo", CallState.ONGOING, NOW - 8_000),
    )
    private val music = item(
        ItemKind.MEDIA, "Entropy", "", listOf(MessageLine(null, "Beach Bunny")), emptyList(),
        LiveContent.Media("Entropy", "Beach Bunny", true, 211_000, 170_000, 0, 0f, null, true, true, true), key = "media",
    )
    private val download = item(
        ItemKind.PROGRESS, "island-demo.apk", "45%", listOf(MessageLine(null, "45% · 2 min left")), emptyList(),
        LiveContent.Progress(0.45f, "45% · 2 min left"), key = "dl",
    )
    private val charging = item(
        ItemKind.SYSTEM, "54%", "54%", emptyList(), emptyList(), LiveContent.System(SystemEvent.CHARGING, 54, "54%"), key = "sys",
    )

    private fun shot(name: String, state: IslandUiState, rtl: Boolean = false, dark: Boolean = false) {
        captureRoboImage("src/test/snapshots/$name.png") { Frame(state, rtl, dark) }
    }

    @Composable
    private fun Frame(state: IslandUiState, rtl: Boolean, dark: Boolean) {
        val wallpaper = if (dark) listOf(Color(0xFF1B1B2F), Color(0xFF3A2E5C)) else listOf(Color(0xFFB8C6FF), Color(0xFFF3D9FF))
        CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalFrozenTime provides NOW,
        ) {
            Box(Modifier.size(412.dp, 280.dp).background(Brush.verticalGradient(wallpaper))) {
                IslandSurface(state, layout)
            }
        }
    }

    private fun state(mode: IslandMode, vararg items: IslandItem, restPill: Boolean = false) =
        IslandUiState(mode = mode, items = items.toList(), appearance = IslandAppearance(showRestPill = restPill))

    @Test fun rest() = shot("rest", state(IslandMode.REST, restPill = true))
    @Test fun compactMessage() = shot("compact_message", state(IslandMode.COMPACT, item()))
    @Test fun expandedMessage() = shot("expanded_message", state(IslandMode.EXPANDED, item()))
    @Test fun expandedMessageLong() = shot("expanded_message_long", state(IslandMode.EXPANDED, longText))
    @Test fun expandedMessageRtl() = shot("expanded_message_rtl", state(IslandMode.EXPANDED, item()), rtl = true)
    @Test fun expandedMessageDark() = shot("expanded_message_dark", state(IslandMode.EXPANDED, item()), dark = true)
    @Test fun compactRtl() = shot("compact_rtl", state(IslandMode.COMPACT, item()), rtl = true)
    @Test fun incomingCompact() = shot("call_incoming_compact", state(IslandMode.COMPACT, incoming))
    @Test fun incomingExpanded() = shot("call_incoming_expanded", state(IslandMode.EXPANDED, incoming))
    @Test fun ongoingCompact() = shot("call_ongoing_compact", state(IslandMode.COMPACT, ongoing))
    @Test fun mediaExpanded() = shot("media_expanded", state(IslandMode.EXPANDED, music))
    @Test fun progressExpanded() = shot("progress_expanded", state(IslandMode.EXPANDED, download))
    @Test fun chargingCompact() = shot("system_charging_compact", state(IslandMode.COMPACT, charging))
    @Test fun splitThree() = shot("split_three", state(IslandMode.SPLIT, music, ongoing, download))
    /** A system glance, opened: its scene's last frame (frozen time ends every scene). */
    private fun glance(event: SystemEvent, label: String, level: Int? = null, device: dev.indla.island.model.AudioKind = dev.indla.island.model.AudioKind.OTHER) =
        state(IslandMode.EXPANDED, item(ItemKind.SYSTEM, label, label, emptyList(), emptyList(), LiveContent.System(event, level, label, device), key = "sys"))

    @Test fun sceneCharging() = shot("scene_charging", glance(SystemEvent.CHARGING, "62%", 62))
    @Test fun sceneDark() = shot("scene_dark", glance(SystemEvent.DARK_MODE, "On"))
    @Test fun sceneLight() = shot("scene_light", glance(SystemEvent.LIGHT_MODE, "On"))
    @Test fun sceneAirplane() = shot("scene_airplane", glance(SystemEvent.AIRPLANE_ON, "On"))
    @Test fun sceneDnd() = shot("scene_dnd", glance(SystemEvent.DND_ON, "Priority only"))
    @Test fun sceneUnlock() = shot("scene_unlock", glance(SystemEvent.UNLOCKED, "Fingerprint", 0))
    @Test fun sceneSpeaker() = shot("scene_speaker", glance(SystemEvent.HEADPHONES_DISCONNECTED, "Island Boom", device = dev.indla.island.model.AudioKind.SPEAKER))

    @Test fun hiddenByAndroid() = shot(
        "hidden_by_android",
        state(IslandMode.EXPANDED, item(ItemKind.HIDDEN_BY_ANDROID, "Messages", "Hidden by Android", listOf(MessageLine(null, "Hidden by Android")), emptyList())),
    )
}
