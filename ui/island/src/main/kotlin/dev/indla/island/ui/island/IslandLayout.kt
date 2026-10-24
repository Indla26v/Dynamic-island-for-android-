package dev.indla.island.ui.island

import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandItem
import dev.indla.island.model.IslandUiState
import dev.indla.island.model.ItemKind
import dev.indla.island.model.StylePreset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A cutout bounding rect in dp, screen coordinates. */
data class CutoutDp(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
}

enum class CutoutKind { CENTER_PUNCH, CORNER_PUNCH, NOTCH, NONE }

/** One shape the island can take, in dp. Every state change springs between two of these. */
data class IslandRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
) {
    val right get() = left + width
    val bottom get() = top + height
}

/**
 * Where the island sits and how big each state is, auto-aligned to the cutout. Everything is dp;
 * the overlay converts to px once per state change.
 */
data class IslandLayout(
    val kind: CutoutKind,
    val screenWidth: Float,
    val centerX: Float,
    val top: Float,
    val restWidth: Float,
    val restHeight: Float,
    /** The camera itself: what the island shrinks into when the rest pill is off. */
    val hole: IslandRect,
    val compactSide: Float,
    val expandedWidth: Float,
    val attachedToTop: Boolean,
    val minimal: Boolean,
) {
    val compactWidth get() = restWidth + 2 * compactSide

    /** Horizontal gap kept free for the camera inside wide shapes. */
    val cameraGap get() = if (kind == CutoutKind.NONE) 0f else max(hole.width + 12f, restWidth * 0.55f)

    fun rectFor(state: IslandUiState): IslandRect = when (state.mode) {
        IslandMode.REST -> if (state.showsRestPill) rest() else hole
        IslandMode.COMPACT, IslandMode.SPLIT -> compact()
        IslandMode.EXPANDED -> expandedRect(state)
    }

    /** The expanded card for [state]: as tall as its content and as wide as it needs. */
    fun expandedRect(state: IslandUiState) = expanded(expandedHeight(state), expandedWidthFor(state))

    fun rest() = IslandRect(clampLeft(restWidth), top, restWidth, restHeight, radius(restHeight))

    fun compact() = IslandRect(clampLeft(compactWidth), top, compactWidth, restHeight, radius(restHeight))

    fun expanded(height: Float, width: Float = expandedWidth) = IslandRect(clampLeft(width), top, width, height, EXPANDED_RADIUS)

    /**
     * Size follows content: a glance (a mode, the headphones, an unlock) gets a narrower card,
     * centred on the camera so it grows out of it evenly on both sides; messages, music and
     * calls use the full width.
     */
    fun expandedWidthFor(state: IslandUiState): Float {
        val item = state.current ?: return expandedWidth
        val width = when {
            state.quickActionsOpen || state.replyOpen -> expandedWidth
            item.kind == ItemKind.SYSTEM -> GLANCE_WIDTH
            else -> expandedWidth
        }
        return width.coerceAtMost(expandedWidth)
    }

    /** Bubble right of the pill (the second item); falls back to the left when there's no room. */
    fun bubble(): IslandRect {
        val pill = compact()
        val right = pill.right + BUBBLE_GAP
        val left = if (right + restHeight <= screenWidth - EDGE) right else pill.left - BUBBLE_GAP - restHeight
        return IslandRect(left, top, restHeight, restHeight, restHeight / 2)
    }

    /** Bubble left of the pill (the third item), so the island can show more at once. */
    fun bubbleLeft(): IslandRect {
        val pill = compact()
        val other = bubble()
        var left = pill.left - BUBBLE_GAP - restHeight
        if (other.left < pill.left || left < EDGE) left = other.right + BUBBLE_GAP
        return IslandRect(left, top, restHeight, restHeight, restHeight / 2)
    }

    /**
     * The expanded card's top band, level with the camera. It carries only small accessories
     * (app name, time, waveform) left and right of the camera; the body starts below it.
     */
    val bandHeight: Float get() = when (kind) {
        CutoutKind.NONE -> BAND_MIN
        else -> max(BAND_MIN, hole.bottom - top + 4f)
    }

    /**
     * A system glance without buttons is a slim bar: its words left of the camera and its scene
     * right of it, all in one row level with the camera, iOS style.
     */
    fun isSlimGlance(state: IslandUiState): Boolean {
        val item = state.current ?: return false
        // Buttons (the torch's off switch) sit inline at the bar's end, so every glance is slim.
        return item.kind == ItemKind.SYSTEM && !state.quickActionsOpen
    }

    fun expandedHeight(state: IslandUiState): Float {
        val item = state.current ?: return bandHeight + PADDING_BOTTOM
        if (isSlimGlance(state)) return max(bandHeight, GLANCE_ROW) + 2 * GLANCE_PAD
        val body = when {
            state.quickActionsOpen -> AVATAR + GAP + BUTTON_ROW
            item.kind == ItemKind.CALL -> CALL_ROW
            item.kind == ItemKind.TIMER -> CALL_ROW + GAP + CONTROL_ROW
            item.kind == ItemKind.MEDIA -> MEDIA_ART + GAP + SCRUBBER + MEDIA_CONTROLS
            item.kind == ItemKind.SYSTEM -> SYSTEM_ROW + actionsHeight(item)
            item.kind == ItemKind.PROGRESS -> PROGRESS_BODY + actionsHeight(item)
            state.replyOpen -> notificationBody(minOf(item.lines.size, 1)) + GAP + REPLY_ROW
            else -> notificationBody(item.lines.size.coerceAtMost(3)) + actionsHeight(item)
        }
        return bandHeight + body + PADDING_BOTTOM
    }

    /** Title plus one 20 dp row per line, beside a 40 dp avatar. */
    fun notificationBody(lines: Int) = max(AVATAR, TEXT_ROW * (1 + lines))

    private fun actionsHeight(item: IslandItem) = if (item.actions.isEmpty()) 0f else GAP + BUTTON_ROW

    /** Camera centre measured from a shape's left edge. */
    fun cameraOffsetIn(rect: IslandRect) = (if (kind == CutoutKind.NONE) centerX else hole.left + hole.width / 2) - rect.left

    private fun radius(height: Float) = height / 2

    private fun clampLeft(width: Float): Float {
        val left = centerX - width / 2
        return left.coerceIn(EDGE, max(EDGE, screenWidth - EDGE - width))
    }

    companion object {
        const val EXPANDED_RADIUS = 34f
        /** Expanded width for system glances. */
        const val GLANCE_WIDTH = 380f
        /** The slim glance bar's content row, and the space above and below it. */
        const val GLANCE_ROW = 44f
        const val GLANCE_PAD = 7f
        const val EDGE = 6f
        const val BUBBLE_GAP = 6f
        const val BAND_MIN = 26f
        const val PADDING_BOTTOM = 16f
        const val GAP = 10f
        const val AVATAR = 40f
        const val TEXT_ROW = 20f
        const val BUTTON_ROW = 36f
        const val REPLY_ROW = 44f
        const val CALL_ROW = 52f
        const val MEDIA_ART = 56f
        const val SCRUBBER = 24f
        const val MEDIA_CONTROLS = 48f
        /** Round play/pause and stop buttons under a timer or recording. */
        const val CONTROL_ROW = 52f
        const val PROGRESS_BODY = 64f
        const val SYSTEM_ROW = 48f

        /**
         * @param cutout the top cutout's bounding rect, or null on devices without one.
         * @param statusBarHeight dp; used to centre the island when there is no cutout.
         */
        fun compute(
            cutout: CutoutDp?,
            screenWidth: Float,
            statusBarHeight: Float,
            appearance: IslandAppearance,
            screenHeight: Float = 800f,
        ): IslandLayout {
            val kind = when {
                cutout == null || cutout.width <= 0f || cutout.top > screenHeight / 3 -> CutoutKind.NONE
                cutout.width > 64f && cutout.top <= 1f -> CutoutKind.NOTCH
                abs(cutout.centerX - screenWidth / 2) < 24f -> CutoutKind.CENTER_PUNCH
                else -> CutoutKind.CORNER_PUNCH
            }
            val scale = appearance.sizeScale.coerceIn(0.8f, 1.3f) *
                if (appearance.preset == StylePreset.MINIMAL) 0.92f else 1f
            val sb = statusBarHeight.coerceAtLeast(24f)

            var restHeight: Float
            var restWidth: Float
            var top: Float
            val centerX: Float
            val hole: IslandRect
            when (kind) {
                CutoutKind.NONE -> {
                    restHeight = (sb - 4f).coerceIn(24f, 34f) * scale
                    restWidth = restHeight * 3.2f
                    top = ((sb - restHeight) / 2).coerceAtLeast(2f)
                    centerX = screenWidth / 2
                    hole = IslandRect(centerX, top + restHeight / 2, 0f, 0f, 0f)
                }
                CutoutKind.NOTCH -> {
                    restHeight = cutout!!.height * scale
                    restWidth = cutout.width * scale
                    top = 0f
                    centerX = cutout.centerX
                    hole = IslandRect(cutout.left, 0f, cutout.width, cutout.height, cutout.height / 2)
                }
                else -> {
                    val c = cutout!!
                    restHeight = max(c.height + 6f, 28f) * scale
                    restWidth = max(restHeight * 2.5f, c.width + 28f)
                    top = (c.centerY - restHeight / 2).coerceAtLeast(2f)
                    centerX = c.centerX
                    hole = IslandRect(c.left, c.top, c.width, c.height, min(c.width, c.height) / 2)
                }
            }

            val attached = appearance.preset == StylePreset.NOTCH_ATTACHED || kind == CutoutKind.NOTCH
            if (attached && kind != CutoutKind.NOTCH) {
                // Hang from the top edge: the shape keeps its bottom where it was.
                restHeight += top
                top = 0f
            }
            top = (top + appearance.offsetYDp).coerceAtLeast(0f)

            val side = (if (appearance.preset == StylePreset.MINIMAL) restHeight + 6f else 50f) * scale
            return IslandLayout(
                kind = kind,
                screenWidth = screenWidth,
                centerX = (centerX + appearance.offsetXDp).coerceIn(0f, screenWidth),
                top = top,
                restWidth = restWidth,
                restHeight = restHeight,
                hole = hole,
                compactSide = side,
                // Already near full width at 100%, so only smaller sizes narrow the card.
                expandedWidth = min(screenWidth - 2 * EDGE - 4f, 420f) * appearance.sizeScale.coerceIn(0.8f, 1f).let { 0.2f + 0.8f * it },
                attachedToTop = attached,
                minimal = appearance.preset == StylePreset.MINIMAL,
            )
        }
    }
}
