package dev.indla.island.service.overlay

import android.util.Log
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FrameStats(val frames: Int = 0, val janky: Int = 0, val worstMs: Float = 0f)

/**
 * JankStats needs an Activity `Window`; the overlay has none. This watches Choreographer only
 * while the island is animating (a state change plus a settle window), counts frames that took
 * longer than 1.5x the refresh interval, and then stops, so it costs nothing at rest.
 */
object OverlayFrameStats {
    private val _stats = MutableStateFlow(FrameStats())
    val stats: StateFlow<FrameStats> = _stats.asStateFlow()

    private var lastFrameNanos = 0L
    private var stopAtNanos = 0L
    private var running = false
    private var refreshNanos = 16_666_667L

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameNanos != 0L) {
                val delta = frameTimeNanos - lastFrameNanos
                val janky = delta > refreshNanos * 3 / 2
                val ms = delta / 1_000_000f
                _stats.value = _stats.value.let {
                    FrameStats(it.frames + 1, it.janky + if (janky) 1 else 0, maxOf(it.worstMs, ms))
                }
                if (janky) Log.d("IslandJank", "Janky overlay frame: %.1f ms".format(ms))
            }
            lastFrameNanos = frameTimeNanos
            if (frameTimeNanos < stopAtNanos) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                running = false
                lastFrameNanos = 0L
            }
        }
    }

    /** Call on the main thread whenever the island changes state. */
    fun track(refreshRateHz: Float, durationMs: Long = 700) {
        refreshNanos = (1_000_000_000 / refreshRateHz.coerceAtLeast(30f)).toLong()
        stopAtNanos = System.nanoTime() + durationMs * 1_000_000
        if (!running) {
            running = true
            Choreographer.getInstance().postFrameCallback(callback)
        }
    }

    fun reset() {
        _stats.value = FrameStats()
    }
}
