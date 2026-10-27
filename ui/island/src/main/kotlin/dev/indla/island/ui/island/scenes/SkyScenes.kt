package dev.indla.island.ui.island.scenes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DayTop = Color(0xFF3FA9FF)
private val DayLow = Color(0xFF9EDBFF)
private val NightTop = Color(0xFF15124A)
private val NightLow = Color(0xFF3B2C8F)
private val Sun = Color(0xFFFFD60A)
private val MoonGlow = Color(0xFFF1EEFF)

/**
 * Dark and light mode as a day–night dial. Behind a little window the sky turns on a wheel: the
 * sun sinks off the right as the moon swings up from the left (or the reverse), the sky floods
 * from blue to indigo, stars twinkle in over the hills. Light mode ends with the sun's rays.
 */
@Composable
internal fun DayNightScene(night: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(2_300)
    Canvas(modifier) {
        val turn = p.segment(0.05f, 0.8f)
        val dark = if (night) turn else 1f - turn
        val win = RoundRect(Rect(0f, size.height * 0.1f, size.width, size.height * 0.9f), CornerRadius(8.dp.toPx()))
        val clip = Path().apply { addRoundRect(win) }
        clipPath(clip) {
            drawRect(Brush.verticalGradient(listOf(mix(DayTop, NightTop, dark), mix(DayLow, NightLow, dark)), win.top, win.bottom))
            // Stars, twinkling in as it darkens.
            val stars = listOf(Offset(0.12f, 0.3f), Offset(0.3f, 0.55f), Offset(0.5f, 0.24f), Offset(0.68f, 0.45f), Offset(0.88f, 0.28f))
            stars.forEachIndexed { i, at ->
                val a = dark * (0.55f + 0.45f * sin((p * 9 + i * 1.3f) * PI).toFloat().let { if (p >= 1f) 1f else it })
                if (a > 0.05f) sparkle(Offset(size.width * at.x, size.height * at.y), 2.2.dp.toPx() * (0.7f + 0.3f * (i % 2)), Color.White.copy(alpha = a.coerceIn(0f, 1f)))
            }
            // The wheel: sun and moon opposite each other on a circle centred below the window.
            val hub = Offset(size.width / 2, win.bottom + size.height * 0.45f)
            val r = size.height * 0.95f
            val sunAngle = -90.0 + 180.0 * dark
            val sunAt = hub + Offset(cos(Math.toRadians(sunAngle)).toFloat(), sin(Math.toRadians(sunAngle)).toFloat()) * r
            val moonAt = hub - (sunAt - hub)
            glow(Sun, sunAt, 13.dp.toPx(), 0.55f)
            drawCircle(Sun, 6.dp.toPx(), sunAt)
            if (!night) {
                val rays = p.segment(0.75f, 1f)
                for (k in 0 until 8) {
                    val a = k * PI / 4 + rays * 0.4
                    val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                    drawLine(Sun.copy(alpha = rays), sunAt + d * 8.5.dp.toPx(), sunAt + d * (8.5f + 3.5f * rays).dp.toPx(), 1.6.dp.toPx(), StrokeCap.Round)
                }
            }
            glow(MoonGlow, moonAt, 12.dp.toPx(), 0.4f * dark)
            crescent(moonAt, 6.dp.toPx(), MoonGlow)
            // Rolling hills along the bottom, in the glance's colour.
            val hills = Path().apply {
                moveTo(0f, win.bottom)
                lineTo(0f, win.bottom - size.height * 0.16f)
                quadraticTo(size.width * 0.25f, win.bottom - size.height * 0.36f, size.width * 0.5f, win.bottom - size.height * 0.16f)
                quadraticTo(size.width * 0.75f, win.bottom - size.height * 0.02f, size.width, win.bottom - size.height * 0.24f)
                lineTo(size.width, win.bottom)
                close()
            }
            drawPath(hills, mix(color, Color.Black, 0.35f + 0.3f * dark))
        }
        drawRoundRect(color, Offset(win.left, win.top), Size(win.width, win.height), CornerRadius(8.dp.toPx()), style = Stroke(1.4.dp.toPx()))
    }
}

/** A four-pointed twinkle. */
private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    drawLine(color, c - Offset(r, 0f), c + Offset(r, 0f), r * 0.45f, StrokeCap.Round)
    drawLine(color, c - Offset(0f, r), c + Offset(0f, r), r * 0.45f, StrokeCap.Round)
}

/** A crescent moon of radius [r], lit on its left. */
internal fun DrawScope.crescent(c: Offset, r: Float, color: Color) {
    val disc = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    val bite = Path().apply { addOval(Rect(c.x - r * 0.45f, c.y - r * 1.15f, c.x + r * 1.55f, c.y + r * 0.85f)) }
    drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, color)
}

/**
 * Do Not Disturb on: the bell rings once, then a crescent moon sweeps in front of it like an
 * eclipse; the bell's waves are swallowed and it fades behind the moon. Off: the moon slides away
 * and the bell rings out again.
 */
