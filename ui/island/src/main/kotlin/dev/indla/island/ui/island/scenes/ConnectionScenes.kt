package dev.indla.island.ui.island.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Connectivity scenes. Each is drawn in its glance's own colour, on and off alike: "off" is told
// by what moves (things peeling away, unravelling, landing), never by turning grey. They sit at
// the bar's right edge, so each one's last frame leans right.

/**
 * Wi-Fi on: three arcs fall in from above like a ripple running backwards and land on the dot
 * one by one, each with a bounce and a flash. Joining a network pops a tick badge on top. Off: the
 * arcs peel away outward and come back as dotted outlines, and the dot opens into a ring.
 */
@Composable
internal fun WifiScene(on: Boolean, joined: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    Canvas(modifier) {
        val unit = size.height * 0.17f
        val s = Offset(size.width - unit * 3.4f, size.height * 0.84f)
        val stroke = unit * 0.5f
        val dot = if (on) overshoot(p.segment(0f, 0.15f), 0.4f) else 1f
        for (i in 0 until 3) {
            val target = unit * (i + 1.15f)
            if (on) {
                val land = p.segment(0.12f + 0.16f * i, 0.4f + 0.16f * i)
                if (land <= 0f) continue
                val r = target * (1f + 1.1f * (1f - overshoot(land, 0.25f)))
                wifiArc(s, r, color.copy(alpha = land), stroke)
                val flash = p.segment(0.4f + 0.16f * i, 0.55f + 0.16f * i, androidx.compose.animation.core.LinearEasing)
                if (flash in 0.01f..0.99f) wifiArc(s, target, Color.White.copy(alpha = 1f - flash), stroke * 0.6f)
            } else {
                // Outermost first.
                val peel = p.segment(0.05f + 0.15f * (2 - i), 0.4f + 0.15f * (2 - i))
                if (peel < 1f) wifiArc(s, target * (1f + 0.7f * peel), color.copy(alpha = 1f - peel), stroke)
                val ghost = p.segment(0.45f + 0.12f * (2 - i), 0.75f + 0.12f * (2 - i))
                if (ghost > 0f) wifiArc(s, target, color.copy(alpha = 0.55f * ghost), stroke * 0.45f, dotted = true)
            }
        }
        if (on) {
            glow(color, s, unit * 1.6f * dot, 0.5f)
            drawCircle(color, unit * 0.5f * dot, s)
        } else {
            val open = p.segment(0.3f, 0.7f)
            drawCircle(color, unit * 0.5f, s, style = if (open > 0f) Stroke(unit * 0.5f * (1f - 0.6f * open)) else androidx.compose.ui.graphics.drawscope.Fill)
        }
        if (on && joined) {
            val pop = p.segment(0.78f, 1f)
            if (pop > 0f) {
                val at = s + Offset(unit * 2.6f, -unit * 3.4f)
                val r = unit * 0.95f * overshoot(pop, 0.35f)
                drawCircle(color, r, at)
                val tick = Path().apply {
                    moveTo(at.x - r * 0.45f, at.y)
                    lineTo(at.x - r * 0.1f, at.y + r * 0.35f)
                    lineTo(at.x + r * 0.5f, at.y - r * 0.35f)
                }
                tracePath(tick, p.segment(0.88f, 1f), Color.White, r * 0.28f)
            }
        }
    }
}

private fun DrawScope.wifiArc(c: Offset, r: Float, color: Color, width: Float, dotted: Boolean = false) {
    drawArc(
        color, 225f, 90f, false, c - Offset(r, r), Size(2 * r, 2 * r),
        style = Stroke(
            width,
            cap = StrokeCap.Round,
            pathEffect = if (dotted) PathEffect.dashPathEffect(floatArrayOf(0.1f, width * 2.4f)) else null,
        ),
    )
}

/**
 * Bluetooth on: the rune draws itself as one neon stroke led by a travelling spark, then an echo
 * pulses out either side once and leaves a pair of small arcs. Off: the spark runs backwards and
 * unravels the rune into a short dash, which pulls into one glowing dot.
 */
