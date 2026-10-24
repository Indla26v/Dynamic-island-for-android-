package dev.indla.island.ui.island

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import dev.indla.island.model.LiveContent
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

val CallGreen = Color(0xFF32D74B)
val DeclineRed = Color(0xFFFF453A)
val RecordingRed = Color(0xFFFF3B30)
val ButtonGrey = Color(0xFF3A3A3C)
val TimerOrange = Color(0xFFFF9F0A)

/** Island's own package: its demo items never show the Island logo. */
fun isIslandPackage(packageName: String) = packageName.startsWith("dev.indla.island")

/** Time left on a countdown, or elapsed on a stopwatch; the app's own text while paused. */
fun LiveContent.Timer.display(now: Long): String {
    val base = baseMs ?: return staticTime
    val ms = if (countDown) base - now else now - base
    return if (ms < 0) "-" + formatDuration(-ms) else formatDuration(ms)
}

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
    // Bars this small read as smooth at 30 fps; ticking at the display's 90-120 Hz all the time
    // music plays is the island's biggest battery cost. Paused bars don't tick at all.
    val animated by produceState(0f, playing, frozen) {
        if (!playing || frozen) return@produceState
        val start = withFrameMillis { it }
        while (true) {
            value = ((withFrameMillis { it } - start) % WAVE_PERIOD_MS) / WAVE_PERIOD_MS.toFloat()
            delay(WAVE_FRAME_MS)
        }
    }
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

private const val WAVE_PERIOD_MS = 900L
private const val WAVE_FRAME_MS = 33L

