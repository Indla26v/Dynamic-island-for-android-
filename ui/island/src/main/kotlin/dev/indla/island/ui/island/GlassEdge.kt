package dev.indla.island.ui.island

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * The "liquid" edge highlight: a specular rim brighter where light from the top-left would hit,
 * with a faint refracted glint on the opposite edge. AGSL on Android 13+, gradient stroke below.
 *
 * Holds one compiled shader; [brush] only updates uniforms, so it is cheap to call every frame.
 */
class GlassEdge {
    private val runtime: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Runtime() else null

    fun brush(size: Size, strength: Float): Brush =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (runtime as Runtime).brush(size, strength)
        } else {
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.55f * strength),
                0.45f to Color.White.copy(alpha = 0.12f * strength),
                1f to Color.White.copy(alpha = 0.28f * strength),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class Runtime {
        private val shader = RuntimeShader(AGSL)
        private val brush = ShaderBrush(shader)

        fun brush(size: Size, strength: Float): Brush {
            shader.setFloatUniform("size", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            shader.setFloatUniform("strength", strength)
            return brush
        }
    }

    private companion object {
        const val AGSL = """
            uniform float2 size;
            uniform float strength;
            half4 main(float2 p) {
                float2 c = p / size - 0.5;
                float len = max(length(c), 0.0001);
                float d = dot(c / len, normalize(float2(-0.55, -0.85)));
                float rim = 0.22 + 0.78 * pow(max(d, 0.0), 2.2);
                float glint = 0.35 * pow(max(-d, 0.0), 6.0);
                float a = clamp((rim + glint) * strength, 0.0, 1.0);
                return half4(a, a, a, a);
            }
        """
    }
}
