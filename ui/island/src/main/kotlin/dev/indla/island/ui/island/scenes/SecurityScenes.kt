package dev.indla.island.ui.island.scenes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Unlocked, however it was unlocked: the padlock's shackle springs open, the lock melts into a
 * round badge and a tick draws across it inside a burst ring.
 */
@Composable
internal fun UnlockedScene(color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_600)
    Canvas(modifier) {
        val c = Offset(size.width - size.height / 2, size.height / 2)
        val s = size.height * 0.3f
        val open = overshoot(p.segment(0f, 0.32f), 0.35f)
        val melt = p.segment(0.32f, 0.58f)
        val r = size.height * 0.36f
        // The body grows from the lock's rectangle into the round badge.
        val bw = s * 1.6f + (2 * r - s * 1.6f) * melt
        val bh = s * 1.2f + (2 * r - s * 1.2f) * melt
        val bodyCenter = Offset(c.x, c.y + s * 0.45f * (1f - melt))
        if (melt < 1f) {
            val shackleR = s * 0.55f
            val bodyTop = bodyCenter.y - bh / 2
            val pivot = Offset(c.x + shackleR, bodyTop)
            translate(0f, -s * 0.3f * open.coerceIn(0f, 1f)) {
                rotate(-35f * open, pivot = pivot) {
                    drawArc(color.copy(alpha = 1f - melt), 180f, 180f, false, Offset(c.x - shackleR, bodyTop - shackleR), Size(2 * shackleR, 2 * shackleR), style = Stroke(s * 0.22f, cap = StrokeCap.Round))
                }
            }
        }
        val burst = p.segment(0.55f, 1f)
        if (burst in 0.01f..0.99f) drawCircle(color.copy(alpha = 0.8f * (1f - burst)), r * (1f + 0.7f * burst), c, style = Stroke(2.dp.toPx()))
        glow(color, bodyCenter, r * 1.8f * p.segment(0.5f, 1f), 0.4f)
        val corner = s * 0.25f + (r - s * 0.25f) * melt
        drawRoundRect(color, Offset(bodyCenter.x - bw / 2, bodyCenter.y - bh / 2), Size(bw, bh), CornerRadius(corner))
        // The keyhole closes up as the tick draws in.
        if (melt < 1f) drawCircle(Color.Black.copy(alpha = 1f - melt), s * 0.15f, bodyCenter)
        val tick = Path().apply {
            moveTo(c.x - r * 0.45f, c.y + r * 0.02f)
            lineTo(c.x - r * 0.1f, c.y + r * 0.36f)
            lineTo(c.x + r * 0.5f, c.y - r * 0.32f)
        }
        tracePath(tick, p.segment(0.55f, 0.85f), Color.White, 2.6.dp.toPx())
    }
}

/**
 * Not recognised: the fingerprint's ridges trace in (or the face frame draws and the eyes
 * blink), a scan line sweeps it, a hard side-to-side shake, and a ring with an × slams onto the
 * lock.
 */
