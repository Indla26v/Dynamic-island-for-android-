package dev.indla.island.ui.island.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import dev.indla.island.ui.island.CallGreen
import dev.indla.island.ui.island.WifiBlue

private val Amber = Color(0xFFFFB74D)
private val CoolScreen = Color(0xFFCFE3FF)

/**
 * Hotspot on: the phone turns lighthouse. Its beam sweeps across the bar, and each nearby device
 * lights up and links back the moment the beam passes over it. Off: the beam narrows to a thread
 * and goes out, the links pull back and the devices shrink to rings.
 */
@Composable
internal fun HotspotScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(2_000)
    Canvas(modifier) {
        val src = Offset(8.dp.toPx(), size.height / 2)
        val devices = listOf(Offset(0.5f, 0.22f), Offset(0.74f, 0.74f), Offset(0.93f, 0.3f))
            .map { Offset(size.width * it.x, size.height * it.y) }
        val angles = devices.map { Math.toDegrees(kotlin.math.atan2((it.y - src.y).toDouble(), (it.x - src.x).toDouble())).toFloat() }
        val reach = size.width * 1.05f
        val (beamAngle, spread, beamAlpha) = if (on) {
            Triple(-45f + 95f * p.segment(0.08f, 0.72f), 9f, 1f - p.segment(0.72f, 1f))
        } else {
            Triple(0f, 26f * (1f - p.segment(0.05f, 0.55f)), 1f - p.segment(0.4f, 0.6f))
        }
        if (beamAlpha > 0.01f && spread > 0.3f) {
            val a = Math.toRadians((beamAngle - spread).toDouble())
            val b = Math.toRadians((beamAngle + spread).toDouble())
            val beam = Path().apply {
                moveTo(src.x, src.y)
                lineTo(src.x + reach * kotlin.math.cos(a).toFloat(), src.y + reach * kotlin.math.sin(a).toFloat())
                lineTo(src.x + reach * kotlin.math.cos(b).toFloat(), src.y + reach * kotlin.math.sin(b).toFloat())
                close()
            }
            drawPath(beam, Brush.radialGradient(listOf(color.copy(alpha = 0.55f * beamAlpha), color.copy(alpha = 0f)), src, reach))
        }
        devices.forEachIndexed { i, d ->
            val lit = if (on) {
                ((beamAngle - angles[i] + 4f) / 10f).coerceIn(0f, 1f)
            } else {
                1f - p.segment(0.35f + 0.12f * i, 0.6f + 0.12f * i)
            }
            if (lit > 0f) {
                // The link back to the phone, drawn out (or pulled back) with the light.
                val end = src + (d - src) * lit
                drawLine(color.copy(alpha = 0.6f), src, end, 1.3.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                glow(color, d, 9.dp.toPx() * lit, 0.5f)
                drawCircle(color, 3.6.dp.toPx() * overshoot(lit, 0.3f), d)
            }
            drawCircle(color.copy(alpha = 0.65f), 3.6.dp.toPx(), d, style = Stroke(1.3.dp.toPx()))
        }
        // The phone.
        val pw = 9.dp.toPx()
        val ph = 16.dp.toPx()
        drawRoundRect(color.copy(alpha = 0.25f), Offset(src.x - pw / 2, src.y - ph / 2), Size(pw, ph), CornerRadius(2.5.dp.toPx()))
        drawRoundRect(color, Offset(src.x - pw / 2, src.y - ph / 2), Size(pw, ph), CornerRadius(2.5.dp.toPx()), style = Stroke(1.6.dp.toPx()))
        glow(color, src, 6.dp.toPx() * beamAlpha, 0.8f)
    }
}

/**
 * Eye protection on: the eye blinks awake, then a warm filter sweeps across it like a curtain,
 * turning the cool blue iris amber and warming the glow around it. Off: the curtain draws back and
 * the iris cools to blue.
 */
