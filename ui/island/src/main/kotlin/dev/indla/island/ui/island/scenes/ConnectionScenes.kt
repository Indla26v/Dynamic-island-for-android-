package dev.indla.island.ui.island.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import dev.indla.island.ui.island.AirplaneOrange
import dev.indla.island.ui.island.CallGreen
import dev.indla.island.ui.island.LocalFrozenTime
import dev.indla.island.ui.island.Secondary
import dev.indla.island.ui.island.WifiBlue

/** The phone links up to the internet: a dotted bridge draws, then packets flow both ways. */
@Composable
internal fun OnlineScene(modifier: Modifier) {
    val t = rememberSceneClock()
    val link = rememberTimeline(900).segment(0f, 1f)
    val frozen = LocalFrozenTime.current != null
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val (left, mid, right) = bridgePoints()
            drawBridge(left, mid, right, Color.White.copy(alpha = 0.3f))
            val cover = (1f - link) * (right.x - left.x)
            if (cover > 0f) drawRect(Color.Black, Offset(right.x - cover, 0f), Size(cover + 30.dp.toPx(), size.height))
            if (!frozen && link >= 1f) {
                val fade = ((FLOW_MS - t) / 500f).coerceIn(0f, 1f)
                for (k in 0 until 4) {
                    val up = k % 2 == 0
                    val p = ((t / 1_300f) + k / 4f) % 1f
                    drawCircle((if (up) CallGreen else WifiBlue).copy(alpha = fade), 3.dp.toPx(), quad(left, mid, right, if (up) p else 1f - p))
                }
            }
            val ring = (t / 900f).coerceAtMost(1f)
            if (ring < 1f) drawCircle(CallGreen.copy(alpha = 0.6f * (1f - ring)), 14.dp.toPx() + 14.dp.toPx() * ring, left, style = Stroke(1.5.dp.toPx()))
        }
        Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).size(28.dp))
        Icon(
            Icons.Rounded.Public,
            null,
            tint = WifiBlue,
            modifier = Modifier.align(Alignment.CenterEnd).size(28.dp).graphicsLayer {
                rotationZ = if (frozen) 0f else (t / 40f) % 360f
                alpha = 0.4f + 0.6f * link
            },
        )
    }
}

/** Mobile data off: the bridge snaps in the middle, both halves droop and the globe greys. */
@Composable
internal fun OfflineScene(modifier: Modifier) {
    val p = rememberTimeline(1_500)
    val snap = p.segment(0.15f, 0.7f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val (left, mid, right) = bridgePoints()
            val gap = 6.dp.toPx() * snap
            val droop = size.height * 0.25f * snap
            clipRectHalf(left = true, gap) { drawBridge(left, mid + Offset(0f, droop), right, Color.White.copy(alpha = 0.3f * (1f - 0.5f * snap))) }
            clipRectHalf(left = false, gap) { drawBridge(left, mid + Offset(0f, droop), right, Color.White.copy(alpha = 0.3f * (1f - 0.5f * snap))) }
        }
        Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).size(28.dp))
        Icon(Icons.Rounded.Public, null, tint = mixColor(WifiBlue, Secondary, snap), modifier = Modifier.align(Alignment.CenterEnd).size(28.dp))
    }
}

private fun mixColor(a: Color, b: Color, t: Float) = mix(a, b, t)

private fun DrawScope.bridgePoints(): Triple<Offset, Offset, Offset> {
    val left = Offset(26.dp.toPx(), size.height / 2)
    val right = Offset(size.width - 26.dp.toPx(), size.height / 2)
    return Triple(left, Offset((left.x + right.x) / 2, size.height * 0.08f), right)
}

private fun DrawScope.drawBridge(left: Offset, mid: Offset, right: Offset, color: Color) {
    val bridge = Path().apply {
        moveTo(left.x, left.y)
        quadraticTo(mid.x, mid.y, right.x, right.y)
    }
    drawPath(bridge, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))))
}

