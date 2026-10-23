package dev.indla.island.ui.island

import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandUiState
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
        IslandMode.EXPANDED -> expanded(expandedHeight(state))
    }

    fun rest() = IslandRect(clampLeft(restWidth), top, restWidth, restHeight, radius(restHeight))

    fun compact() = IslandRect(clampLeft(compactWidth), top, compactWidth, restHeight, radius(restHeight))

    fun expanded(height: Float) = IslandRect(clampLeft(expandedWidth), top, expandedWidth, height, EXPANDED_RADIUS)

    /** The split-state bubble: beside the compact pill, on whichever side has room. */
    fun bubble(): IslandRect {
        val pill = compact()
        val size = restHeight
        val right = pill.right + BUBBLE_GAP
        val left = if (right + size <= screenWidth - EDGE) right else pill.left - BUBBLE_GAP - size
        return IslandRect(left, top, size, size, size / 2)
    }

    /** Header height of the expanded card: the row the camera sits in. */
    val headerHeight get() = max(restHeight, 40f)

    fun expandedHeight(state: IslandUiState): Float {
        val item = state.current ?: return headerHeight + PADDING_BOTTOM
        var h = PADDING_TOP + headerHeight
        h += when {
            state.quickActionsOpen -> GAP + BUTTON_ROW
            state.replyOpen -> item.lines.size.coerceAtMost(1) * LINE + GAP + REPLY_ROW
            else -> item.lines.size.coerceAtMost(3) * LINE + if (item.actions.isNotEmpty()) GAP + BUTTON_ROW else 0f
        }
        return h + PADDING_BOTTOM
    }

    /** Camera centre measured from a shape's left edge. */
    fun cameraOffsetIn(rect: IslandRect) = (if (kind == CutoutKind.NONE) centerX else hole.left + hole.width / 2) - rect.left

    private fun radius(height: Float) = height / 2

    private fun clampLeft(width: Float): Float {
        val left = centerX - width / 2
        return left.coerceIn(EDGE, max(EDGE, screenWidth - EDGE - width))
    }

    companion object {
        const val EXPANDED_RADIUS = 30f
        const val EDGE = 6f
        const val BUBBLE_GAP = 6f
        const val PADDING_TOP = 4f
        const val PADDING_BOTTOM = 14f
        const val LINE = 46f
        const val GAP = 8f
        const val BUTTON_ROW = 40f
        const val REPLY_ROW = 48f

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

            val side = (if (appearance.preset == StylePreset.MINIMAL) restHeight + 6f else 60f) * scale
            return IslandLayout(
                kind = kind,
                screenWidth = screenWidth,
                centerX = (centerX + appearance.offsetXDp).coerceIn(0f, screenWidth),
                top = top,
                restWidth = restWidth,
                restHeight = restHeight,
                hole = hole,
                compactSide = side,
                expandedWidth = min(screenWidth - 2 * EDGE - 4f, 420f),
                attachedToTop = attached,
                minimal = appearance.preset == StylePreset.MINIMAL,
            )
        }
    }
}
