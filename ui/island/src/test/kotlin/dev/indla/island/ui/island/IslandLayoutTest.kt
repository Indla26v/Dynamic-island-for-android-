package dev.indla.island.ui.island

import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandItem
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandUiState
import dev.indla.island.model.ItemKind
import dev.indla.island.model.MessageLine
import dev.indla.island.model.StylePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandLayoutTest {
    private val width = 412f
    private val statusBar = 40f

    private fun layout(cutout: CutoutDp?, appearance: IslandAppearance = IslandAppearance()) =
        IslandLayout.compute(cutout, width, statusBar, appearance)

    @Test
    fun centrePunchHole_centresOnTheCamera() {
        val l = layout(CutoutDp(194f, 10f, 218f, 34f))
        assertEquals(CutoutKind.CENTER_PUNCH, l.kind)
        assertEquals(206f, l.centerX, 0.01f)
        val rest = l.rest()
        assertEquals(22f, rest.top + rest.height / 2, 0.01f) // vertically on the hole
        assertTrue(rest.width > 24f)
    }

    @Test
    fun cornerPunchHole_isOffCentre_andStaysOnScreen() {
        val l = layout(CutoutDp(30f, 12f, 52f, 34f))
        assertEquals(CutoutKind.CORNER_PUNCH, l.kind)
        assertEquals(41f, l.centerX, 0.01f)
        val compact = l.compact()
        assertTrue(compact.left >= IslandLayout.EDGE)
        val card = l.expanded(200f)
        assertTrue(card.right <= width - IslandLayout.EDGE + 0.01f)
    }

    @Test
    fun teardropNotch_attachesToTopEdge() {
        val l = layout(CutoutDp(156f, 0f, 256f, 30f))
        assertEquals(CutoutKind.NOTCH, l.kind)
        assertTrue(l.attachedToTop)
        assertEquals(0f, l.top, 0.01f)
        assertEquals(100f, l.restWidth, 0.01f)
    }

    @Test
    fun noCutout_fallsBackToStatusBarCentre() {
        val l = layout(null)
        assertEquals(CutoutKind.NONE, l.kind)
        assertEquals(width / 2, l.centerX, 0.01f)
        assertTrue(l.top + l.restHeight <= statusBar)
    }

    @Test
    fun offsetsAreDp_andNotchAttachedPresetHangsFromTop() {
        val base = layout(CutoutDp(194f, 10f, 218f, 34f))
        val moved = layout(CutoutDp(194f, 10f, 218f, 34f), IslandAppearance(offsetXDp = 12f, offsetYDp = 3f))
        assertEquals(base.centerX + 12f, moved.centerX, 0.01f)
        assertEquals(base.top + 3f, moved.top, 0.01f)

        val attached = layout(CutoutDp(194f, 10f, 218f, 34f), IslandAppearance(preset = StylePreset.NOTCH_ATTACHED))
        assertEquals(0f, attached.top, 0.01f)
        assertEquals(base.top + base.restHeight, attached.restHeight, 0.01f)
    }

    @Test
    fun hiddenRest_shrinksIntoTheCamera() {
        val l = layout(CutoutDp(194f, 10f, 218f, 34f))
        val rect = l.rectFor(IslandUiState(mode = IslandMode.REST))
        assertEquals(l.hole, rect)
    }

    @Test
    fun expandedHeight_growsWithLinesAndActions() {
        val l = layout(CutoutDp(194f, 10f, 218f, 34f))
        fun state(lines: Int) = IslandUiState(
            mode = IslandMode.EXPANDED,
            items = listOf(
                IslandItem(
                    key = "k", packageName = "p", appLabel = "App", kind = ItemKind.MESSAGE, title = "t", glance = "g",
                    lines = List(lines) { MessageLine("s", "m$it") }, actions = emptyList(), avatarKey = null,
                    postTime = 0, groupKey = null, isGroupSummary = false,
                ),
            ),
        )
        assertEquals(IslandLayout.TEXT_ROW * 2, l.expandedHeight(state(3)) - l.expandedHeight(state(1)), 0.01f)
    }
}
