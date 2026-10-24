package dev.indla.island.service.listener

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** App labels and a vivid accent picked from each app icon, computed once per package. */
@Singleton
class AppInfoCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val labels = HashMap<String, String>()
    private val accents = HashMap<String, Long?>()

    fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    /**
     * The icon's most vivid colour, lightened until it reads on the black island, or null for
     * icons with no real colour (so the user's accent is used instead).
     */
    fun accent(pkg: String): Long? = accents.getOrPut(pkg) {
        runCatching {
            val bitmap = context.packageManager.getApplicationIcon(pkg).toBitmap(48, 48)
            val palette = Palette.from(bitmap).clearFilters().generate()
            val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            swatch?.rgb?.let(::readableOnBlack)
        }.getOrNull()
    }

    private fun readableOnBlack(rgb: Int): Long? {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        if (hsl[1] < 0.25f) return null
        // At least 4.5:1 against black means relative luminance above ~0.18.
        while (ColorUtils.calculateLuminance(ColorUtils.HSLToColor(hsl)) < 0.18 && hsl[2] < 0.95f) hsl[2] += 0.05f
        return (ColorUtils.HSLToColor(hsl) or Color.BLACK).toLong() and 0xFFFFFFFFL
    }
}
