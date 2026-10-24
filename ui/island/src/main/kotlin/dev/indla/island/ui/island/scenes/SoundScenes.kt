package dev.indla.island.ui.island.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.indla.island.model.AudioKind
import dev.indla.island.ui.island.DeclineRed
import dev.indla.island.ui.island.Secondary
import kotlin.math.PI
import kotlin.math.sin

/** Ring: the bell swings twice, sending out three sound waves that ripple away. */
@Composable
internal fun RingScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_800)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(20.dp.toPx(), size.height / 2)
            for (i in 0 until 3) {
                val w = p.segment(0.1f + i * 0.15f, 0.7f + i * 0.1f)
                if (w in 0.01f..0.99f || (i == 0 && p >= 1f)) waves(c, size.height * (0.4f + 1.2f * (if (p >= 1f) 0.1f else w)), args.color.copy(alpha = if (p >= 1f) 0.35f else 0.8f * (1f - w)))
            }
        }
        val swing = sin(p.segment(0f, 0.7f, androidx.compose.animation.core.LinearEasing) * 4 * PI).toFloat() * (1f - p) * 26f
        Icon(
            Icons.Rounded.NotificationsActive,
            null,
            tint = args.color,
            modifier = Modifier.size(30.dp).graphicsLayer {
                rotationZ = swing
                transformOrigin = TransformOrigin(0.5f, 0.1f)
            },
        )
    }
}

