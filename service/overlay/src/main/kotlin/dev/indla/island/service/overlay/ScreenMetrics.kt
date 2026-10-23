package dev.indla.island.service.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.DisplayCutout
import android.view.WindowManager
import dev.indla.island.ui.island.CutoutDp

/** Screen size and top cutout in dp, read once per configuration change. */
data class ScreenMetrics(
    val density: Float,
    val widthDp: Float,
    val heightDp: Float,
    val statusBarDp: Float,
    val cutout: CutoutDp?,
    val landscape: Boolean,
) {
    companion object {
        fun read(context: Context, windowManager: WindowManager): ScreenMetrics {
            val density = context.resources.displayMetrics.density
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.maximumWindowMetrics.bounds
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(dm)
                Rect(0, 0, dm.widthPixels, dm.heightPixels)
            }
            val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            // Only a cutout along the top edge hosts the island; side cutouts (landscape) don't.
            val cutout = display?.cutout
            val top = cutout?.boundingRects
                ?.filter { !it.isEmpty && it.top < bounds.height() / 4 && it.height() < bounds.height() / 4 }
                ?.minByOrNull { it.top }
                ?.let { camera(it, cutout) }
            val statusBarId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarPx = if (statusBarId != 0) context.resources.getDimensionPixelSize(statusBarId) else (24 * density).toInt()
            return ScreenMetrics(
                density = density,
                widthDp = bounds.width() / density,
                heightDp = bounds.height() / density,
                statusBarDp = statusBarPx / density,
                cutout = top?.let { CutoutDp(it.left / density, it.top / density, it.right / density, it.bottom / density) },
                landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            )
        }

        /**
         * The camera itself, not the cutout's bounding rect: many devices report a punch hole's
         * bounds from the top edge down, which would make the island far too tall. The cutout
         * path (API 31+) is exact; below that, a tall narrow rect is read as a hole at its bottom.
         */
        private fun camera(bounding: Rect, cutout: DisplayCutout): RectF {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val path = cutout.cutoutPath
                if (path != null) {
                    val exact = RectF().also { path.computeBounds(it, true) }
                    if (!exact.isEmpty && RectF(bounding).contains(exact)) return exact
                }
            }
            val w = bounding.width().toFloat()
            return if (bounding.top == 0 && bounding.height() > w * 1.1f) {
                RectF(bounding.left.toFloat(), bounding.bottom - w, bounding.right.toFloat(), bounding.bottom.toFloat())
            } else {
                RectF(bounding)
            }
        }
    }
}
