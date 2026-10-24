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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.indla.island.ui.island.CallGreen
import dev.indla.island.ui.island.Secondary
import dev.indla.island.ui.island.WifiBlue

private val Amber = Color(0xFFFFB74D)
private val CoolScreen = Color(0xFFCFE3FF)

/**
 * Hotspot on: the phone pulses signal rings outward and three nearby devices light up one by one,
 * each joined back to the phone by a dotted line. Off: the rings fold in and the devices dim.
 */
@Composable
internal fun HotspotScene(on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(2_000)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            val phone = Offset(16.dp.toPx(), size.height / 2)
            for (i in 0 until 2) {
                val ring = if (on) p.segment(i * 0.15f, 0.6f + i * 0.15f) else 1f - p.segment(i * 0.1f, 0.5f + i * 0.1f)
                if (ring in 0.01f..0.99f) {
                    val r = 10.dp.toPx() + size.width * 0.45f * ring
                    drawCircle(WifiBlue.copy(alpha = 0.55f * (1f - ring)), r, phone, style = Stroke(1.5.dp.toPx()))
                }
            }
            val devices = listOf(Offset(0.55f, 0.22f), Offset(0.78f, 0.55f), Offset(0.95f, 0.2f))
            devices.forEachIndexed { i, at ->
                val lit = if (on) p.segment(0.35f + i * 0.15f, 0.5f + i * 0.15f) else 1f - p.segment(0.2f + i * 0.1f, 0.4f + i * 0.1f)
                val d = Offset(size.width * at.x, size.height * at.y)
                if (lit > 0f) {
                    drawLine(
                        WifiBlue.copy(alpha = 0.5f * lit),
                        phone + Offset(12.dp.toPx(), 0f),
                        d,
                        1.2.dp.toPx(),
                        StrokeCap.Round,
                        PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                    )
                }
                drawCircle((if (lit > 0.5f) CallGreen else Secondary).copy(alpha = 0.35f + 0.65f * lit), 4.dp.toPx(), d)
            }
        }
        Icon(Icons.Rounded.PhoneAndroid, null, tint = if (on) WifiBlue else Secondary, modifier = Modifier.size(26.dp))
    }
}

/**
 * Eye protection on: the eye opens, and the screen beside it warms from cool white to amber as
 * three blue light rays are cut off. Off: the amber cools back and the eye relaxes.
 */
@Composable
internal fun EyeScene(on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    val warm = if (on) p.segment(0.25f, 0.9f) else 1f - p.segment(0.1f, 0.8f)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            // The screen: a rounded panel whose tint warms up.
            val screen = Rect(size.width * 0.55f, size.height * 0.12f, size.width * 0.98f, size.height * 0.88f)
            drawRoundRect(mix(CoolScreen, Amber, warm).copy(alpha = 0.9f), screen.topLeft, screen.size, androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            // Blue light rays from the screen toward the eye, cut short as it warms.
            for (i in 0 until 3) {
                val y = size.height * (0.3f + 0.2f * i)
                val from = Offset(screen.left - 3.dp.toPx(), y)
                val reach = (screen.left - size.width * 0.3f) * (1f - warm)
                if (reach > 1f) drawLine(WifiBlue.copy(alpha = 0.7f), from, from - Offset(reach, 0f), 1.6.dp.toPx(), StrokeCap.Round)
            }
            // The eye: lids open from a line to an almond, iris inside.
            val open = if (on) p.segment(0f, 0.35f) else 1f - 0.5f * p.segment(0.5f, 1f)
            val c = Offset(size.width * 0.17f, size.height / 2)
            val w = size.width * 0.15f
            val h = size.height * 0.32f * open
            val lid = Path().apply {
                moveTo(c.x - w, c.y)
                quadraticTo(c.x, c.y - h * 2, c.x + w, c.y)
                quadraticTo(c.x, c.y + h * 2, c.x - w, c.y)
                close()
            }
            drawPath(lid, Color.White, style = Stroke(1.8.dp.toPx()))
            if (open > 0.2f) drawCircle(mix(WifiBlue, Amber, warm), h * 0.9f, c)
        }
    }
}

/**
 * Link to Windows: phone and laptop slide toward each other, a link draws between them and a
 * sync pulse travels across once. Disconnecting breaks the link and they drift apart.
 */
@Composable
internal fun LinkScene(on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(1_900)
    val near = if (on) p.segment(0f, 0.4f) else 1f - p.segment(0.3f, 0.8f)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val left = Offset(28.dp.toPx() + 10.dp.toPx() * near, size.height / 2)
            val right = Offset(size.width - 34.dp.toPx() - 10.dp.toPx() * near, size.height / 2)
            val link = if (on) p.segment(0.35f, 0.65f) else 1f - p.segment(0f, 0.35f)
            val mid = Offset((left.x + right.x) / 2, size.height / 2)
            // Two chain links meeting in the middle.
            val half = (mid.x - left.x) * link
            val r = 5.dp.toPx()
            drawRoundRect(WifiBlue, Offset(left.x, mid.y - r), Size(half + r, 2 * r), androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(2.dp.toPx()))
            drawRoundRect(WifiBlue, Offset(right.x - half - r, mid.y - r), Size(half + r, 2 * r), androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(2.dp.toPx()))
            if (on) {
                val pulse = p.segment(0.65f, 1f)
                if (pulse in 0.01f..0.99f) drawCircle(CallGreen.copy(alpha = 1f - pulse * 0.4f), 3.5.dp.toPx(), Offset(left.x + (right.x - left.x) * pulse, mid.y))
            }
        }
        Icon(
            Icons.Rounded.PhoneAndroid,
            null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.CenterStart).size(26.dp).graphicsLayer { translationX = 10.dp.toPx() * near },
        )
        Icon(
            Icons.Rounded.Laptop,
            null,
            tint = if (on) WifiBlue else Secondary,
            modifier = Modifier.align(Alignment.CenterEnd).size(30.dp).graphicsLayer { translationX = -10.dp.toPx() * near },
        )
    }
}
