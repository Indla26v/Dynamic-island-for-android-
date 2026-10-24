package dev.indla.island.ui.island.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ModeNight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.indla.island.ui.island.BedtimeTeal
import dev.indla.island.ui.island.NightIndigo
import dev.indla.island.ui.island.SaverYellow

private val Dusk = Color(0xFFFF8A3D)
private val NightSky = Color(0xFF1B1A4A)
private val DaySky = Color(0xFF5AB8FF)
private val Dawn = Color(0xFFFFC28A)
private val Moonlight = Color(0xFFE8E6FF)
private val WindowLight = Color(0xFFFFD66B)

/**
 * Dark mode is a sunset over a little city: the sky deepens from orange to indigo, the sun sinks
 * behind the skyline, the moon rises along an arc, windows light up one by one and three stars
 * pop in. Light mode is the sunrise: the moon sets, the sun climbs and its rays grow, a cloud
 * drifts in and settles, and the windows go out. Played once; the last frame stays.
 */
@Composable
internal fun SkyScene(night: Boolean, modifier: Modifier) {
    val p = rememberTimeline(2_400)
    Canvas(modifier.clip(RoundedCornerShape(14.dp))) {
        val w = size.width
        val h = size.height
        val sky = p.segment(0f, 0.7f)
        val (top, low) = if (night) {
            mix(DaySky, NightSky, sky) to mix(Dusk, NightIndigo.copy(alpha = 0.9f), sky)
        } else {
            mix(NightSky, DaySky, sky) to mix(NightIndigo, Dawn, sky)
        }
        drawRect(Brush.verticalGradient(listOf(top, low)))
        val horizon = h * 0.78f

        // Sun and moon on their arcs, behind the skyline.
        val sunGo = p.segment(0.05f, 0.6f)
        val moonGo = p.segment(0.35f, 0.95f)
        val sunT = if (night) 1f - sunGo else sunGo
        val moonT = if (night) moonGo else 1f - moonGo
        clipRect(bottom = horizon) {
            val sun = Offset(w * 0.3f, horizon + h * 0.25f - h * 0.6f * sunT)
            if (!night) rays(sun, h * 0.16f, p.segment(0.55f, 1f))
            drawCircle(SaverYellow, h * 0.13f, sun)
            val moon = Offset(w * 0.72f, horizon + h * 0.25f - h * 0.62f * moonT)
            drawCircle(Moonlight, h * 0.11f, moon)
            drawCircle(mix(top, low, 0.3f), h * 0.1f, moon + Offset(h * 0.05f, -h * 0.03f))
        }

        if (night) {
            listOf(Offset(0.12f, 0.18f) to 0.6f, Offset(0.48f, 0.1f) to 0.72f, Offset(0.9f, 0.22f) to 0.84f).forEach { (at, start) ->
                val k = overshoot(p.segment(start, start + 0.12f), 0.4f)
                if (k > 0f) drawCircle(Color.White, h * 0.025f * k, Offset(w * at.x, h * at.y))
            }
        } else {
            val drift = p.segment(0.5f, 1f)
            cloud(Offset(-w * 0.2f + w * 0.55f * drift, h * 0.28f), h * 0.11f)
        }

        skyline(horizon, w, h, night, p)
    }
}

/** Sun rays that grow outward once. */
private fun DrawScope.rays(c: Offset, r: Float, grow: Float) {
    if (grow <= 0f) return
    for (i in 0 until 8) {
        val a = i * kotlin.math.PI.toFloat() / 4
        val dir = Offset(kotlin.math.cos(a), kotlin.math.sin(a))
        drawLine(SaverYellow.copy(alpha = 0.9f), c + dir * (r * 1.05f), c + dir * (r * (1.05f + 0.5f * grow)), r * 0.16f, StrokeCap.Round)
    }
}

private fun DrawScope.cloud(at: Offset, r: Float) {
    val c = Color.White.copy(alpha = 0.92f)
    drawCircle(c, r, at)
    drawCircle(c, r * 0.8f, at + Offset(r * 1.1f, r * 0.2f))
    drawCircle(c, r * 0.7f, at + Offset(-r * 1f, r * 0.3f))
    drawRoundRect(c, at + Offset(-r * 1.5f, r * 0.2f), Size(r * 3.3f, r * 0.85f), CornerRadius(r * 0.4f))
}

/** A small skyline; its windows light up at night and go out in the morning, one by one. */
private fun DrawScope.skyline(horizon: Float, w: Float, h: Float, night: Boolean, p: Float) {
    val ink = Color(0xFF0E0E1A)
    val blocks = listOf(0.04f to 0.32f, 0.2f to 0.5f, 0.38f to 0.28f, 0.52f to 0.58f, 0.7f to 0.36f, 0.84f to 0.46f)
    blocks.forEachIndexed { i, (x, height) ->
        val bw = w * 0.13f
        val top = horizon - h * height * 0.55f
        drawRect(ink, Offset(w * x, top), Size(bw, h - top))
        val on = if (night) p.segment(0.5f + i * 0.07f, 0.56f + i * 0.07f) else 1f - p.segment(0.3f + i * 0.08f, 0.36f + i * 0.08f)
        if (on > 0f) {
            drawRect(WindowLight.copy(alpha = on), Offset(w * x + bw * 0.3f, top + h * 0.08f), Size(bw * 0.4f, h * 0.07f))
        }
    }
    drawRect(ink, Offset(0f, horizon), Size(w, h - horizon))
}