@Composable
internal fun BluetoothScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_800)
    Canvas(modifier) {
        val h = size.height * 0.34f
        val w = h * 0.5f
        val c = Offset(size.width - h * 1.9f, size.height / 2)
        val rune = Path().apply {
            moveTo(c.x - w, c.y - h * 0.5f)
            lineTo(c.x + w, c.y + h * 0.5f)
            lineTo(c.x, c.y + h)
            lineTo(c.x, c.y - h)
            lineTo(c.x + w, c.y - h * 0.5f)
            lineTo(c.x - w, c.y + h * 0.5f)
        }
        val stroke = 2.4.dp.toPx()
        if (on) {
            val drawn = p.segment(0f, 0.55f, androidx.compose.animation.core.LinearEasing)
            glow(color, c, h * 1.6f * drawn, 0.35f)
            tracePath(rune, drawn, color, stroke)
            if (drawn in 0.01f..0.99f) spark(color, pathPoint(rune, drawn), stroke * 0.8f)
            val echo = p.segment(0.55f, 1f)
            for (side in listOf(-1f, 1f)) {
                for (k in 0 until 2) {
                    val r = h * (1.15f + 0.45f * k) + h * 0.8f * echo
                    val a = if (echo < 1f) (1f - echo) * 0.9f else 0f
                    if (a > 0f) sideArc(c, r, side, color.copy(alpha = a), stroke * 0.8f)
                }
                // What stays: one small pair of arcs.
                val stay = p.segment(0.75f, 1f)
                if (stay > 0f) sideArc(c, h * 1.35f, side, color.copy(alpha = 0.85f * stay), stroke * 0.8f)
            }
        } else {
            val undrawn = 1f - p.segment(0f, 0.5f, androidx.compose.animation.core.LinearEasing)
            tracePath(rune, undrawn, color, stroke)
            if (undrawn in 0.01f..0.99f) spark(color, pathPoint(rune, undrawn), stroke * 0.8f)
            val dash = p.segment(0.45f, 0.8f)
            val half = h * 0.45f * (1f - dash)
            if (p >= 0.45f) {
                if (half > 0.5f) drawLine(color, c - Offset(half, 0f), c + Offset(half, 0f), stroke, StrokeCap.Round)
                val dot = p.segment(0.7f, 1f)
                glow(color, c, h * 0.9f * dot, 0.6f)
                drawCircle(color, stroke * (0.5f + 0.7f * overshoot(dot, 0.3f)), c)
            }
        }
    }
}

private fun DrawScope.sideArc(c: Offset, r: Float, side: Float, color: Color, width: Float) {
    val start = if (side < 0) 145f else -35f
    drawArc(color, start, 70f, false, c - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(width, cap = StrokeCap.Round))
}

/**
 * Airplane mode on: the plane takes off along a curving contrail, and the signal bars under its
 * path drop one by one into a dotted line; the plane ends cruising at the top right. Off: it comes
 * in from the top right, touches down with a puff and rolls out to the left, and the bars pop back up.
 */
@Composable
internal fun AirplaneScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(2_100)
    val plane = rememberVectorPainter(Icons.Rounded.AirplanemodeActive)
    Canvas(modifier) {
        val ground = size.height * 0.86f
        val s = 20.dp.toPx()
        // The runway.
        drawLine(color.copy(alpha = 0.45f), Offset(size.width * 0.34f, ground), Offset(size.width, ground), 1.6.dp.toPx(), StrokeCap.Round)
        // Signal bars at the left.
        for (i in 0 until 4) {
            val bh = size.height * (0.16f + 0.13f * i)
            val x = 2.dp.toPx() + i * 6.dp.toPx()
            val up = if (on) 1f - p.segment(0.25f + i * 0.1f, 0.4f + i * 0.1f) else overshoot(p.segment(0.55f + i * 0.09f, 0.72f + i * 0.09f), 0.3f)
            if (up > 0.02f) {
                drawRoundRect(color.copy(alpha = 0.35f + 0.65f * up), Offset(x, ground - bh * up), Size(4.dp.toPx(), bh * up), CornerRadius(2.dp.toPx()))
            } else {
                drawCircle(color.copy(alpha = 0.6f), 1.4.dp.toPx(), Offset(x + 2.dp.toPx(), ground))
            }
        }
        // One flight path either way: take-off climbs to the top right, landing glides in from
        // there, touches down and rolls out to the left.
        val path: (Float) -> Offset
        val t: Float
        if (on) {
            val start = Offset(size.width * 0.42f, ground - s * 0.4f)
            val ctrl = Offset(size.width * 0.78f, ground - s * 0.3f)
            val end = Offset(size.width - s * 0.7f, size.height * 0.24f)
            path = { k -> quad(start, ctrl, end, k) }
            t = p.segment(0f, 0.8f)
            val trail = Path().apply {
                moveTo(start.x, start.y)
                for (k in 1..24) path(t * k / 24f).let { lineTo(it.x, it.y) }
            }
            drawPath(trail, color.copy(alpha = 0.55f * (1f - 0.5f * p.segment(0.8f, 1f))), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        } else {
            val from = Offset(size.width - s * 0.6f, size.height * 0.2f)
            val touch = Offset(size.width * 0.66f, ground - s * 0.42f)
            val stop = Offset(size.width * 0.5f, ground - s * 0.42f)
            val glide = Offset(size.width * 0.74f, size.height * 0.3f)
            path = { k -> if (k < 0.7f) quad(from, glide, touch, k / 0.7f) else touch + (stop - touch) * ((k - 0.7f) / 0.3f) }
            t = p.segment(0f, 0.85f)
            // The touchdown puff.
            val puff = p.segment(0.55f, 0.85f)
            if (puff in 0.01f..0.99f) {
                for (k in 0 until 3) {
                    drawCircle(color.copy(alpha = 0.5f * (1f - puff)), (2f + 3f * puff).dp.toPx(), Offset(touch.x + s * 0.3f + k * 5.dp.toPx() * puff, ground - 2.dp.toPx()))
                }
            }
        }
        val at = path(t)
        val ahead = path((t + 0.03f).coerceAtMost(1f))
        val back = path((t - 0.03f).coerceAtLeast(0f))
        val angle = Math.toDegrees(kotlin.math.atan2((ahead.y - back.y).toDouble(), (ahead.x - back.x).toDouble())).toFloat() + 90f
        // The icon points up; turn it to fly along the path, nose first.
        translate(at.x - s / 2, at.y - s / 2) {
            rotate(angle, pivot = Offset(s / 2, s / 2)) {
                with(plane) { draw(Size(s, s), colorFilter = ColorFilter.tint(color)) }
            }
        }
    }
}

