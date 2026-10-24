package dev.indla.island.ui.island

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import dev.indla.island.model.LiveContent
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

val CallGreen = Color(0xFF32D74B)
val DeclineRed = Color(0xFFFF453A)
val RecordingRed = Color(0xFFFF3B30)
val ButtonGrey = Color(0xFF3A3A3C)

/**
 * A fixed "now" (wall clock, ms) for screenshot tests: timers stop ticking and looping
 * animations hold still, so golden images are deterministic. Null in the app.
 */
val LocalFrozenTime = staticCompositionLocalOf<Long?> { null }

/**
 * Animated equaliser bars driven only by play state: no microphone, no audio analysis.
 * The animation is read in the draw phase, so it redraws without recomposing.
 */
@Composable
fun WaveBars(color: Color, playing: Boolean, modifier: Modifier = Modifier, bars: Int = 5) {
    val frozen = LocalFrozenTime.current != null
    val transition = rememberInfiniteTransition(label = "bars")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val phase = if (frozen) 0.3f else animated
        val gap = size.width / (bars * 2f - 1f)
        for (i in 0 until bars) {
            val level = if (playing) {
                0.35f + 0.65f * ((sin((phase * 2 * PI + i * 1.3).toFloat()) + 1f) / 2f) *
                    (0.6f + 0.4f * ((sin((phase * 4 * PI + i * 0.7).toFloat()) + 1f) / 2f))
            } else {
                0.25f
            }
            val h = size.height * level
            drawRoundRect(
                color,
                topLeft = Offset(i * gap * 2f, (size.height - h) / 2f),
                size = Size(gap, h),
                cornerRadius = CornerRadius(gap / 2f),
            )
        }
    }
}

/** A thin ring; null fraction spins as indeterminate. */
@Composable
fun ProgressRing(fraction: Float?, color: Color, modifier: Modifier = Modifier, stroke: Dp = Dp(2.5f)) {
    val frozen = LocalFrozenTime.current != null
    val spin = if (fraction == null) {
        val transition = rememberInfiniteTransition(label = "spin")
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    } else {
        null
    }
    Canvas(modifier) {
        val w = stroke.toPx()
        val arcSize = Size(size.minDimension - w, size.minDimension - w)
        val topLeft = Offset(w / 2, w / 2)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
        if (fraction != null) {
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        } else {
            val angle = if (frozen) 0f else spin!!.value
            drawArc(color, angle, 90f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/**
 * Wall-clock "now", refreshed every [periodMs] while composed. Only call timers and media
 * positions use it, so the island ticks once a second, never per frame.
 */
@Composable
fun rememberNow(periodMs: Long = 1_000): State<Long> {
    val frozen = LocalFrozenTime.current
    return produceState(frozen ?: System.currentTimeMillis(), frozen) {
        if (frozen != null) return@produceState
        while (true) {
            value = System.currentTimeMillis()
            delay(periodMs - value % periodMs)
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Where the track is right now, from the position the session reported and how long ago. */
fun LiveContent.Media.positionNow(): Long {
    if (!playing) return positionMs
    val advanced = positionMs + ((SystemClock.elapsedRealtime() - positionAtElapsedMs) * speed).toLong()
    return if (durationMs > 0) advanced.coerceIn(0, durationMs) else advanced.coerceAtLeast(0)
}

/** "now", "3m", "2h": the relative time on an expanded notification. */
fun relativeTime(postTime: Long, now: Long): String {
    if (postTime <= 0) return "now"
    val minutes = ((now - postTime) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h"
    }
}