/** A thin ring; null fraction spins as indeterminate. */
@Composable
fun ProgressRing(fraction: Float?, color: Color, modifier: Modifier = Modifier, stroke: Dp = Dp(2.5f)) {
    val frozen = LocalFrozenTime.current != null
    // Progress glides between updates instead of jumping.
    val smooth = fraction?.let { animateFloatAsState(it, tween(500), label = "ring").value }
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
            drawArc(color, -90f, 360f * (smooth ?: fraction).coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
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

val RecordRed = Color(0xFFFF3B30)

// ---- Per-activity motion ------------------------------------------------------------------------
// Every kind of island moves its own way: music bounces as an equaliser, a call breathes as a
// voice wave, an incoming call rings, a recording scrolls its waveform past a red playhead, a
// timer ticks, battery rings fill up from empty, and ringer icons ring, buzz or fall quiet.
// Continuous ones tick at ~30 fps (small marks look smooth there) and only while they run.

/** Wall-clock ms, refreshed at ~30 fps while [running]; frozen in screenshot tests. */
@Composable
fun rememberTicker(running: Boolean): State<Long> {
    val frozen = LocalFrozenTime.current
    return produceState(frozen ?: System.currentTimeMillis(), running, frozen) {
        if (!running || frozen != null) return@produceState
        while (true) {
            withFrameMillis { }
            value = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
}

/**
 * 0 to 1 once, when first shown: rings fill and numbers count up from empty. Screenshots get
 * the finished state.
 */
@Composable
fun rememberReveal(durationMs: Int = 900): Float {
    val frozen = LocalFrozenTime.current != null
    val reveal = remember { Animatable(if (frozen) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    return reveal.value
}

/** A spring from [from] to [to], once, after [delayMs]. */
@Composable
fun rememberSpringIn(from: Float, to: Float, dampingRatio: Float, stiffness: Float, delayMs: Long = 0): Float {
    val frozen = LocalFrozenTime.current != null
    val value = remember { Animatable(if (frozen) to else from) }
    LaunchedEffect(Unit) {
        if (frozen) return@LaunchedEffect
        delay(delayMs)
        value.animateTo(to, spring(dampingRatio = dampingRatio, stiffness = stiffness))
    }
    return value.value
}

/** Like the Recorder app: recorded bars scroll left past a red playhead; ahead of it, dots. */
@Composable
fun RecordingWave(startedAtMs: Long?, modifier: Modifier, playhead: Float = 0.7f, bar: Dp = Dp(2f), step: Dp = Dp(4f)) {
    val now by rememberTicker(startedAtMs != null)
    Canvas(modifier) {
        val w = bar.toPx()
        val gap = step.toPx()
        val head = size.width * playhead
        val elapsed = (now - (startedAtMs ?: now)).coerceAtLeast(0)
        val samples = elapsed / SAMPLE_MS
        val frac = (elapsed % SAMPLE_MS) / SAMPLE_MS.toFloat()
        var i = 0
        while (true) {
            val x = head - (i + frac) * gap
            val index = samples - i
            if (x < -w || index < 0) break
            val h = size.height * amplitude(index)
            drawRoundRect(Color.White.copy(alpha = 0.62f), Offset(x - w / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
            i++
        }
        var x = head + gap * (1 - frac)
        while (x < size.width) {
            drawCircle(Color.White.copy(alpha = 0.28f), w * 0.6f, Offset(x, size.height / 2))
            x += gap
        }
        drawLine(RecordRed, Offset(head, 0f), Offset(head, size.height), strokeWidth = Dp(1.5f).toPx())
        drawCircle(RecordRed, w * 1.4f, Offset(head, w * 1.4f))
    }
}

/** A red dot that breathes while recording and holds still when paused. */
@Composable
fun RecordDot(recording: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val now by rememberTicker(recording)
    Canvas(modifier.size(size)) {
        val t = (now % 1_400) / 1_400f
        val pulse = if (recording) 0.5f + 0.5f * kotlin.math.cos(t * 2 * PI).toFloat() else 1f
        drawCircle(RecordRed.copy(alpha = 0.25f * pulse), this.size.minDimension / 2)
        drawCircle(RecordRed.copy(alpha = if (recording) 0.65f + 0.35f * pulse else 0.5f), this.size.minDimension * 0.3f)
    }
}

/** A call's voice: a smooth wave travelling through symmetric bars, unlike music's bounce. */
@Composable
fun VoiceWave(color: Color, modifier: Modifier = Modifier, bars: Int = 6) {
    val now by rememberTicker(true)
    Canvas(modifier) {
        val gap = size.width / (bars * 2f - 1f)
        val t = (now % 1_200) / 1_200f
        for (i in 0 until bars) {
            val centre = 1f - kotlin.math.abs(i - (bars - 1) / 2f) / bars
            val wave = (sin((t * 2 * PI - i * 0.8).toFloat()) + 1f) / 2f
            val h = size.height * (0.25f + 0.75f * wave * centre)
            drawRoundRect(color, Offset(i * gap * 2f, (size.height - h) / 2f), Size(gap, h), CornerRadius(gap / 2f))
        }
    }
}

/** Rotation (degrees) of a ringing phone: three quick shakes, then a pause. */
@Composable
fun ringingAngle(): Float {
    val now by rememberTicker(true)
    val t = now % 1_500
    return if (t < 600) 16f * sin(t / 600f * 6 * PI).toFloat() * (1f - t / 600f) else 0f
}

/** Scale of a timer icon: a small tick on every second. */
@Composable
fun tickScale(running: Boolean): Float {
    val now by rememberTicker(running)
    if (!running) return 1f
    val t = (now % 1_000) / 180f
    return 1f + 0.14f * (1f - t).coerceIn(0f, 1f)
}

/** Rings, buzzes or quiets once when shown: rotation for a bell, offset for vibration. */
@Composable
fun rememberShake(): Float {
    val p = rememberReveal(1_100)
    return sin(p * 8 * PI).toFloat() * (1f - p)
}

private fun amplitude(i: Long): Float {
    fun noise(x: Double) = (sin(x) * 43758.5453).let { it - kotlin.math.floor(it) }.toFloat()
    val fine = noise(i * 12.9898)
    val coarse = noise((i / 4) * 78.233)
    return 0.16f + 0.84f * (0.45f * fine + 0.55f * coarse)
}

private const val TICK_MS = 33L
private const val SAMPLE_MS = 100L
