package dev.indla.island.ui.island.scenes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import dev.indla.island.model.AudioKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Sound scenes, each in its glance's own colour; they sit at the bar's right edge.

/**
 * Ring: the bell swings hard and settles, motion arcs flicking off it at each end of the swing,
 * while an equaliser beside it jumps to life and settles into a steady chord.
 */
@Composable
internal fun RingScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    val bell = rememberVectorPainter(Icons.Rounded.Notifications)
    Canvas(modifier) {
        val color = args.color
        val s = 26.dp.toPx()
        val pivot = Offset(s / 2 + 2.dp.toPx(), size.height / 2 - s * 0.42f)
        val swingT = p.segment(0f, 0.75f, LinearEasing)
        val swing = sin(swingT * 5 * PI).toFloat() * (1f - swingT) * 30f
        drawBell(bell, pivot, s, swing, color)
        // Motion arcs at the ends of the swing.
        val kick = abs(sin(swingT * 5 * PI).toFloat()) * (1f - swingT)
        if (kick > 0.15f) {
            val side = if (sin(swingT * 5 * PI) > 0) 1f else -1f
            val c = pivot + Offset(0f, s * 0.55f)
            val r = s * 0.62f
            drawArc(color.copy(alpha = kick), if (side > 0) -40f else 140f, 80f, false, c - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }
        // The equaliser: five bars that jump, then settle into a chord.
        val bars = listOf(0.45f, 0.8f, 0.6f, 1f, 0.55f)
        val x0 = s + 12.dp.toPx()
        val bw = 4.dp.toPx()
        val gap = ((size.width - x0 - 2.dp.toPx()) - bars.size * bw) / (bars.size - 1)
        val floor = size.height * 0.82f
        val maxH = size.height * 0.62f
        bars.forEachIndexed { i, target ->
            val grow = p.segment(0.08f + 0.06f * i, 0.4f + 0.06f * i)
            val bounce = abs(sin((p * 7 + i * 0.7f) * PI).toFloat()) * (1f - p.segment(0.5f, 1f))
            val h = maxH * (target * grow * (1f - 0.5f * bounce) + 0.5f * bounce * grow).coerceIn(0.08f, 1f)
            val x = x0 + i * (bw + gap)
            drawRoundRect(color.copy(alpha = 0.55f + 0.45f * grow), Offset(x, floor - h), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}

/**
 * Vibrate: the phone buzzes in a blur of after-images while a haptic waveform shoots out of it
 * and draws across, its peaks shrinking as the buzz dies down.
 */
@Composable
internal fun VibrateScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_700)
    Canvas(modifier) {
        val color = args.color
        val pw = 14.dp.toPx()
        val ph = 26.dp.toPx()
        val c = Offset(pw / 2 + 4.dp.toPx(), size.height / 2)
        val buzz = 1f - p.segment(0f, 0.85f)
        val shake = sin(p * 26 * PI).toFloat() * 3.dp.toPx() * buzz
        // After-images either side while it buzzes.
        if (buzz > 0.05f) {
            for (ghost in listOf(-1f, 1f)) {
                phone(c + Offset(ghost * 3.dp.toPx() * buzz, 0f), pw, ph, color.copy(alpha = 0.3f * buzz))
            }
        }
        phone(c + Offset(shake, 0f), pw, ph, color)
        // The waveform: a zig-zag whose peaks shrink toward the right.
        val start = c.x + pw / 2 + 5.dp.toPx()
        val end = size.width - 2.dp.toPx()
        val wave = Path().apply {
            moveTo(start, c.y)
            val steps = 9
            for (j in 1..steps) {
                val x = start + (end - start) * j / steps
                val amp = size.height * 0.34f * (1f - j / (steps + 1f))
                lineTo(x, c.y + if (j == steps) 0f else if (j % 2 == 0) amp else -amp)
            }
        }
        val drawn = p.segment(0.05f, 0.7f, LinearEasing)
        tracePath(wave, drawn, color, 2.dp.toPx())
        if (drawn in 0.01f..0.99f) spark(color, pathPoint(wave, drawn), 1.8.dp.toPx())
    }
}

private fun DrawScope.phone(c: Offset, w: Float, h: Float, color: Color) {
    drawRoundRect(color.copy(alpha = color.alpha * 0.25f), Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(3.dp.toPx()))
    drawRoundRect(color, Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(3.dp.toPx()), style = Stroke(1.8.dp.toPx()))
    drawLine(color, Offset(c.x - 2.dp.toPx(), c.y + h / 2 - 3.5.dp.toPx()), Offset(c.x + 2.dp.toPx(), c.y + h / 2 - 3.5.dp.toPx()), 1.4.dp.toPx(), StrokeCap.Round)
}

/**
 * Silent: the side switch flips over and floods red, the bell's sound waves are pulled back into
 * it, and a slash swooshes through the bell with a bright head.
 */
@Composable
internal fun SilentScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_800)
    val bell = rememberVectorPainter(Icons.Rounded.Notifications)
    Canvas(modifier) {
        val color = args.color
        // The switch.
        val tw = 22.dp.toPx()
        val th = 12.dp.toPx()
        val track = Offset(2.dp.toPx(), (size.height - th) / 2)
        val flip = overshoot(p.segment(0f, 0.3f), 0.25f)
        drawRoundRect(color.copy(alpha = 0.25f + 0.55f * flip), track, Size(tw, th), CornerRadius(th / 2))
        drawRoundRect(color, track, Size(tw, th), CornerRadius(th / 2), style = Stroke(1.4.dp.toPx()))
        val knob = track + Offset(th / 2 + (tw - th) * flip, th / 2)
        drawCircle(Color.White, th * 0.36f, knob)
        // The bell, with its waves pulled back in.
        val s = 26.dp.toPx()
        val bc = Offset(size.width - s / 2 - 4.dp.toPx(), size.height / 2)
        val pull = p.segment(0.2f, 0.6f)
        for (i in 0 until 2) {
            val r = s * (0.75f + 0.3f * i) * (1f - 0.5f * pull)
            val a = (1f - pull) * 0.9f
            if (a > 0.02f) {
                drawArc(color.copy(alpha = a), -40f, 80f, false, bc - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color.copy(alpha = a), 140f, 80f, false, bc - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        drawBell(bell, bc - Offset(0f, s * 0.42f), s, 0f, color)
        // The slash, top left to bottom right, with a cut-out halo so it reads on the bell.
        val cut = p.segment(0.55f, 0.85f)
        if (cut > 0f) {
            val from = bc + Offset(-s * 0.5f, -s * 0.5f)
            val to = from + Offset(s, s) * cut
            drawLine(Color.Black, from, to, 5.dp.toPx(), StrokeCap.Round)
            drawLine(color, from, to, 2.2.dp.toPx(), StrokeCap.Round)
            if (cut < 1f) spark(color, to, 1.6.dp.toPx())
        }
    }
}

/** The bell icon hung from [pivot] (its top centre), swung [degrees]. */
private fun DrawScope.drawBell(bell: VectorPainter, pivot: Offset, s: Float, degrees: Float, color: Color) {
    rotate(degrees, pivot = pivot) {
        translate(pivot.x - s / 2, pivot.y - s * 0.08f) {
            with(bell) { draw(Size(s, s), colorFilter = ColorFilter.tint(color)) }
        }
    }
}

/**
 * Audio devices, each as itself. Earbuds: the case's lid flips open, the buds spring out and tilt
 * apart in a pairing halo. Over-ear headphones: the band draws, the cups clamp on, a note floats
 * up. A speaker: the cone pumps and rings radiate. Disconnecting plays each one closing down.
 */
@Composable
internal fun AudioScene(kind: AudioKind, connected: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    val note = rememberVectorPainter(Icons.Rounded.MusicNote)
    Canvas(modifier) {
        when (kind) {
            AudioKind.EARBUDS -> earbuds(p, connected, color)
            AudioKind.SPEAKER -> speaker(p, connected, color)
            else -> overEar(p, connected, color, note)
        }
    }
}

private fun DrawScope.earbuds(p: Float, connected: Boolean, color: Color) {
    val caseW = 30.dp.toPx()
    val caseH = 20.dp.toPx()
    val cx = size.width - caseW / 2 - 4.dp.toPx()
    val top = size.height - caseH - 3.dp.toPx()
    val lidH = caseH * 0.36f
    // Lid angle: 0 closed, 1 fully open (flipped back over the hinge on the top edge).
    val open = if (connected) p.segment(0f, 0.3f) else 1f - overshoot(p.segment(0.45f, 0.7f), 0.15f)
    val rise = if (connected) overshoot(p.segment(0.22f, 0.6f), 0.22f) else 1f - p.segment(0f, 0.45f)
    val tilt = if (connected) p.segment(0.5f, 0.75f) else 1f - p.segment(0f, 0.3f)
    // The pairing halo, once, behind everything.
    if (connected) {
        val halo = p.segment(0.55f, 1f)
        if (halo in 0.01f..0.99f) {
            val r = caseW * (0.4f + 0.7f * halo)
            drawCircle(color.copy(alpha = 0.7f * (1f - halo)), r, Offset(cx, top - caseH * 0.2f), style = Stroke(1.6.dp.toPx()))
        }
        glow(color, Offset(cx, top - caseH * 0.25f), caseW * 0.8f * p.segment(0.6f, 1f), 0.3f)
    }
    // The buds, rising out of the case and leaning apart.
    for (side in listOf(-1f, 1f)) {
        val base = Offset(cx + side * caseW * 0.2f, top + caseH * 0.35f)
        val lift = size.height * 0.58f * rise.coerceAtLeast(0f)
        val head = base - Offset(-side * 9.dp.toPx() * tilt, lift)
        rotate(side * 22f * tilt, pivot = head) {
            drawRoundRect(color, Offset(head.x - 2.dp.toPx(), head.y), Size(4.dp.toPx(), 9.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawCircle(color, 4.6.dp.toPx(), head)
        }
        if (rise > 0.6f) {
            glow(color, head, 6.dp.toPx(), 0.6f * tilt)
            drawCircle(Color.White.copy(alpha = tilt), 1.3.dp.toPx(), head + Offset(side * 1.5.dp.toPx(), -1.dp.toPx()))
        }
    }
    // The case, drawn over the buds' stems.
    drawRoundRect(Color.Black, Offset(cx - caseW / 2, top), Size(caseW, caseH), CornerRadius(caseH * 0.42f))
    drawRoundRect(color.copy(alpha = 0.28f), Offset(cx - caseW / 2, top), Size(caseW, caseH), CornerRadius(caseH * 0.42f))
    drawRoundRect(color, Offset(cx - caseW / 2, top), Size(caseW, caseH), CornerRadius(caseH * 0.42f), style = Stroke(1.6.dp.toPx()))
    // The status light on its front.
    drawCircle(color, 1.6.dp.toPx(), Offset(cx, top + caseH * 0.68f))
    // The lid: flattens as it swings over the hinge, then shows its inside behind the case.
    val k = cos(open.coerceIn(-0.2f, 1.2f) * PI * 0.85).toFloat()
    val lh = lidH * abs(k)
    val lidTop = if (k >= 0) top else top - lh
    drawRoundRect(if (k >= 0) color else color.copy(alpha = 0.55f), Offset(cx - caseW / 2, lidTop), Size(caseW, lh.coerceAtLeast(1.dp.toPx())), CornerRadius(caseH * 0.3f))
    // Closing clicks shut with a ring.
    if (!connected) {
        val click = p.segment(0.68f, 1f)
        if (click in 0.01f..0.99f) {
            val r = caseW * (0.55f + 0.5f * click)
            drawRoundRect(color.copy(alpha = 0.8f * (1f - click)), Offset(cx - r, top + caseH / 2 - r * 0.62f), Size(2 * r, r * 1.24f), CornerRadius(r * 0.6f), style = Stroke(1.4.dp.toPx()))
        }
    }
}

private fun DrawScope.overEar(p: Float, connected: Boolean, color: Color, note: VectorPainter) {
    val r = size.height * 0.34f
    val cx = size.width - r - 10.dp.toPx()
    val bandTop = size.height * 0.12f
    val band = if (connected) p.segment(0f, 0.4f) else 1f
    val clamp = if (connected) overshoot(p.segment(0.3f, 0.65f), 0.25f) else 1f - p.segment(0.1f, 0.55f)
    val bandPath = Path().apply { addArc(androidx.compose.ui.geometry.Rect(cx - r, bandTop, cx + r, bandTop + 2 * r), 180f, 180f) }
    tracePath(bandPath, band, color, 3.dp.toPx())
    for (side in listOf(-1f, 1f)) {
        val x = cx + side * (r + 8.dp.toPx() * (1f - clamp))
        val cw = 9.dp.toPx()
        val ch = 16.dp.toPx()
        val y = bandTop + r - 2.dp.toPx()
        drawRoundRect(color.copy(alpha = 0.3f), Offset(x - cw / 2, y), Size(cw, ch), CornerRadius(cw * 0.45f))
        drawRoundRect(color, Offset(x - cw / 2, y), Size(cw, ch), CornerRadius(cw * 0.45f), style = Stroke(1.8.dp.toPx()))
    }
    if (connected) {
        val rise = p.segment(0.6f, 1f)
        if (rise > 0f) {
            val s = 13.dp.toPx()
            translate(cx - r - s * 1.3f, bandTop + r * 0.9f - 10.dp.toPx() * rise) {
                with(note) { draw(Size(s, s), alpha = rise, colorFilter = ColorFilter.tint(color)) }
            }
        }
    }
}

private fun DrawScope.speaker(p: Float, connected: Boolean, color: Color) {
    val bodyH = size.height * 0.86f
    val bodyW = bodyH * 0.6f
    val cx = size.width - bodyW / 2 - 4.dp.toPx()
    val top = (size.height - bodyH) / 2
    drawRoundRect(color.copy(alpha = 0.22f), Offset(cx - bodyW / 2, top), Size(bodyW, bodyH), CornerRadius(bodyW * 0.22f))
    drawRoundRect(color, Offset(cx - bodyW / 2, top), Size(bodyW, bodyH), CornerRadius(bodyW * 0.22f), style = Stroke(1.6.dp.toPx()))
    val pumpT = p.segment(0.1f, 0.8f, LinearEasing)
    val pump = if (connected) sin(pumpT * 3 * 2 * PI).toFloat().coerceAtLeast(0f) * (1f - pumpT) else 0f
    val woofer = Offset(cx, top + bodyH * 0.63f)
    val wr = bodyW * 0.3f * (1f + 0.25f * pump)
    drawCircle(color, wr, woofer, style = Stroke(2.dp.toPx()))
    drawCircle(color, wr * 0.38f, woofer)
    drawCircle(color, bodyW * 0.11f, Offset(cx, top + bodyH * 0.24f), style = Stroke(1.5.dp.toPx()))
    // Rings radiate to the left (or collapse back in when it disconnects).
    for (i in 0 until 3) {
        val ring = if (connected) p.segment(0.15f + i * 0.15f, 0.65f + i * 0.12f) else 1f - p.segment(i * 0.1f, 0.5f + i * 0.1f)
        if (ring in 0.01f..0.99f) {
            val rr = wr + size.width * 0.45f * ring
            drawArc(color.copy(alpha = 0.75f * (1f - ring)), 140f, 80f, false, woofer - Offset(rr, rr), Size(2 * rr, 2 * rr), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
