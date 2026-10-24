package dev.indla.island.service.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 90/120 Hz screens run the island's window at the panel's top rate only while it moves, and
 * leave the choice to the system the rest of the time, so a resting island costs nothing extra.
 *
 * @param apply sets the window's preferred refresh rate (0 = no preference).
 */
internal class RefreshRateBooster(
    context: Context,
    private val scope: CoroutineScope,
    private val apply: (Float) -> Unit,
) {
    private val max: Float = context.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f
    private var current = 0f
    private var job: Job? = null

    /** Top rate for the next [BOOST_MS]; calls within that time extend it. */
    fun boost() {
        if (max <= 61f) return
        set(max)
        job?.cancel()
        job = scope.launch {
            delay(BOOST_MS)
            set(0f)
        }
    }

    private fun set(rate: Float) {
        if (rate == current) return
        current = rate
        apply(rate)
    }

    private companion object {
        /** Long enough for the morph spring and the content fade to finish. */
        const val BOOST_MS = 900L
    }
}
