package dev.indla.island.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IslandBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Cold start of the settings app, with the shipped baseline profile. */
    @Test
    fun startup() = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
    }

    /**
     * The plan's smoothness check: expand and collapse the island repeatedly and record frame
     * timing. The overlay draws on the app's UI thread, so FrameTimingMetric covers it.
     * CI compares frameOverrunMs P90 between runs and fails on a regression.
     */
    @Test
    fun expandCollapse() = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            prepareIsland()
            device.findObject(By.text("Message"))?.click()
            Thread.sleep(800)
        },
    ) {
        val (x, y) = islandPoint()
        repeat(10) {
            // Swipe down expands, swipe up collapses: the default gesture map.
            device.swipe(x, y, x, y + 160, 8)
            Thread.sleep(450)
            device.swipe(x, y + 40, x, y - 20, 8)
            Thread.sleep(450)
        }
    }
}
