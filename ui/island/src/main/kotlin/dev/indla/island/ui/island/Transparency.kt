package dev.indla.island.ui.island

import android.content.Context
import android.provider.Settings

/**
 * Honours the system's "reduce transparency" style settings: OEM reduce-transparency switches,
 * disabled window blurs, and high-contrast text all switch glass to solid surfaces.
 */
object Transparency {
    fun reduced(context: Context): Boolean {
        val cr = context.contentResolver
        fun secure(name: String) = runCatching { Settings.Secure.getInt(cr, name, 0) == 1 }.getOrDefault(false)
        fun global(name: String) = runCatching { Settings.Global.getInt(cr, name, 0) == 1 }.getOrDefault(false)
        return secure("accessibility_reduce_transparency") ||
            secure("high_text_contrast_enabled") ||
            global("disable_window_blurs")
    }
}
