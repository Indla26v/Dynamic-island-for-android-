package dev.indla.island.ui.island

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Interactive spots drawn by the (untouchable) render window, in render-window px. The hit
 * window maps its touches into this space to find what was tapped.
 */
class HitRegistry {
    private val regions = mutableStateMapOf<String, Rect>()

    /**
     * The music bar position under the finger (0..1) while it is dragged, then held after release
     * until the player reports the new position, so the thumb never snaps back. The hit window
     * writes it; the render window draws it.
     */
    var scrub by mutableStateOf<Float?>(null)
        internal set
    var scrubbing by mutableStateOf(false)
        internal set
    /** elapsedRealtime of the last release; positions reported before it are stale. */
    var scrubReleasedAt = 0L
        internal set

    internal fun clearScrub() {
        if (!scrubbing) scrub = null
    }

    operator fun get(id: String): Rect? = regions[id]

    fun find(point: Offset): String? = regions.entries
        .filter { it.value.contains(point) }
        .minByOrNull { it.value.width * it.value.height }
        ?.key

    internal fun put(id: String, rect: Rect) {
        regions[id] = rect
    }

    internal fun remove(id: String) {
        regions.remove(id)
    }

    companion object {
        const val BUBBLE = "bubble"
        const val BUBBLE_LEFT = "bubble:left"
        const val MEDIA_PREVIOUS = "media:previous"
        const val MEDIA_PLAY_PAUSE = "media:playpause"
        const val MEDIA_NEXT = "media:next"
        const val MEDIA_SEEK = "media:seek"
        const val REPLY_ROW = "reply"
        const val QUICK_MUTE = "quick:mute"
        const val QUICK_DISMISS = "quick:dismiss"
        /** Clears the panel deck's card from the phone's notification panel. */
        const val PANEL_CLEAR = "panel:clear"
        /** Not a region of its own: a tap here opens the item's app, like tapping the card. */
        const val OPEN_APP = "open"
        fun action(index: Int) = "action:$index"
        fun actionIndex(id: String): Int? = id.removePrefix("action:").takeIf { id.startsWith("action:") }?.toIntOrNull()
    }
}

/** Registers this element's bounds under [id] while it is composed. */
fun Modifier.hitRegion(registry: HitRegistry?, id: String): Modifier = if (registry == null) this else composed {
    DisposableEffect(registry, id) { onDispose { registry.remove(id) } }
    onGloballyPositioned { registry.put(id, it.boundsInWindow()) }
}