/** Draws [block] clipped to the left or right half, leaving a [gap] in the middle. */
private fun DrawScope.clipRectHalf(left: Boolean, gap: Float, block: DrawScope.() -> Unit) {
    val mid = size.width / 2
    clipRect(
        left = if (left) 0f else mid + gap,
        right = if (left) mid - gap else size.width,
        block = block,
    )
}

/**
 * Airplane mode on: the plane takes off along a dashed runway arc with a contrail while the
 * signal bars drain. Off: it lands back on the runway and the bars refill one by one.
 */
@Composable
internal fun AirplaneScene(on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(2_000)
    val fly = p.segment(0f, 0.8f)
    val plane = rememberVectorPainter(Icons.Rounded.AirplanemodeActive)
    Canvas(modifier) {
        val start = Offset(size.width * 0.3f, size.height * 0.8f)
        val end = Offset(size.width * 0.92f, size.height * 0.22f)
        val ctrl = Offset(size.width * 0.75f, size.height * 0.85f)
        drawLine(Secondary, Offset(size.width * 0.24f, size.height * 0.9f), Offset(size.width * 0.72f, size.height * 0.9f), 1.5.dp.toPx(), StrokeCap.Round)
        val t = if (on) fly else 1f - fly
        // Contrail along the flight so far.
        val trail = Path().apply {
            moveTo(start.x, start.y)
            for (i in 1..24) {
                val q = quad(start, ctrl, end, t * i / 24f)
                lineTo(q.x, q.y)
            }
        }
        val trailAlpha = if (on) 0.5f * (1f - 0.6f * p.segment(0.75f, 1f)) else 0.5f * (1f - fly)
        drawPath(trail, AirplaneOrange.copy(alpha = trailAlpha), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
        // Signal bars: drain on take-off, refill on landing.
        for (i in 0 until 4) {
            val lit = if (on) 1f - p.segment(i * 0.15f, i * 0.15f + 0.2f) else p.segment(0.5f + i * 0.12f, 0.6f + i * 0.12f)
            val bh = size.height * (0.18f + 0.14f * i)
            drawRoundRect(Color.White.copy(alpha = 0.2f + 0.8f * lit), Offset(4.dp.toPx() + i * 6.dp.toPx(), size.height * 0.82f - bh), Size(4.dp.toPx(), bh), CornerRadius(2.dp.toPx()))
        }
        // The plane, nose along its path.
        val at = quad(start, ctrl, end, t)
        val ahead = quad(start, ctrl, end, (t + 0.02f).coerceAtMost(1f))
        val back = quad(start, ctrl, end, (t - 0.02f).coerceAtLeast(0f))
        val angle = Math.toDegrees(kotlin.math.atan2((ahead.y - back.y).toDouble(), (ahead.x - back.x).toDouble())).toFloat() + 90f
        val s = 22.dp.toPx()
        translate(at.x - s / 2, at.y - s / 2) {
            rotate(angle, pivot = Offset(s / 2, s / 2)) {
                with(plane) { draw(Size(s, s), colorFilter = ColorFilter.tint(if (on) AirplaneOrange else Secondary)) }
            }
        }
    }
}

/**
 * Wi-Fi connected: a router sends three waves to the phone; each one that arrives lights one arc
 * of the phone's Wi-Fi symbol. Disconnected: the arcs crack, fall away and a slash draws.
 */
@Composable
internal fun WifiScene(on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(2_000)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val phone = Offset(18.dp.toPx(), size.height / 2)
            val router = Offset(size.width - 18.dp.toPx(), size.height / 2)
            val symbol = Offset(phone.x + 26.dp.toPx(), size.height * 0.78f)
            val unit = size.height * 0.16f
            for (i in 0 until 3) {
                if (on) {
                    // A wave travels from the router to the phone.
                    val wave = p.segment(i * 0.2f, i * 0.2f + 0.45f)
                    if (wave in 0.01f..0.99f) {
                        val r = (router.x - phone.x) * wave
                        drawArc(WifiBlue.copy(alpha = 0.6f * (1f - wave * 0.5f)), 150f, 60f, false, router - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    val lit = p.segment(i * 0.2f + 0.4f, i * 0.2f + 0.5f)
                    arc(symbol, unit * (i + 1), 0.25f + 0.75f * lit, WifiBlue)
                } else {
                    val fall = p.segment(0.1f + (2 - i) * 0.12f, 0.45f + (2 - i) * 0.12f)
                    arc(symbol + Offset(0f, size.height * 0.4f * fall), unit * (i + 1), (1f - fall) * 0.9f, Secondary)
                }
            }
            drawCircle(if (on) WifiBlue else Secondary, unit * 0.35f, symbol)
            if (!on) {
                val slash = p.segment(0.6f, 1f)
                drawLine(Secondary, symbol + Offset(-unit * 2.4f, -unit * 3.2f), symbol + Offset(-unit * 2.4f + unit * 4.8f * slash, -unit * 3.2f + unit * 3.6f * slash), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).size(26.dp))
        Icon(Icons.Rounded.Router, null, tint = if (on) WifiBlue else Secondary, modifier = Modifier.align(Alignment.CenterEnd).size(26.dp))
    }
}

private fun DrawScope.arc(center: Offset, r: Float, alpha: Float, color: Color) {
    if (alpha <= 0f) return
    drawArc(color.copy(alpha = alpha), 225f, 90f, false, center - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(r * 0.28f, cap = StrokeCap.Round))
}

/**
 * Location on: a little map grid fades in, the pin drops with a bounce, a ripple and one radar
 * sweep circle it. Off: the pin lifts and shrinks away, the grid fades and a slash draws.
 */
@Composable
internal fun LocationScene(on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    val grid = if (on) p.segment(0f, 0.35f) else 1f - p.segment(0.3f, 0.8f) * 0.7f
    val drop = if (on) p.segment(0.2f, 0.6f) else 1f - p.segment(0f, 0.45f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            var x = step / 2
            while (x < size.width) {
                drawLine(Color.White.copy(alpha = 0.12f * grid), Offset(x, 0f), Offset(x + size.height * 0.3f, size.height), 1.dp.toPx())
                x += step
            }
            var y = step / 2
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.12f * grid), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += step
            }
            val base = Offset(size.width / 2, size.height * 0.8f)
            if (on) {
                val ripple = p.segment(0.55f, 1f)
                if (ripple in 0.01f..0.99f) {
                    val r = size.height * 0.15f + size.height * 0.45f * ripple
                    drawOval(WifiBlue.copy(alpha = 0.7f * (1f - ripple)), base - Offset(r, r * 0.35f), Size(2 * r, r * 0.7f), style = Stroke(1.5.dp.toPx()))
                }
                val sweep = p.segment(0.6f, 1f)
                if (sweep in 0.01f..0.99f) {
                    val r = size.height * 0.45f
                    drawArc(WifiBlue.copy(alpha = 0.25f), -90f + 360f * sweep - 40f, 40f, true, base - Offset(r, r), Size(2 * r, 2 * r))
                }
            } else {
                val slash = p.segment(0.6f, 1f)
                drawLine(Secondary, Offset(size.width * 0.32f, size.height * 0.12f), Offset(size.width * (0.32f + 0.36f * slash), size.height * (0.12f + 0.76f * slash)), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        Icon(
            Icons.Rounded.LocationOn,
            null,
            tint = if (on) WifiBlue else Secondary,
            modifier = Modifier.size(28.dp).graphicsLayer {
                translationY = -size.height * 1.4f * (1f - overshoot(drop, 0.25f))
                val s = if (on) 1f else 0.4f + 0.6f * drop
                scaleX = s
                scaleY = s
                alpha = if (on) 1f else drop
            },
        )
    }
}