@Composable
internal fun DndScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    val bell = rememberVectorPainter(Icons.Rounded.Notifications)
    Canvas(modifier) {
        val s = 24.dp.toPx()
        val bc = Offset(size.width - s / 2 - 8.dp.toPx(), size.height / 2)
        // Where the moon is on its sweep: 0 off to the upper left, 1 over the bell.
        val cover = if (on) overshoot(p.segment(0.35f, 0.75f), 0.12f) else 1f - p.segment(0f, 0.45f)
        val ringT = if (on) p.segment(0f, 0.4f, LinearEasing) else p.segment(0.4f, 0.9f, LinearEasing)
        val swing = sin(ringT * 4 * PI).toFloat() * (1f - ringT) * 22f
        val waves = if (on) (1f - p.segment(0.35f, 0.6f)) * (if (ringT > 0f) 1f else 0f) else p.segment(0.45f, 0.75f)
        for (i in 0 until 2) {
            val r = s * (0.7f + 0.28f * i)
            if (waves > 0.02f) {
                drawArc(color.copy(alpha = waves * 0.9f), -40f, 80f, false, bc - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color.copy(alpha = waves * 0.9f), 140f, 80f, false, bc - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        rotate(swing, pivot = bc - Offset(0f, s * 0.42f)) {
            translate(bc.x - s / 2, bc.y - s / 2) {
                with(bell) { draw(Size(s, s), alpha = 1f - 0.75f * cover.coerceIn(0f, 1f), colorFilter = ColorFilter.tint(color)) }
            }
        }
        if (cover > 0.01f) {
            val from = bc + Offset(-s * 1.6f, -s * 0.9f)
            val at = from + (bc - from) * cover
            val shown = cover.coerceIn(0f, 1f)
            glow(color, at, s * 1.1f, 0.7f * shown)
            crescent(at + Offset(-s * 0.08f, 0f), s * 0.56f, Color.Black)
            crescent(at, s * 0.52f, mix(color, Color.White, 0.3f).copy(alpha = shown))
            // Two stars around the eclipse.
            val twinkle = p.segment(0.7f, 1f)
            if (on && twinkle > 0f) {
                sparkle(bc + Offset(-s * 0.85f, -s * 0.45f), 2.dp.toPx(), color.copy(alpha = twinkle))
                sparkle(bc + Offset(-s * 0.55f, s * 0.5f), 1.5.dp.toPx(), color.copy(alpha = twinkle * 0.8f))
            }
        }
    }
}

/**
 * Bedtime on: a crescent moon swings in on a string like a mobile over a cot, settles, and stars
 * blink on around it while a "z" drifts up. Off: the sun climbs over a hill and its rays open.
 */
@Composable
internal fun BedtimeScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(2_100)
    Canvas(modifier) {
        if (on) {
            val pivot = Offset(size.width - 26.dp.toPx(), 0f)
            val len = size.height * 0.62f
            val enter = p.segment(0f, 0.3f)
            val settle = p.segment(0.25f, 0.85f, LinearEasing)
            val angle = (-70f * (1f - enter)) + 28f * sin(settle * 3 * PI).toFloat() * (1f - settle) * enter
            val tip = pivot + Offset(sin(Math.toRadians(angle.toDouble())).toFloat() * -len, cos(Math.toRadians(angle.toDouble())).toFloat() * len)
            drawLine(color.copy(alpha = 0.7f), pivot, tip, 1.2.dp.toPx())
            glow(color, tip, 14.dp.toPx(), 0.45f)
            crescent(tip, 8.dp.toPx(), color)
            val stars = listOf(Offset(-30f, 10f), Offset(-46f, 28f), Offset(14f, 30f), Offset(-14f, 6f))
            stars.forEachIndexed { i, d ->
                val blink = p.segment(0.45f + 0.1f * i, 0.6f + 0.1f * i)
                if (blink > 0f) sparkle(pivot + Offset(d.x.dp.toPx(), d.y.dp.toPx()), (1.6f + 0.6f * (i % 2)).dp.toPx() * overshoot(blink, 0.5f), Color.White.copy(alpha = blink))
            }
            val z = p.segment(0.6f, 1f)
            if (z > 0f) {
                val zc = tip + Offset(-14.dp.toPx(), -6.dp.toPx() - 6.dp.toPx() * z)
                val zs = 3.5.dp.toPx()
                val zPath = Path().apply {
                    moveTo(zc.x - zs, zc.y - zs)
                    lineTo(zc.x + zs, zc.y - zs)
                    lineTo(zc.x - zs, zc.y + zs)
                    lineTo(zc.x + zs, zc.y + zs)
                }
                drawPath(zPath, color.copy(alpha = z), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            }
        } else {
            val ground = size.height * 0.86f
            val sc = Offset(size.width - 34.dp.toPx(), ground - (size.height * 0.5f) * p.segment(0f, 0.6f))
            val rays = p.segment(0.55f, 1f)
            glow(Sun, sc, 16.dp.toPx(), 0.5f)
            for (k in 0 until 8) {
                val a = -PI + k * PI / 7
                val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(Sun.copy(alpha = rays), sc + d * 10.dp.toPx(), sc + d * (10f + 5f * rays).dp.toPx(), 1.8.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(Sun, 7.dp.toPx(), sc)
            // The hill, in front of the sun.
            val hill = Path().apply {
                moveTo(size.width * 0.15f, size.height)
                quadraticTo(size.width * 0.62f, ground - size.height * 0.42f, size.width, size.height * 0.92f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(hill, Color.Black)
            drawPath(hill, color.copy(alpha = 0.35f))
            drawPath(hill, color, style = Stroke(1.6.dp.toPx()))
        }
    }
}
