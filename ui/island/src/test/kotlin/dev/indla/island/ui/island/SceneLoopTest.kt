package dev.indla.island.ui.island

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Glances play once and hold their last frame. A ticker that runs for as long as the glance is
 * shown (`rememberTicker(true)`) would loop and cost battery, so no scene or glyph may start one;
 * bounded clocks (`rememberSceneClock`, `rememberOnceClock`) are the way to animate over time.
 */
class SceneLoopTest {
    @Test
    fun noGlanceTicksForever() {
        val root = File("src/main/kotlin/dev/indla/island/ui/island")
        val files = root.resolve("scenes").listFiles().orEmpty().toList() + root.resolve("SystemGlyphs.kt")
        assertTrue("scene sources not found from ${File(".").absolutePath}", files.isNotEmpty())
        val offenders = files.filter { "rememberTicker(true)" in it.readText() }.map { it.name }
        assertTrue("these glances loop forever: $offenders", offenders.isEmpty())
    }
}
