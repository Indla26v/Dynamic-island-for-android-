package dev.indla.island.ui.island.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.indla.island.model.AudioKind
import dev.indla.island.ui.island.LocalFrozenTime
import dev.indla.island.ui.island.rememberTicker
import kotlinx.coroutines.delay

/** What a scene needs to know about its glance. */
internal data class SceneArgs(
    val level: Int?,
    val color: Color,
    val label: String,
    val device: AudioKind = AudioKind.OTHER,
    /** Unlock glances: the face did it rather than a fingerprint. */
    val face: Boolean = false,
)

/**
 * One clock per scene, 0 to 1, linear, played once; every beat of the scene is a [segment] of
 * it. Screenshot tests (frozen time) get the last frame.
 */
@Composable
internal fun rememberTimeline(durationMs: Int): Float {
    val frozen = LocalFrozenTime.current != null
    val t = remember { Animatable(if (frozen) 1f else 0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    return t.value
}

/** This beat's own 0..1, eased, for the part of the timeline between [from] and [to]. */
internal fun Float.segment(from: Float, to: Float, easing: Easing = FastOutSlowInEasing): Float =
    easing.transform(((this - from) / (to - from)).coerceIn(0f, 1f))

/** A spring-like overshoot over 0..1 that settles at 1 (for snaps and pops). */
internal fun overshoot(x: Float, amount: Float = 0.18f): Float =
    if (x >= 1f) 1f else x + amount * kotlin.math.sin(x * kotlin.math.PI).toFloat()

/**
 * Wall-clock ms since the scene appeared, ticking only for [forMs] (bubbles, packets), then
 * still: scenes play once, never loop.
 */
@Composable
internal fun rememberSceneClock(forMs: Long = FLOW_MS): Long {
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(forMs)
        running = false
    }
    val start = remember { System.currentTimeMillis() }
    val now by rememberTicker(running)
    return (now - start).coerceAtLeast(0)
}

/** Flows end a little before the glance closes (3.5 s), so the last frame is seen at rest. */
internal const val FLOW_MS = 3_000L

/** Draws the first [fraction] of [path], for things that trace themselves in. */
internal fun DrawScope.tracePath(path: Path, fraction: Float, color: Color, width: Float) {
    if (fraction <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), part, true)
    drawPath(part, color, style = Stroke(width, cap = StrokeCap.Round))
}

internal fun DrawScope.battery(x: Float, y: Float, w: Float, h: Float, fraction: Float, color: Color, outlineAlpha: Float = 1f) {
    val stroke = 1.6.dp.toPx()
    val r = h * 0.3f
    drawRoundRect(color.copy(alpha = 0.9f * outlineAlpha), Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(stroke))
    drawRoundRect(color.copy(alpha = 0.9f * outlineAlpha), Offset(x + w + stroke, y + h * 0.3f), Size(stroke * 1.6f, h * 0.4f), CornerRadius(stroke))
    val inset = stroke * 1.8f
    val fill = (w - 2 * inset) * fraction.coerceIn(0f, 1f)
    if (fill > 0f) drawRoundRect(color, Offset(x + inset, y + inset), Size(fill, h - 2 * inset), CornerRadius(r * 0.6f))
}

/** A small lightning bolt centred on [c]. */
internal fun DrawScope.bolt(c: Offset, h: Float, color: Color = Color.White) {
    val w = h * 0.55f
    val path = Path().apply {
        moveTo(c.x + w * 0.1f, c.y - h / 2)
        lineTo(c.x - w * 0.45f, c.y + h * 0.08f)
        lineTo(c.x - w * 0.02f, c.y + h * 0.08f)
        lineTo(c.x - w * 0.12f, c.y + h / 2)
        lineTo(c.x + w * 0.45f, c.y - h * 0.1f)
        lineTo(c.x + w * 0.02f, c.y - h * 0.1f)
        close()
    }
    drawPath(path, color)
}

/** A point on the quadratic curve a -> b bent toward c. */
internal fun quad(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1 - t
    return Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
}

/** Linear blend of two colours. */
internal fun mix(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, a.alpha + (b.alpha - a.alpha) * k)
}

/** A gauge: a 180° arc with a needle at [value] (0 = left / slow, 1 = right / fast). */
internal fun DrawScope.gauge(center: Offset, radius: Float, value: Float, arc: Color, needle: Color, redline: Color? = null) {
    val stroke = radius * 0.18f
    val box = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    drawArc(arc.copy(alpha = 0.35f), 180f, 180f, false, box.topLeft, box.size, style = Stroke(stroke, cap = StrokeCap.Round))
    if (redline != null) drawArc(redline, 315f, 45f, false, box.topLeft, box.size, style = Stroke(stroke, cap = StrokeCap.Round))
    val angle = Math.toRadians(180.0 + 180.0 * value.coerceIn(-0.05f, 1.08f))
    val tip = Offset(center.x + (radius * 0.85f) * kotlin.math.cos(angle).toFloat(), center.y + (radius * 0.85f) * kotlin.math.sin(angle).toFloat())
    drawLine(needle, center, tip, stroke * 0.7f, StrokeCap.Round)
    drawCircle(needle, stroke * 0.8f, center)
}