/** Vibrate: a phone buzzes side to side between zig-zag marks that decay to stillness. */
@Composable
internal fun VibrateScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_600)
    val decay = 1f - p
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val shake = sin(p * 18 * PI).toFloat() * 3.dp.toPx() * decay
        val pw = 18.dp.toPx()
        val ph = 32.dp.toPx()
        drawRoundRect(args.color, Offset(c.x - pw / 2 + shake, c.y - ph / 2), Size(pw, ph), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawLine(args.color, Offset(c.x - 3.dp.toPx() + shake, c.y + ph / 2 - 4.dp.toPx()), Offset(c.x + 3.dp.toPx() + shake, c.y + ph / 2 - 4.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
        for (side in listOf(-1f, 1f)) {
            for (k in 1..2) {
                val x = c.x + side * (pw / 2 + 6.dp.toPx() * k)
                val amp = 4.dp.toPx() * (0.35f + 0.65f * decay) * (if (k == 2) 0.6f else 1f)
                val zig = Path().apply {
                    moveTo(x, c.y - ph * 0.35f)
                    for (j in 1..4) lineTo(x + (if (j % 2 == 0) -amp else amp) * side * 0.5f, c.y - ph * 0.35f + ph * 0.175f * j)
                }
                drawPath(zig, args.color.copy(alpha = if (p >= 1f && k == 2) 0f else 0.8f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/** Silent: waves leave the bell, a red slash cuts across, the waves fold back in and it goes red. */
@Composable
internal fun SilentScene(modifier: Modifier) {
    val p = rememberTimeline(1_800)
    val out = p.segment(0f, 0.35f)
    val cut = p.segment(0.35f, 0.6f)
    val fold = p.segment(0.55f, 0.85f)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(20.dp.toPx(), size.height / 2)
            val reach = out * (1f - fold)
            for (i in 0 until 3) {
                if (reach > 0.02f) waves(c, size.height * (0.4f + 0.35f * i) * reach + size.height * 0.2f, Color.White.copy(alpha = 0.7f * (1f - fold)))
            }
            if (cut > 0f) {
                drawLine(DeclineRed, Offset(c.x + 30.dp.toPx(), size.height * 0.1f), Offset(c.x + 30.dp.toPx() + size.width * 0.5f * cut, size.height * 0.1f + size.height * 0.8f * cut), 2.5.dp.toPx(), StrokeCap.Round)
            }
        }
        Icon(
            if (fold < 0.5f) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
            null,
            tint = mix(Color.White, DeclineRed, fold),
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Sound waves (two arcs) to the right of [c] at radius [r]. */
private fun DrawScope.waves(c: Offset, r: Float, color: Color) {
    drawArc(color, -35f, 70f, false, c - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Audio devices, each as itself. Earbuds rise out of a case whose lid flips open; over-ear
 * headphones assemble (the band traces in, the cups slide onto it) and pulse once; a speaker's
 * cone pumps three times and sound rings radiate. Disconnecting plays each in reverse.
 */
@Composable
internal fun AudioScene(kind: AudioKind, connected: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_700)
    val k = if (connected) p else 1f - p
    val tint = if (connected) color else Secondary
    Canvas(modifier) {
        when (kind) {
            AudioKind.EARBUDS -> earbuds(k, tint)
            AudioKind.SPEAKER -> speaker(p, connected, tint)
            else -> overEar(k, p, connected, tint)
        }
    }
}

private fun DrawScope.earbuds(k: Float, tint: Color) {
    val cx = size.width / 2
    val caseW = size.height * 0.95f
    val caseH = size.height * 0.5f
    val top = size.height * 0.48f
    val lid = k.segment(0f, 0.45f)
    val rise = k.segment(0.35f, 1f)
    // Buds, rising out of the case.
    for (side in listOf(-1f, 1f)) {
        val bx = cx + side * caseW * 0.2f
        val by = top + caseH * 0.3f - size.height * 0.38f * overshoot(rise, 0.1f)
        drawCircle(tint, size.height * 0.1f, Offset(bx, by))
        drawRoundRect(tint, Offset(bx - size.height * 0.035f, by), Size(size.height * 0.07f, size.height * 0.2f), CornerRadius(size.height * 0.035f))
    }
    // Case body, drawn over the buds' stems.
    drawRoundRect(Color(0xFF2C2C2E), Offset(cx - caseW / 2, top), Size(caseW, caseH), CornerRadius(caseH * 0.35f))
    drawRoundRect(tint.copy(alpha = 0.6f), Offset(cx - caseW / 2, top), Size(caseW, caseH), CornerRadius(caseH * 0.35f), style = Stroke(1.5.dp.toPx()))
    // The lid hinges open from the back edge.
    rotate(-110f * lid, pivot = Offset(cx + caseW / 2, top)) {
        drawRoundRect(Color(0xFF3A3A3C), Offset(cx - caseW / 2, top - caseH * 0.32f), Size(caseW, caseH * 0.32f), CornerRadius(caseH * 0.2f))
    }
}

private fun DrawScope.overEar(k: Float, p: Float, connected: Boolean, tint: Color) {
    val cx = size.width / 2
    val r = size.height * 0.36f
    val band = k.segment(0f, 0.45f)
    val cups = k.segment(0.35f, 0.8f)
    val bandPath = Path().apply { addArc(androidx.compose.ui.geometry.Rect(cx - r, size.height * 0.12f, cx + r, size.height * 0.12f + 2 * r), 180f, 180f) }
    tracePath(bandPath, band, tint, 3.dp.toPx())
    for (side in listOf(-1f, 1f)) {
        val x = cx + side * (r + size.width * 0.3f * (1f - overshoot(cups, 0.08f)))
        val cw = size.height * 0.2f
        val ch = size.height * 0.36f
        drawRoundRect(tint.copy(alpha = cups), Offset(x - cw / 2, size.height * 0.48f), Size(cw, ch), CornerRadius(cw * 0.45f))
        if (connected) {
            val pulse = p.segment(0.8f, 1f)
            if (pulse in 0.01f..0.99f) {
                val pr = cw * (0.8f + 1.2f * pulse)
                drawArc(tint.copy(alpha = 0.7f * (1f - pulse)), if (side < 0) 120f else -60f, 120f, false, Offset(x - pr, size.height * 0.66f - pr), Size(2 * pr, 2 * pr), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.speaker(p: Float, connected: Boolean, tint: Color) {
    val cx = size.width / 2
    val bodyH = size.height * 0.9f
    val bodyW = bodyH * 0.62f
    val top = (size.height - bodyH) / 2
    val appear = if (connected) p.segment(0f, 0.25f) else 1f
    drawRoundRect(Color(0xFF2C2C2E).copy(alpha = appear), Offset(cx - bodyW / 2, top), Size(bodyW, bodyH), CornerRadius(bodyW * 0.2f))
    // Woofer: pumps three times with decaying strength, or dims when disconnecting.
    val pump = if (connected) sin(p.segment(0.2f, 0.75f, androidx.compose.animation.core.LinearEasing) * 3 * 2 * PI).toFloat().coerceAtLeast(0f) * (1f - p) else 0f
    val wr = bodyW * 0.32f * (1f + 0.18f * pump)
    val woofer = Offset(cx, top + bodyH * 0.64f)
    drawCircle(tint.copy(alpha = appear * (if (connected) 1f else 1f - 0.6f * p)), wr, woofer, style = Stroke(2.dp.toPx()))
    drawCircle(tint.copy(alpha = appear * (if (connected) 1f else 1f - 0.6f * p)), wr * 0.35f, woofer)
    drawCircle(tint.copy(alpha = appear * 0.8f), bodyW * 0.12f, Offset(cx, top + bodyH * 0.24f), style = Stroke(1.5.dp.toPx()))
    // Rings radiate once (or collapse inward when it disconnects).
    for (i in 0 until 2) {
        val ring = if (connected) p.segment(0.3f + i * 0.15f, 0.9f + i * 0.05f) else 1f - p.segment(i * 0.1f, 0.6f + i * 0.1f)
        if (ring in 0.01f..0.99f) {
            val rr = wr + size.width * 0.32f * ring
            drawArc(tint.copy(alpha = 0.6f * (1f - ring)), -40f, 80f, false, woofer - Offset(rr, rr), Size(2 * rr, 2 * rr), style = Stroke(1.5.dp.toPx()))
            drawArc(tint.copy(alpha = 0.6f * (1f - ring)), 140f, 80f, false, woofer - Offset(rr, rr), Size(2 * rr, 2 * rr), style = Stroke(1.5.dp.toPx()))
        }
    }
}