@Composable
internal fun EyeScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    Canvas(modifier) {
        val c = Offset(size.width - 30.dp.toPx(), size.height / 2)
        val hw = 26.dp.toPx()
        val blink = p.segment(0f, 0.2f)
        val open = 1f - 0.9f * sin(blink * kotlin.math.PI).toFloat()
        val hh = 12.dp.toPx() * open
        val eye = Path().apply {
            moveTo(c.x - hw, c.y)
            quadraticTo(c.x, c.y - hh * 2, c.x + hw, c.y)
            quadraticTo(c.x, c.y + hh * 2, c.x - hw, c.y)
            close()
        }
        val warm = if (on) p.segment(0.2f, 0.8f) else 1f - p.segment(0.2f, 0.8f)
        glow(color, c, hw * 1.3f, 0.35f * warm)
        clipPath(eye) {
            drawRect(Color.White.copy(alpha = 0.95f), Offset(c.x - hw, c.y - hh), Size(2 * hw, 2 * hh))
            val iris = 9.dp.toPx()
            drawCircle(mix(WifiBlue, color, warm), iris, c)
            drawCircle(Color.Black, iris * 0.45f, c)
            drawCircle(Color.White, iris * 0.2f, c + Offset(-iris * 0.35f, -iris * 0.35f))
            // The warm curtain, sweeping left to right (and back when it switches off).
            val edge = c.x - hw + 2 * hw * warm
            drawRect(color.copy(alpha = 0.42f), Offset(c.x - hw, c.y - hh), Size(edge - (c.x - hw), 2 * hh))
            if (warm in 0.02f..0.98f) drawLine(color, Offset(edge, c.y - hh), Offset(edge, c.y + hh), 2.dp.toPx())
        }
        drawPath(eye, color, style = Stroke(2.dp.toPx()))
    }
}

/**
 * Link to Windows: the phone and the PC are two puzzle pieces. They slide together and snap with
 * a flash at the seam, then a sync pulse runs across. Off: they pull apart with a spark in the gap.
 */
@Composable
internal fun LinkScene(on: Boolean, color: Color, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    val phone = rememberVectorPainter(Icons.Rounded.PhoneAndroid)
    val laptop = rememberVectorPainter(Icons.Rounded.Laptop)
    Canvas(modifier) {
        val pw = 32.dp.toPx()
        val ph = 26.dp.toPx()
        val tab = 5.dp.toPx()
        val top = (size.height - ph) / 2
        val seamX = size.width - 2.dp.toPx() - pw
        val apart = if (on) 12.dp.toPx() * (1f - overshoot(p.segment(0f, 0.45f), 0.2f)) else 12.dp.toPx() * p.segment(0.1f, 0.55f)
        val left = Offset(seamX - pw - apart, top)
        val right = Offset(seamX + apart, top)
        drawPiece(puzzlePiece(left, pw, ph, tab, tabOut = true), color)
        drawPiece(puzzlePiece(right, pw, ph, tab, tabOut = false), color)
        val icon = 14.dp.toPx()
        translate(left.x + (pw - icon) / 2, top + (ph - icon) / 2) { with(phone) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(Color.White)) } }
        val wide = 17.dp.toPx()
        translate(right.x + (pw - wide) / 2 + tab * 0.4f, top + (ph - wide) / 2) { with(laptop) { draw(Size(wide, wide), colorFilter = ColorFilter.tint(Color.White)) } }
        if (on) {
            val flash = p.segment(0.42f, 0.62f)
            if (flash in 0.01f..0.99f) glow(Color.White, Offset(seamX, size.height / 2), ph * 0.8f, 0.7f * (1f - flash))
            val pulse = p.segment(0.6f, 0.95f)
            if (pulse in 0.01f..0.99f) spark(color, Offset(left.x + (right.x + pw - left.x) * pulse, top + ph + 2.dp.toPx()), 1.8.dp.toPx())
        } else {
            val sparkle = p.segment(0.1f, 0.5f)
            if (sparkle in 0.01f..0.99f) {
                val c = Offset(seamX, size.height / 2)
                for (k in 0 until 4) {
                    val a = Math.toRadians(45.0 + 90.0 * k)
                    val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                    drawLine(color.copy(alpha = 1f - sparkle), c + d * (2.dp.toPx() + 6.dp.toPx() * sparkle), c + d * (5.dp.toPx() + 8.dp.toPx() * sparkle), 1.5.dp.toPx(), StrokeCap.Round)
                }
                spark(color, c, 1.6.dp.toPx() * (1f - sparkle))
            }
        }
    }
}

/** A rounded tile with a round tab sticking out of its right side, or a matching notch in its left. */
private fun puzzlePiece(at: Offset, w: Float, h: Float, tab: Float, tabOut: Boolean): Path {
    val body = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(at.x, at.y, at.x + w, at.y + h, CornerRadius(6f * tab / 5f))) }
    val knob = Path().apply {
        val cx = if (tabOut) at.x + w else at.x
        addOval(Rect(cx - tab, at.y + h / 2 - tab, cx + tab, at.y + h / 2 + tab))
    }
    return Path().apply { op(body, knob, if (tabOut) PathOperation.Union else PathOperation.Difference) }
}

private fun DrawScope.drawPiece(piece: Path, color: Color) {
    drawPath(piece, color.copy(alpha = 0.28f))
    drawPath(piece, color, style = Stroke(1.6.dp.toPx()))
}
