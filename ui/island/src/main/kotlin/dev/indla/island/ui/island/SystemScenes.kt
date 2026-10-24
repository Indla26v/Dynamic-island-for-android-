package dev.indla.island.ui.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.indla.island.model.SystemEvent
import kotlin.math.PI
import kotlin.math.sin

// ---- Scenes ----------------------------------------------------------------------------------
// Some glances tell a little story across the card instead of animating one icon: energy
// bubbles stream into the battery, the phone links up to the internet, the plug pulls out.
// Each plays once while its card is open and then holds its final frame.

internal fun hasScene(event: SystemEvent) = event in SCENES

private val SCENES = setOf(SystemEvent.CHARGING, SystemEvent.DATA_ON, SystemEvent.BATTERY_LOW, SystemEvent.UNPLUGGED)

@Composable
internal fun SystemScene(event: SystemEvent, level: Int?, color: Color, modifier: Modifier) {
    when (event) {
        SystemEvent.CHARGING -> ChargingScene(level ?: 50, modifier)
        SystemEvent.DATA_ON -> OnlineScene(modifier)
        SystemEvent.BATTERY_LOW -> DrainScene(level ?: 10, modifier)
        SystemEvent.UNPLUGGED -> UnplugScene(color, modifier)
        else -> Unit
    }
}

/** Runs a clock for [forMs] after first shown, then stops: scenes play once, never loop. */
@Composable
private fun rememberSceneClock(forMs: Long = SCENE_MS): Long {
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(forMs)
        running = false
    }
    val start = remember { System.currentTimeMillis() }
    val now by rememberTicker(running)
    return (now - start).coerceAtLeast(0)
}

/** Green energy bubbles drift left to right and are drawn into the battery, which fills up. */
@Composable
private fun ChargingScene(level: Int, modifier: Modifier) {
    val t = rememberSceneClock()
    val fill = rememberReveal(1_800)
    val frozen = LocalFrozenTime.current != null
    Canvas(modifier) {
        val bw = 46.dp.toPx()
        val bh = 22.dp.toPx()
        val bx = size.width - bw - 4.dp.toPx()
        val by = (size.height - bh) / 2
        // Bubbles: each has its own lane and size; they shrink into the battery's left edge.
        if (!frozen) {
            val fade = ((SCENE_MS - t) / 500f).coerceIn(0f, 1f)
            for (i in 0 until BUBBLES) {
                val p = ((t / 1_700f) + i / BUBBLES.toFloat()) % 1f
                val x = p * (bx + 2.dp.toPx())
                val y = size.height / 2 + sin(p * 3 * PI + i).toFloat() * size.height * 0.22f
                val absorb = ((p - 0.82f) / 0.18f).coerceIn(0f, 1f)
                val r = (2.6f + (i % 3) * 1.4f).dp.toPx() * (1f - absorb)
                val a = (p * 6).coerceAtMost(1f) * (1f - absorb) * fade
                if (r > 0.3f && a > 0.01f) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFFB6FFC4).copy(alpha = a), CallGreen.copy(alpha = a * 0.85f)),
                            center = Offset(x - r * 0.3f, y - r * 0.3f),
                            radius = r * 1.3f,
                        ),
                        r,
                        Offset(x, y),
                    )
                }
            }
        }
        battery(bx, by, bw, bh, level / 100f * fill, CallGreen)
        bolt(Offset(bx + bw / 2, by + bh / 2), bh * 0.62f)
    }
}

/** The phone links up to the internet: a dotted bridge draws, then packets flow both ways. */
@Composable
private fun OnlineScene(modifier: Modifier) {
    val t = rememberSceneClock()
    val link = rememberReveal(900)
    val frozen = LocalFrozenTime.current != null
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val left = Offset(26.dp.toPx(), size.height / 2)
            val right = Offset(size.width - 26.dp.toPx(), size.height / 2)
            val mid = Offset((left.x + right.x) / 2, size.height * 0.08f)
            val bridge = Path().apply {
                moveTo(left.x, left.y)
                quadraticTo(mid.x, mid.y, right.x, right.y)
            }
            // The bridge draws out from the phone.
            drawPath(
                bridge,
                Color.White.copy(alpha = 0.3f),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))),
            )
            val cover = (1f - link) * (right.x - left.x)
            if (cover > 0f) drawRect(Color.Black, Offset(right.x - cover, 0f), Size(cover + 30.dp.toPx(), size.height))
            if (!frozen && link >= 1f) {
                val fade = ((SCENE_MS - t) / 500f).coerceIn(0f, 1f)
                for (k in 0 until 4) {
                    val up = k % 2 == 0
                    val p = ((t / 1_300f) + k / 4f) % 1f
                    val q = if (up) p else 1f - p
                    val pt = quad(left, mid, right, q)
                    drawCircle((if (up) CallGreen else WifiBlue).copy(alpha = fade), 3.dp.toPx(), pt)
                }
            }
            // A ring pulses out of the phone as it connects.
            val ring = (t / 900f).coerceAtMost(1f)
            if (ring < 1f) drawCircle(CallGreen.copy(alpha = 0.6f * (1f - ring)), 14.dp.toPx() + 14.dp.toPx() * ring, left, style = Stroke(1.5.dp.toPx()))
        }
        Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).size(28.dp))
        Icon(
            Icons.Rounded.Public,
            null,
            tint = WifiBlue,
            modifier = Modifier.align(Alignment.CenterEnd).size(28.dp).graphicsLayer {
                rotationZ = (t / 40f) % 360f * if (frozen) 0f else 1f
                alpha = 0.4f + 0.6f * link
            },
        )
    }
}

