package dev.indla.island.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile shipped in app/src/main/baseline-prof.txt: startup, the three
 * tabs, and the island morphing through its states, so first runs aren't JIT-janky.
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=dev.indla.island.benchmark.BaselineProfileGenerator
 * then copy the generated *-baseline-prof.txt over app/src/main/baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET, includeInStartupProfile = true) {
        prepareIsland()
        for (tab in listOf("Apps", "Look & feel", "Home")) {
            device.findObject(By.desc(tab))?.click() ?: device.findObject(By.text(tab))?.click()
            device.wait(Until.hasObject(By.text(tab)), 2_000)
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
        }
        device.findObject(By.text("Message"))?.click()
        Thread.sleep(800)
        val (x, y) = islandPoint()
        repeat(3) {
            device.swipe(x, y, x, y + 160, 8)
            Thread.sleep(500)
            device.swipe(x, y + 40, x, y - 20, 8)
            Thread.sleep(500)
        }
    }
}