/**
 * Mobile data on: the up and down arrows ping-pong past each other twice, trailing light, and
 * settle side by side with a small burst. Off: they slide together and fold into one flat dash.
 */
@Composable
internal fun DataScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_800)
    Canvas(modifier) {
        val h = size.height * 0.62f
        val cx = size.width - 22.dp.toPx()
        val gap = 7.dp.toPx()
        val stroke = 2.6.dp.toPx()
        if (on) {
            val bounce = p.segment(0f, 0.7f, androidx.compose.animation.core.LinearEasing)
            // Two full swings that die down, then rest.
            val swing = sin(bounce * 4 * PI).toFloat() * (1f - bounce) * size.height * 0.22f
            val upY = size.height / 2 - swing
            val downY = size.height / 2 + swing
            if (bounce < 1f) {
                drawLine(color.copy(alpha = 0.35f), Offset(cx - gap, upY + h * 0.5f), Offset(cx - gap, upY + h * 0.5f + abs(swing) * 1.4f), stroke, StrokeCap.Round)
                drawLine(color.copy(alpha = 0.35f), Offset(cx + gap, downY - h * 0.5f), Offset(cx + gap, downY - h * 0.5f - abs(swing) * 1.4f), stroke, StrokeCap.Round)
            }
            dataArrow(Offset(cx - gap, upY), h, up = true, color, stroke)
            dataArrow(Offset(cx + gap, downY), h, up = false, color, stroke)
            val burst = p.segment(0.7f, 1f)
            if (burst in 0.01f..0.99f) {
                for (k in 0 until 8) {
                    val a = k * PI / 4
                    val from = Offset(cx, size.height / 2) + Offset(cos(a).toFloat(), sin(a).toFloat()) * (h * 0.55f + h * 0.3f * burst)
                    drawLine(color.copy(alpha = 1f - burst), from, from + Offset(cos(a).toFloat(), sin(a).toFloat()) * 4.dp.toPx(), 1.6.dp.toPx(), StrokeCap.Round)
                }
            }
            glow(color, Offset(cx, size.height / 2), h * 0.9f * p.segment(0.7f, 1f), 0.3f)
        } else {
            val meet = p.segment(0f, 0.45f)
            val fold = p.segment(0.4f, 0.8f)
            val dx = gap * (1f - meet)
            val squash = 1f - fold
            if (squash > 0.05f) {
                dataArrow(Offset(cx - dx, size.height / 2), h * squash, up = true, color, stroke)
                dataArrow(Offset(cx + dx, size.height / 2), h * squash, up = false, color, stroke)
            }
            if (fold > 0f) {
                val half = (gap + 5.dp.toPx()) * fold
                drawLine(color, Offset(cx - half, size.height / 2), Offset(cx + half, size.height / 2), stroke, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.dataArrow(c: Offset, h: Float, up: Boolean, color: Color, stroke: Float) {
    val dir = if (up) -1f else 1f
    val tip = c + Offset(0f, dir * h / 2)
    val tail = c - Offset(0f, dir * h / 2)
    drawLine(color, tail, tip, stroke, StrokeCap.Round)
    val wing = h * 0.28f
    drawLine(color, tip, tip + Offset(-wing, -dir * wing), stroke, StrokeCap.Round)
    drawLine(color, tip, tip + Offset(wing, -dir * wing), stroke, StrokeCap.Round)
}

/**
 * Location on: a little map disc tilts into view, the pin drops onto it, squashes on impact and
 * springs back, a ripple runs out and a satellite dot circles the pin once. Off: the pin lifts,
 * swells and pops like a bubble into specks; the empty map stays behind.
 */
@Composable
internal fun LocationScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    val pin = rememberVectorPainter(Icons.Rounded.LocationOn)
    Canvas(modifier) {
        val cx = size.width - 26.dp.toPx()
        val ground = size.height * 0.8f
        val discW = 22.dp.toPx()
        val discH = 6.dp.toPx()
        val disc = if (on) p.segment(0f, 0.25f) else 1f
        drawOval(color.copy(alpha = 0.2f * disc), Offset(cx - discW * disc, ground - discH), Size(2 * discW * disc, 2 * discH))
        drawOval(color.copy(alpha = 0.7f * disc), Offset(cx - discW * disc, ground - discH), Size(2 * discW * disc, 2 * discH), style = Stroke(1.4.dp.toPx()))
        // Map lines across the disc.
        if (disc > 0.5f) {
            drawLine(color.copy(alpha = 0.45f), Offset(cx - discW * 0.6f, ground - discH * 0.4f), Offset(cx + discW * 0.5f, ground + discH * 0.5f), 1.dp.toPx())
            drawLine(color.copy(alpha = 0.45f), Offset(cx - discW * 0.1f, ground - discH * 0.9f), Offset(cx + discW * 0.2f, ground + discH * 0.9f), 1.dp.toPx())
        }
        val s = 24.dp.toPx()
        if (on) {
            val drop = p.segment(0.15f, 0.45f, androidx.compose.animation.core.FastOutLinearInEasing)
            val impact = p.segment(0.45f, 0.75f)
            val squashY = if (impact > 0f) 1f - 0.3f * sin(impact * PI).toFloat() * (1f - impact) * 2.2f else 1f
            val squashX = 2f - squashY
            val y = -s + (ground - (-s)) * drop
            if (drop > 0f) {
                translate(cx - s / 2, y - s) {
                    scale(squashX, squashY.coerceIn(0.6f, 1.1f), pivot = Offset(s / 2, s)) {
                        with(pin) { draw(Size(s, s), colorFilter = ColorFilter.tint(color)) }
                    }
                }
            }
            val ripple = p.segment(0.45f, 0.85f)
            if (ripple in 0.01f..0.99f) {
                val rw = discW * (0.4f + 1.1f * ripple)
                drawOval(color.copy(alpha = 0.8f * (1f - ripple)), Offset(cx - rw, ground - rw * 0.28f), Size(2 * rw, rw * 0.56f), style = Stroke(1.6.dp.toPx()))
            }
            val orbit = p.segment(0.6f, 1f)
            if (orbit in 0.01f..0.99f) {
                val a = -PI / 2 + orbit * 2 * PI
                val head = Offset(cx, ground - s * 0.62f)
                spark(color, head + Offset(cos(a).toFloat() * s * 0.55f, sin(a).toFloat() * s * 0.3f), 1.6.dp.toPx())
            }
        } else {
            val lift = p.segment(0f, 0.4f)
            val swell = p.segment(0.3f, 0.5f)
            val pop = p.segment(0.5f, 0.85f)
            if (pop <= 0f) {
                val k = 1f + 0.25f * swell
                translate(cx - s / 2, ground - s - s * 0.35f * lift) {
                    scale(k, k, pivot = Offset(s / 2, s / 2)) {
                        with(pin) { draw(Size(s, s), colorFilter = ColorFilter.tint(color)) }
                    }
                }
            }
            if (pop in 0.01f..0.99f) {
                val at = Offset(cx, ground - s * 0.9f)
                for (k in 0 until 7) {
                    val a = k * 2 * PI / 7
                    drawCircle(color.copy(alpha = 1f - pop), 1.8.dp.toPx() * (1f - 0.5f * pop), at + Offset(cos(a).toFloat(), sin(a).toFloat()) * (s * 0.3f + s * 0.6f * pop))
                }
                drawCircle(color.copy(alpha = 0.6f * (1f - pop)), s * 0.35f * (1f + pop), at, style = Stroke(1.4.dp.toPx()))
            }
        }
    }
}