/** Battery low: the battery drains to its level while its outline blinks red. */
@Composable
private fun DrainScene(level: Int, modifier: Modifier) {
    val t = rememberSceneClock(2_400)
    val p = rememberReveal(1_400)
    Canvas(modifier) {
        val bw = 56.dp.toPx()
        val bh = 26.dp.toPx()
        val bx = (size.width - bw) / 2
        val by = (size.height - bh) / 2
        val blink = if (t < 2_000) 0.45f + 0.55f * ((sin(t / 250f * PI).toFloat() + 1f) / 2f) else 1f
        battery(bx, by, bw, bh, (0.45f - (0.45f - level / 100f) * p).coerceAtLeast(0.02f), DeclineRed, outlineAlpha = blink)
    }
}

/** Unplugged: the plug pulls away from the socket with a little spark. */
@Composable
private fun UnplugScene(color: Color, modifier: Modifier) {
    val p = rememberReveal(1_200)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            // The socket.
            drawRoundRect(color.copy(alpha = 0.8f), Offset(cx - 34.dp.toPx(), cy - 12.dp.toPx()), Size(8.dp.toPx(), 24.dp.toPx()), CornerRadius(3.dp.toPx()))
            // The spark, gone by the time the plug is away.
            val spark = (1f - p * 2.5f).coerceIn(0f, 1f)
            if (spark > 0f) {
                for (a in listOf(-0.6f, 0f, 0.6f)) {
                    val from = Offset(cx - 22.dp.toPx(), cy)
                    val to = Offset(from.x + kotlin.math.cos(a) * 9.dp.toPx(), from.y + kotlin.math.sin(a) * 9.dp.toPx())
                    drawLine(SaverYellow.copy(alpha = spark), from, to, 1.5.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Icon(
            Icons.Rounded.Power,
            null,
            tint = color,
            modifier = Modifier.size(30.dp).graphicsLayer {
                rotationZ = -90f + 14f * p
                translationX = (-14).dp.toPx() + 34.dp.toPx() * p
                alpha = 1f - 0.35f * p
            },
        )
    }
}

private fun quad(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1 - t
    return Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
}

private fun DrawScope.battery(x: Float, y: Float, w: Float, h: Float, fraction: Float, color: Color, outlineAlpha: Float = 1f) {
    val stroke = 1.6.dp.toPx()
    val r = h * 0.3f
    drawRoundRect(color.copy(alpha = 0.9f * outlineAlpha), Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(stroke))
    drawRoundRect(color.copy(alpha = 0.9f * outlineAlpha), Offset(x + w + stroke, y + h * 0.3f), Size(stroke * 1.6f, h * 0.4f), CornerRadius(stroke))
    val inset = stroke * 1.8f
    val fill = (w - 2 * inset) * fraction.coerceIn(0f, 1f)
    if (fill > 0f) drawRoundRect(color, Offset(x + inset, y + inset), Size(fill, h - 2 * inset), CornerRadius(r * 0.6f))
}

/** A small lightning bolt centred on [c]. */
private fun DrawScope.bolt(c: Offset, h: Float) {
    val w = h * 0.55f
    val path = Path().apply {
        moveTo(c.x + w * 0.1f, c.y - h / 2)
        lineTo(c.x - w * 0.45f, c.y + h * 0.08f)
        lineTo(c.x - w * 0.02f, c.y + h * 0.08f)
        lineTo(c.x - w * 0.12f, c.y + h / 2)
        lineTo(c.x + w * 0.45f, c.y - h * 0.1f)
        lineTo(c.x + w * 0.02f, c.y - h * 0.1f)
        close()
    }
    drawPath(path, Color.White)
}

private const val SCENE_MS = 5_500L
private const val BUBBLES = 9
