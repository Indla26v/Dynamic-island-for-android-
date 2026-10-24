package dev.indla.island.benchmark

import android.hardware.display.DisplayManager
import android.view.Display
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val TARGET = "dev.indla.island.bench"

/**
 * Test-device setup: on a CI emulator nobody can tap through the permission screens, so the
 * benchmark grants Island's two services from the shell (test devices only, never a phone).
 */
fun MacrobenchmarkScope.prepareIsland() {
    shell("cmd notification allow_listener $TARGET/dev.indla.island.service.listener.IslandNotificationListener")
    shell("settings put secure enabled_accessibility_services $TARGET/dev.indla.island.service.overlay.IslandAccessibilityService")
    shell("pm grant $TARGET android.permission.POST_NOTIFICATIONS")
    pressHome()
    startActivityAndWait()
    // A fresh install opens onboarding; with both services on, Finish is enabled.
    device.wait(Until.findObject(By.text("Finish")), 3_000)?.click()
    device.wait(Until.hasObject(By.text("Try it")), 5_000)
}

/** Where the island sits: the top cutout's centre, or the middle of the status bar. */
fun islandPoint(): Pair<Int, Int> {
    val context = InstrumentationRegistry.getInstrumentation().context
    val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    val rect = display.cutout?.boundingRects?.minByOrNull { it.top }
    val width = context.resources.displayMetrics.widthPixels
    return if (rect != null) rect.centerX() to rect.centerY() else width / 2 to (24 * context.resources.displayMetrics.density).toInt()
}

private fun MacrobenchmarkScope.shell(command: String) {
    device.executeShellCommand(command)
}
