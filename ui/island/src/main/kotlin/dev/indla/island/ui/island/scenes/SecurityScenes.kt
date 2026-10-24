package dev.indla.island.ui.island.scenes

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
import dev.indla.island.ui.island.CallGreen
import dev.indla.island.ui.island.DeclineRed
import kotlin.math.PI
import kotlin.math.sin

/**
 * Unlocked: the fingerprint's ridges trace themselves in green from the centre out (or the face
 * outline traces, the eyes blink once and the smile draws), then the lock's shackle springs open.
 * Not recognised: the same trace in red, a three-shake shudder, and the shackle jiggles but stays
 * shut.
 */
@Composable
internal fun UnlockScene(face: Boolean, ok: Boolean, modifier: Modifier) {
    val p = rememberTimeline(if (ok) 1_700 else 1_500)
    val trace = p.segment(0f, 0.55f)
    val color = if (ok) CallGreen else DeclineRed
    Canvas(modifier) {
        val shake = if (ok) 0f else sin(p.segment(0.55f, 0.9f, androidx.compose.animation.core.LinearEasing) * 6 * PI).toFloat() * 4.dp.toPx() * (1f - p)
        val symbol = Offset(size.height * 0.55f + shake, size.height / 2)
        val r = size.height * 0.4f
        if (face) faceTrace(symbol, r, trace, p, color) else fingerprint(symbol, r, trace, color)
        val open = if (ok) overshoot(p.segment(0.55f, 0.85f), 0.25f) else 0f
        val jiggle = if (ok) 0f else sin(p.segment(0.6f, 0.95f, androidx.compose.animation.core.LinearEasing) * 4 * PI).toFloat() * (1f - p) * 0.15f
        lock(Offset(size.width - size.height * 0.55f, size.height * 0.62f), size.height * 0.3f, open + jiggle, Color.White)
    }
}

private fun DrawScope.fingerprint(c: Offset, r: Float, trace: Float, color: Color) {
    val w = 1.8.dp.toPx()
    // Concentric open loops, innermost first; each starts once the one inside it is half drawn.
    for (i in 0 until 5) {
        val rr = r * (0.2f + 0.2f * i)
        val path = Path().apply { addArc(Rect(c.x - rr * 0.8f, c.y - rr, c.x + rr * 0.8f, c.y + rr), 200f - i * 4f, 300f - i * 18f) }
        tracePath(path, trace.segment(i * 0.12f, 0.5f + i * 0.12f, androidx.compose.animation.core.LinearEasing), color, w)
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

/** A padlock; [open] 0 shut, 1 shackle swung open (values in between jiggle it). */
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
