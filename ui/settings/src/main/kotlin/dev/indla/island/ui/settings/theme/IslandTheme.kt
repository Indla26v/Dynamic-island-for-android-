package dev.indla.island.ui.settings.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Glass tokens. Tint opacity never drops below what keeps text at 4.5:1 on the app background. */
@Immutable
data class GlassColors(
    val tint: Color,
    val tintStrong: Color,
    val edgeStrength: Float,
    val content: Color,
    val contentSecondary: Color,
    val dark: Boolean,
    val solid: Boolean,
)

val LocalGlass = staticCompositionLocalOf {
    GlassColors(Color(0x1FFFFFFF), Color(0x33FFFFFF), 0.5f, Color.White, Color(0xB3FFFFFF), dark = true, solid = false)
}

/**
 * Material You dynamic colour (wallpaper-tinted) with glass tokens on top.
 * @param intensity the "Glass intensity" slider: 0 = clear, 1 = tinted.
 * @param reduceTransparency system setting: glass turns into solid surfaces.
 */
@Composable
fun IslandTheme(intensity: Float, reduceTransparency: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF8AB4F8), secondary = Color(0xFFA8C7FA), tertiary = Color(0xFFD7AEFB))
        else -> lightColorScheme(primary = Color(0xFF0B57D0), secondary = Color(0xFF00639B), tertiary = Color(0xFF8E4EC6))
    }
    val i = intensity.coerceIn(0f, 1f)
    val glass = when {
        reduceTransparency -> GlassColors(
            tint = scheme.surfaceContainerHigh,
            tintStrong = scheme.surfaceContainerHighest,
            edgeStrength = 0f,
            content = scheme.onSurface,
            contentSecondary = scheme.onSurfaceVariant,
            dark = dark,
            solid = true,
        )
        dark -> GlassColors(
            tint = Color.White.copy(alpha = 0.07f + 0.08f * i),
            tintStrong = Color.White.copy(alpha = 0.14f + 0.1f * i),
            edgeStrength = 0.6f,
            content = Color.White,
            contentSecondary = Color.White.copy(alpha = 0.72f),
            dark = true,
            solid = false,
        )
        else -> GlassColors(
            tint = Color.White.copy(alpha = 0.42f + 0.3f * i),
            tintStrong = Color.White.copy(alpha = 0.62f + 0.25f * i),
            edgeStrength = 0.9f,
            content = Color(0xFF111318),
            contentSecondary = Color(0xFF41454F),
            dark = false,
            solid = false,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalGlass provides glass, content = content)
    }
}
