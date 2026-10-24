package dev.indla.island.service.overlay

import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.annotation.RequiresApi
import java.util.function.Consumer

/**
 * Real backdrop blur behind the expanded card. A view added straight to WindowManager can't
 * blur what's behind it, but a `Window` can (`setBackgroundBlurRadius`), and an accessibility
 * service may open a Dialog window of TYPE_ACCESSIBILITY_OVERLAY. The window is created first,
 * so it sits under the island's render window, and is only ever moved once per state change.
 *
 * Unsupported devices, battery saver and "disable window blurs" keep the frosted-tint fallback.
 */
internal class BlurWindow(private val context: Context, private val onSupportChanged: (Boolean) -> Unit) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var dialog: Dialog? = null
    private val background = GradientDrawable().apply { setColor(0x14000000) }
    private var listener: Consumer<Boolean>? = null

    var supported: Boolean = false
        private set

    fun create() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        create31()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun create31() {
        supported = windowManager.isCrossWindowBlurEnabled
        val l = Consumer<Boolean> { enabled ->
            supported = enabled
            if (!enabled) hide()
            onSupportChanged(enabled)
        }
        windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, l)
        listener = l
        val d = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        d.setContentView(View(context))
        d.setCancelable(false)
        d.window?.run {
            setType(LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            setFormat(PixelFormat.TRANSLUCENT)
            clearFlags(LayoutParams.FLAG_DIM_BEHIND)
            addFlags(
                LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            setBackgroundDrawable(background)
            setWindowAnimations(android.R.style.Animation_Toast)
            attributes = attributes.apply {
                gravity = Gravity.TOP or Gravity.START
                width = 1
                height = 1
                alpha = 0f
                layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
                title = "Island blur"
            }
        }
        runCatching { d.show() }.onFailure { return }
        dialog = d
    }

    /** Blur exactly the card's settled bounds, rounded like the card. */
    fun show(x: Int, y: Int, width: Int, height: Int, cornerPx: Float, blurPx: Int) {
        if (!supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val window = dialog?.window ?: return
        // DecorView copies GradientDrawable.getCornerRadius() into the blur region, which is 0
        // for per-corner radii, so use a single radius. On an attached (square-topped) card the
        // two top corners get tint without blur, flush with the screen edge where it barely shows.
        background.cornerRadius = cornerPx
        // The blur region copies the background drawable's shape, but only when the drawable is
        // (re)set: changing the radii in place leaves the old square region behind.
        window.setBackgroundDrawable(null)
        window.setBackgroundDrawable(background)
        window.setBackgroundBlurRadius(blurPx)
        window.attributes = window.attributes.apply {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            alpha = 1f
        }
    }

    fun hide() {
        val window = dialog?.window ?: return
        if (window.attributes.alpha == 0f) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setBackgroundBlurRadius(0)
        window.attributes = window.attributes.apply {
            alpha = 0f
            width = 1
            height = 1
        }
    }

    fun destroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listener?.let { windowManager.removeCrossWindowBlurEnabledListener(it) }
        }
        runCatching { dialog?.dismiss() }
        dialog = null
    }
}
