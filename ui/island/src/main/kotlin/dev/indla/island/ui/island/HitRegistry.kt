package dev.indla.island.ui.island

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
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
        const val REPLY_ROW = "reply"
        const val QUICK_MUTE = "quick:mute"
        const val QUICK_DISMISS = "quick:dismiss"
        fun action(index: Int) = "action:$index"
        fun actionIndex(id: String): Int? = id.removePrefix("action:").takeIf { id.startsWith("action:") }?.toIntOrNull()
    }
}

/** Registers this element's bounds under [id] while it is composed. */
fun Modifier.hitRegion(registry: HitRegistry?, id: String): Modifier = if (registry == null) this else composed {
    DisposableEffect(registry, id) { onDispose { registry.remove(id) } }
    onGloballyPositioned { registry.put(id, it.boundsInWindow()) }
}