@Composable
internal fun UnlockScene(face: Boolean, ok: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(if (ok) 1_800 else 1_600)
    val trace = p.segment(0f, 0.5f)
    Canvas(modifier) {
        val shake = if (ok) 0f else sin(p.segment(0.5f, 0.85f, LinearEasing) * 6 * PI).toFloat() * 4.dp.toPx() * (1f - p.segment(0.5f, 0.9f))
        val r = size.height * 0.38f
        val symbol = Offset(r + 4.dp.toPx() + shake, size.height / 2)
        if (face) faceTrace(symbol, r, trace, p, color) else fingerprint(symbol, r, trace, color)
        // The scan line, once, as the trace completes.
        val scan = p.segment(0.35f, 0.6f, LinearEasing)
        if (scan in 0.01f..0.99f) {
            val y = symbol.y - r + 2 * r * scan
            drawLine(color.copy(alpha = 0.9f), Offset(symbol.x - r, y), Offset(symbol.x + r, y), 1.6.dp.toPx(), StrokeCap.Round)
            glow(color, Offset(symbol.x, y), r * 0.9f, 0.3f)
        }
        val lockAt = Offset(size.width - size.height * 0.48f, size.height * 0.64f)
        val s = size.height * 0.28f
        if (ok) {
            val open = overshoot(p.segment(0.55f, 0.8f), 0.3f)
            val burst = p.segment(0.6f, 1f)
            if (burst in 0.01f..0.99f) drawCircle(color.copy(alpha = 0.8f * (1f - burst)), s * (1.2f + 1.6f * burst), lockAt - Offset(0f, s * 0.3f), style = Stroke(2.dp.toPx()))
            glow(color, lockAt - Offset(0f, s * 0.3f), s * 2.2f * p.segment(0.6f, 1f), 0.35f)
            lock(lockAt, s, open, color)
        } else {
            val jiggle = sin(p.segment(0.5f, 0.85f, LinearEasing) * 4 * PI).toFloat() * (1f - p.segment(0.5f, 0.9f)) * 0.15f
            lock(lockAt, s, jiggle, color)
            val slam = overshoot(p.segment(0.7f, 0.95f), 0.35f)
            if (slam > 0f) {
                val c = lockAt + Offset(s * 0.9f, -s * 1.15f)
                val rr = s * 0.62f * slam
                drawCircle(Color.Black, rr + 1.5.dp.toPx(), c)
                drawCircle(color, rr, c)
                val x = rr * 0.45f
                drawLine(Color.White, c - Offset(x, x), c + Offset(x, x), 1.6.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, c + Offset(-x, x), c + Offset(x, -x), 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.fingerprint(c: Offset, r: Float, trace: Float, color: Color) {
    val w = 1.8.dp.toPx()
    // Concentric open loops, innermost first; each starts once the one inside it is half drawn.
    for (i in 0 until 5) {
        val rr = r * (0.2f + 0.2f * i)
        val path = Path().apply { addArc(Rect(c.x - rr * 0.8f, c.y - rr, c.x + rr * 0.8f, c.y + rr), 200f - i * 4f, 300f - i * 18f) }
        tracePath(path, trace.segment(i * 0.12f, 0.5f + i * 0.12f, LinearEasing), color, w)
    }
}

private fun DrawScope.faceTrace(c: Offset, r: Float, trace: Float, p: Float, color: Color) {
    val w = 2.dp.toPx()
    val len = r * 0.45f
    // Four corner brackets, like the Face ID frame.
    for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
        val corner = Offset(c.x + dx * r, c.y + dy * r)
        val path = Path().apply {
            moveTo(corner.x, corner.y - dy * len)
            lineTo(corner.x, corner.y)
            lineTo(corner.x - dx * len, corner.y)
        }
        tracePath(path, trace, color, w)
    }
    // Eyes, which blink once, and a smile that draws.
    val blink = p.segment(0.55f, 0.75f)
    val open = if (blink in 0.01f..0.99f) kotlin.math.abs(1f - 2f * blink) else 1f
    val eye = r * 0.12f
    for (dx in listOf(-1f, 1f)) {
        drawLine(color, Offset(c.x + dx * r * 0.35f, c.y - r * 0.2f - eye * open), Offset(c.x + dx * r * 0.35f, c.y - r * 0.2f + eye * open), w, StrokeCap.Round)
    }
    val smile = Path().apply { addArc(Rect(c.x - r * 0.4f, c.y - r * 0.1f, c.x + r * 0.4f, c.y + r * 0.5f), 20f, 140f) }
    tracePath(smile, p.segment(0.4f, 0.7f), color, w)
}

/** A padlock in [color]; [open] 0 shut, 1 shackle swung open (values in between jiggle it). */
private fun DrawScope.lock(bottomCenter: Offset, s: Float, open: Float, color: Color) {
    val bodyW = s * 1.6f
    val bodyH = s * 1.2f
    val body = Offset(bottomCenter.x - bodyW / 2, bottomCenter.y - bodyH / 2)
    val shackleR = s * 0.55f
    val pivot = Offset(bottomCenter.x + shackleR, body.y)
    translate(0f, -s * 0.3f * open.coerceIn(0f, 1f)) {
        rotate(-35f * open, pivot = pivot) {
            drawArc(color, 180f, 180f, false, Offset(bottomCenter.x - shackleR, body.y - shackleR), Size(2 * shackleR, 2 * shackleR), style = Stroke(s * 0.22f, cap = StrokeCap.Round))
        }
    }
    drawRoundRect(color, body, Size(bodyW, bodyH), CornerRadius(s * 0.25f))
    drawCircle(Color.Black, s * 0.15f, Offset(bottomCenter.x, body.y + bodyH * 0.45f))
}