/**
 * Do Not Disturb: three notification bubbles fly toward the moon disc and are hushed (shrink and
 * fade), then the disc settles into a crescent. Off fills the crescent and lets one bubble out.
 */
@Composable
internal fun DndScene(args: SceneArgs, on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        Canvas(Modifier.fillMaxSize()) {
            val disc = Offset(size.width - size.height * 0.5f, size.height / 2)
            val r = size.height * 0.36f
            for (i in 0 until 3) {
                val k = if (on) p.segment(i * 0.12f, 0.45f + i * 0.12f) else p.segment(0.5f, 0.9f)
                if (!on && i > 0) continue
                val start = Offset(size.width * 0.05f, size.height * (0.25f + 0.25f * i))
                val pos = if (on) start + (disc - start) * k else disc + (Offset(size.width * 0.15f, size.height * 0.3f) - disc) * k
                val s = if (on) 1f - k else k
                if (s > 0.02f) {
                    drawRoundRect(Color.White.copy(alpha = 0.85f * s), pos - Offset(11.dp.toPx() * s, 5.dp.toPx() * s), Size(22.dp.toPx() * s, 10.dp.toPx() * s), CornerRadius(5.dp.toPx() * s))
                }
            }
            drawCircle(args.color, r, disc)
            // The bite (in the card's black) that makes the crescent.
            val bite = if (on) p.segment(0.55f, 1f) else 1f - p.segment(0.1f, 0.6f)
            if (bite > 0f) drawCircle(Color.Black, r * 0.82f * bite, disc + Offset(r * 0.42f, -r * 0.3f))
        }
    }
}

/**
 * Bedtime: two curtains slide closed, the moon rises between them and three z's float up once.
 * Off opens the curtains and a warm wedge of morning light spills in.
 */
@Composable
internal fun BedtimeScene(on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(2_000)
    Canvas(modifier.clip(RoundedCornerShape(12.dp))) {
        val w = size.width
        val h = size.height
        val close = if (on) p.segment(0f, 0.5f) else 1f - p.segment(0f, 0.5f)
        drawRect(mix(Color(0xFF2B3A55), NightSky, if (on) close else 1f - p.segment(0.2f, 0.8f)))
        if (!on) {
            val glow = p.segment(0.3f, 1f)
            val wedge = Path().apply {
                moveTo(w * 0.5f, 0f)
                lineTo(w * (0.5f - 0.4f * glow), h)
                lineTo(w * (0.5f + 0.4f * glow), h)
                close()
            }
            drawPath(wedge, Brush.verticalGradient(listOf(Dawn.copy(alpha = 0.7f * glow), Color.Transparent)))
        }
        val moonUp = if (on) p.segment(0.4f, 0.8f) else 0f
        if (moonUp > 0f) {
            val m = Offset(w / 2, h * (1f - 0.55f * moonUp))
            drawCircle(Moonlight, h * 0.15f, m)
            drawCircle(NightSky, h * 0.13f, m + Offset(h * 0.07f, -h * 0.04f))
        }
        if (on) {
            for (k in 0 until 3) {
                val z = p.segment(0.6f + k * 0.1f, 0.95f + k * 0.02f)
                if (z <= 0f || z >= 1f) continue
                val x = w * (0.58f + 0.06f * k)
                val y = h * (0.6f - 0.45f * z)
                val s = h * (0.08f + 0.02f * k)
                val c = BedtimeTeal.copy(alpha = 1f - z)
                val sw = 1.4.dp.toPx()
                drawLine(c, Offset(x, y), Offset(x + s, y), sw, StrokeCap.Round)
                drawLine(c, Offset(x + s, y), Offset(x, y + s), sw, StrokeCap.Round)
                drawLine(c, Offset(x, y + s), Offset(x + s, y + s), sw, StrokeCap.Round)
            }
        }
        // Curtains, with soft folds; they leave a window in the middle where the moon shows.
        val panel = w * 0.34f * close
        val cloth = Color(0xFF6B4EA8)
        drawRect(cloth, Offset(0f, 0f), Size(panel, h))
        drawRect(cloth, Offset(w - panel, 0f), Size(panel, h))
        for (f in 1..2) {
            drawLine(Color.Black.copy(alpha = 0.18f), Offset(panel * f / 3f, 0f), Offset(panel * f / 3f, h), 1.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.18f), Offset(w - panel * f / 3f, 0f), Offset(w - panel * f / 3f, h), 1.dp.toPx())
        }
    }
}
