package dev.indla.island.ui.island.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.indla.island.ui.island.CallGreen
import dev.indla.island.ui.island.DeclineRed
import dev.indla.island.ui.island.LocalFrozenTime
import dev.indla.island.ui.island.MonsterRed
import dev.indla.island.ui.island.SaverYellow
import dev.indla.island.ui.island.Secondary
import kotlin.math.PI
import kotlin.math.sin

private val TorchWarm = Color(0xFFFFE9A8)

/** Green energy bubbles drift left to right and are drawn into the battery, which fills up. */
@Composable
internal fun ChargingScene(args: SceneArgs, modifier: Modifier) {
    val t = rememberSceneClock()
    val fill = rememberTimeline(1_800).segment(0f, 1f)
    val frozen = LocalFrozenTime.current != null
    val level = args.level ?: 50
    Canvas(modifier) {
        val bw = 46.dp.toPx()
        val bh = 22.dp.toPx()
        val bx = size.width - bw - 4.dp.toPx()
        val by = (size.height - bh) / 2
        if (!frozen) {
            val fade = ((FLOW_MS - t) / 500f).coerceIn(0f, 1f)
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

/** Battery low: the battery drains to its level while its outline blinks red three times. */
@Composable
internal fun DrainScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_800)
    val level = args.level ?: 10
    Canvas(modifier) {
        val bw = 56.dp.toPx()
        val bh = 26.dp.toPx()
        val bx = (size.width - bw) / 2
        val by = (size.height - bh) / 2
        val drain = p.segment(0f, 0.6f)
        val blink = if (p < 1f) 0.45f + 0.55f * ((kotlin.math.cos(p * 6 * PI).toFloat() + 1f) / 2f) else 1f
        battery(bx, by, bw, bh, (0.45f - (0.45f - level / 100f) * drain).coerceAtLeast(0.02f), args.color, outlineAlpha = blink)
    }
}

/** Unplugged: the plug pulls away from the socket with a little spark. */
@Composable
internal fun UnplugScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_200).segment(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            drawRoundRect(args.color.copy(alpha = 0.8f), Offset(cx - 34.dp.toPx(), cy - 12.dp.toPx()), Size(8.dp.toPx(), 24.dp.toPx()), CornerRadius(3.dp.toPx()))
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
            tint = args.color,
            modifier = Modifier.size(30.dp).graphicsLayer {
                rotationZ = -90f + 14f * p
                translationX = (-14).dp.toPx() + 34.dp.toPx() * p
                alpha = 1f - 0.35f * p
            },
        )
    }
}

/**
 * Battery saver: a gauge needle eases down to "eco" while the battery shifts green to yellow
 * and a leaf sprouts beside it. Off runs it back up.
 */
@Composable
internal fun SaverScene(args: SceneArgs, on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(1_800)
    val swing = p.segment(0f, 0.65f)
    val leaf = p.segment(0.55f, 1f)
    val level = args.level ?: 60
    val from = if (on) CallGreen else SaverYellow
    val to = if (on) SaverYellow else CallGreen
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height * 0.42f
            val c = Offset(r + 4.dp.toPx(), size.height * 0.72f)
            val needle = if (on) 0.75f - 0.6f * swing else 0.15f + 0.45f * swing
            gauge(c, r, needle, Secondary, mix(from, to, swing))
            val bw = 44.dp.toPx()
            val bh = 20.dp.toPx()
            battery(size.width - bw - 6.dp.toPx(), (size.height - bh) / 2, bw, bh, level / 100f, mix(from, to, swing))
        }
        if (on) {
            Icon(
                Icons.Rounded.Spa,
                null,
                tint = CallGreen,
                modifier = Modifier.align(Alignment.TopCenter).size(18.dp).graphicsLayer {
                    scaleX = overshoot(leaf)
                    scaleY = overshoot(leaf)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            )
        }
    }
}

/**
 * Monster mode: the needle whips to the redline with an overshoot, a flame bursts behind it and
 * speed lines streak left once. Off eases the needle back and the flame shrinks to an ember.
 */
@Composable
internal fun MonsterScene(args: SceneArgs, on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(1_600)
    val whip = p.segment(0f, 0.45f)
    val flame = p.segment(0.25f, 0.7f)
    val streak = p.segment(0.35f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (on && streak in 0.01f..0.99f) {
                for (i in 0 until 4) {
                    val y = size.height * (0.25f + 0.17f * i)
                    val head = size.width * (1f - streak)
                    drawLine(MonsterRed.copy(alpha = 0.7f * (1f - streak)), Offset(head, y), Offset(head + size.width * 0.3f, y), 1.5.dp.toPx(), StrokeCap.Round)
                }
            }
            val r = size.height * 0.44f
            val c = Offset(size.width / 2, size.height * 0.74f)
            val needle = if (on) overshoot(whip, 0.12f) else 1f - 0.5f * whip
            gauge(c, r, needle, Secondary, Color.White, redline = MonsterRed)
        }
        val size = if (on) 0.4f + 0.6f * overshoot(flame) else 1f - 0.65f * flame
        Icon(
            Icons.Rounded.LocalFireDepartment,
            null,
            tint = if (on) MonsterRed else Secondary,
            modifier = Modifier.align(Alignment.CenterEnd).size(30.dp).graphicsLayer {
                scaleX = size
                scaleY = size
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        )
    }
}

/**
 * Flashlight: a warm cone opens from the torch across the card and a few motes drift through
 * it once. Off folds the cone back into the torch.
 */
@Composable
internal fun TorchScene(args: SceneArgs, on: Boolean, modifier: Modifier) {
    val p = rememberTimeline(1_700)
    val open = if (on) p.segment(0f, 0.55f) else 1f - p.segment(0f, 0.6f)
    val motes = p.segment(0.3f, 1f)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            val origin = Offset(24.dp.toPx(), size.height / 2)
            val far = size.width
            val spread = size.height * 0.62f * open
            if (open > 0.01f) {
                val cone = Path().apply {
                    moveTo(origin.x, origin.y - 3.dp.toPx())
                    lineTo(far, origin.y - spread)
                    lineTo(far, origin.y + spread)
                    lineTo(origin.x, origin.y + 3.dp.toPx())
                    close()
                }
                drawPath(cone, Brush.horizontalGradient(listOf(TorchWarm.copy(alpha = 0.6f * open), TorchWarm.copy(alpha = 0f)), startX = origin.x, endX = far))
            }
            if (on && motes in 0.01f..0.99f) {
                for (i in 0 until 5) {
                    val x = origin.x + (far - origin.x) * ((motes + i * 0.2f) % 1f)
                    val y = origin.y + sin(i * 2.1f + motes * 4).toFloat() * spread * 0.5f
                    drawCircle(Color.White.copy(alpha = 0.7f * (1f - motes)), 1.4.dp.toPx(), Offset(x, y))
                }
            }
        }
        Icon(
            if (on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            null,
            tint = if (on) TorchWarm else Secondary,
            modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = 90f },
        )
    }
}

private const val BUBBLES = 9
