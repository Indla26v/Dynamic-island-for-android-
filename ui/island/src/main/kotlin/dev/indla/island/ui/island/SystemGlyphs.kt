package dev.indla.island.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BedtimeOff
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ModeNight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WifiTetheringOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.indla.island.model.SystemEvent
import kotlin.math.PI
import kotlin.math.sin

// ---- System glances --------------------------------------------------------------------------
// Each mode change has its own short motion, in the spirit of iOS: the plane flies in, the
// torch throws a glow, the battery fills, Monster mode flares, the sun spins into the moon,
// Wi-Fi arcs light up one by one, the clipboard gets a tick. All finish within about a second,
// except the torch's glow and Monster's flicker, which last only while the glance is shown.

val AirplaneOrange = Color(0xFFFF9F0A)
val SaverYellow = Color(0xFFFFD60A)
val NightIndigo = Color(0xFF5E5CE6)
val WifiBlue = Color(0xFF0A84FF)
val ClipTeal = Color(0xFF64D2FF)
val MonsterRed = Color(0xFFFF3B30)
val BedtimeTeal = Color(0xFF64D2FF)
val EyeAmber = Color(0xFFFFB74D)
private val TorchWarm = Color(0xFFFFE9A8)

@Composable
internal fun SystemGlyph(event: SystemEvent, color: Color, size: Dp, level: Int? = null) {
    when (event) {
        SystemEvent.AIRPLANE_ON -> AirplaneIn(color, size)
        SystemEvent.AIRPLANE_OFF -> IconGlyph(Icons.Rounded.AirplanemodeInactive, color, size) { p ->
            translationY = size.toPx() * 0.12f * p
            alpha = 1f - 0.3f * p
        }
        SystemEvent.FLASHLIGHT_ON -> Torch(on = true, color, size)
        SystemEvent.FLASHLIGHT_OFF -> Torch(on = false, color, size)
        SystemEvent.SAVER_ON, SystemEvent.SAVER_OFF -> BatteryFill(level ?: 50, color, size)
        SystemEvent.MONSTER_ON -> MonsterFlare(color, size)
        SystemEvent.MONSTER_OFF -> IconGlyph(Icons.Rounded.LocalFireDepartment, color, size) { p ->
            val s = 1f - 0.25f * p
            scaleX = s
            scaleY = s
            alpha = 1f - 0.4f * p
        }
        SystemEvent.DARK_MODE -> Horizon(color, size, night = true)
        SystemEvent.LIGHT_MODE -> Horizon(color, size, night = false)
        SystemEvent.WIFI_CONNECTED, SystemEvent.WIFI_ON -> WifiArcs(color, size, connected = true)
        SystemEvent.WIFI_DISCONNECTED, SystemEvent.WIFI_OFF -> WifiArcs(color, size, connected = false)
        SystemEvent.CLIPBOARD -> Clipboard(color, size)
        SystemEvent.RINGER_SILENT -> SilentBell(color, size)
        SystemEvent.LOCATION_ON -> PinDrop(color, size)
        SystemEvent.LOCATION_OFF -> IconGlyph(Icons.Rounded.LocationOff, color, size) { p ->
            val s = 1f - 0.2f * p
            scaleX = s
            scaleY = s
        }
        SystemEvent.DND_ON -> FocusMoon(color, size)
        SystemEvent.DND_OFF -> IconGlyph(Icons.Rounded.DoNotDisturbOff, color, size) { p -> alpha = 1f - 0.4f * p }
        SystemEvent.BEDTIME_ON -> Sleepy(color, size)
        SystemEvent.BEDTIME_OFF -> IconGlyph(Icons.Rounded.BedtimeOff, color, size) { p ->
            translationY = -size.toPx() * 0.1f * p
        }
        SystemEvent.DATA_ON -> DataArrows(color, size, on = true)
        SystemEvent.DATA_OFF -> DataArrows(color, size, on = false)
        SystemEvent.UNLOCKED -> Unlocked(color, size, face = level == FACE)
        SystemEvent.AUTH_FAILED -> NotRecognised(color, size, face = level == FACE)
        else -> LegacyGlyph(event, color, size)
    }
}

/** Unlock glances carry this in [LiveContent.System.level] when the face did it. */
const val FACE = 1
private const val BREATHE_MS = 4_000L

/** The pin drops in with a bounce, and a ring ripples out where it lands. */
@Composable
private fun PinDrop(color: Color, size: Dp) {
    val drop = rememberSpringIn(-1f, 0f, 0.42f, Spring.StiffnessMediumLow)
    val ripple = rememberReveal(1_600)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 1.4f)) {
            val r = this.size.width * 0.18f + this.size.width * 0.3f * ripple
            drawOval(
                color.copy(alpha = 0.6f * (1f - ripple)),
                topLeft = Offset(this.size.width / 2 - r, this.size.height * 0.78f - r * 0.32f),
                size = Size(2 * r, r * 0.64f),
                style = Stroke(1.5.dp.toPx()),
            )
        }
        Icon(Icons.Rounded.LocationOn, null, tint = color, modifier = Modifier.size(size).graphicsLayer { translationY = size.toPx() * drop })
    }
}

/** Do Not Disturb: the moon in its purple disc pops in, and two rings breathe out from it. */
@Composable
private fun FocusMoon(color: Color, size: Dp) {
    val pop = rememberSpringIn(0.4f, 1f, 0.5f, Spring.StiffnessMediumLow)
    val now = rememberOnceClock(1_800)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 1.5f)) {
            for (k in 0..1) {
                val t = ((now % 2_000) / 2_000f + k * 0.5f) % 1f
                drawCircle(color.copy(alpha = 0.45f * (1f - t)), this.size.minDimension * (0.3f + 0.2f * t), style = Stroke(1.5.dp.toPx()))
            }
        }
        Box(
            Modifier.size(size * 0.86f).graphicsLayer {
                scaleX = pop
                scaleY = pop
            }.clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.ModeNight, null, tint = Color.White, modifier = Modifier.size(size * 0.52f)) }
    }
}

/** Bedtime: the icon rocks gently while little z's float up and fade. */
@Composable
private fun Sleepy(color: Color, size: Dp) {
    val now = rememberOnceClock(1_800)
    Box(Modifier.size(size)) {
        Icon(
            Icons.Rounded.Bedtime,
            null,
            tint = color,
            modifier = Modifier.size(size).graphicsLayer {
                rotationZ = 8f * sin((now % 2_400) / 2_400f * 2 * PI).toFloat()
            },
        )
        Canvas(Modifier.size(size)) {
            for (k in 0..2) {
                val t = ((now % 2_100) / 2_100f + k / 3f) % 1f
                val x = this.size.width * (0.72f + 0.12f * t)
                val y = this.size.height * (0.35f - 0.45f * t)
                val z = this.size.minDimension * (0.12f + 0.06f * k)
                val a = (if (t < 0.2f) t / 0.2f else 1f - (t - 0.2f) / 0.8f).coerceIn(0f, 1f)
                // A small "z": top bar, diagonal, bottom bar.
                val c = color.copy(alpha = a)
                val w = 1.4.dp.toPx()
                drawLine(c, Offset(x, y), Offset(x + z, y), w, StrokeCap.Round)
                drawLine(c, Offset(x + z, y), Offset(x, y + z), w, StrokeCap.Round)
                drawLine(c, Offset(x, y + z), Offset(x + z, y + z), w, StrokeCap.Round)
            }
        }
    }
}

/** Mobile data: up and down arrows trade places a few times; off, they grey out under a slash. */
@Composable
private fun DataArrows(color: Color, size: Dp, on: Boolean) {
    val p = rememberReveal(1_800)
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = this.size.minDimension * 0.1f
        val shift = if (on) sin(p * 3 * 2 * PI).toFloat() * h * 0.06f * (1f - p * 0.6f) else 0f
        val c = if (on) color else color.copy(alpha = 0.55f)
        fun arrow(x: Float, up: Boolean, dy: Float) {
            val top = h * 0.2f + dy
            val bottom = h * 0.8f + dy
            drawLine(c, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
            val tip = if (up) top else bottom
            val dir = if (up) 1f else -1f
            drawLine(c, Offset(x, tip), Offset(x - w * 0.12f, tip + dir * h * 0.14f), stroke, StrokeCap.Round)
            drawLine(c, Offset(x, tip), Offset(x + w * 0.12f, tip + dir * h * 0.14f), stroke, StrokeCap.Round)
        }
        arrow(w * 0.36f, up = true, dy = -shift)
        arrow(w * 0.64f, up = false, dy = shift)
        if (!on && p > 0.4f) {
            val q = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            drawLine(color, Offset(w * 0.18f, h * 0.18f), Offset(w * (0.18f + 0.64f * q), h * (0.18f + 0.64f * q)), stroke, StrokeCap.Round)
        }
    }
}

/** Unlocked: the face or fingerprint fills in green and a lock pops open beside it. */
@Composable
private fun Unlocked(color: Color, size: Dp, face: Boolean) {
    val p = rememberReveal(900)
    val open = rememberSpringIn(0f, 1f, 0.5f, Spring.StiffnessMediumLow, delayMs = 350)
    Box(Modifier.size(size)) {
        Icon(
            if (face) Icons.Rounded.Face else Icons.Rounded.Fingerprint,
            null,
            tint = color.copy(alpha = 0.35f + 0.65f * p),
            modifier = Modifier.size(size).graphicsLayer {
                val s = 0.85f + 0.15f * p
                scaleX = s
                scaleY = s
            },
        )
        Icon(
            Icons.Rounded.LockOpen,
            null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).offset(size * 0.14f, size * 0.1f).size(size * 0.48f).graphicsLayer {
                scaleX = open
                scaleY = open
            },
        )
    }
}

/** Not recognised: the symbol turns red and shakes side to side, like a wrong passcode. */
@Composable
private fun NotRecognised(color: Color, size: Dp, face: Boolean) {
    val p = rememberReveal(900)
    Icon(
        if (face) Icons.Rounded.Face else Icons.Rounded.Fingerprint,
        null,
        tint = color,
        modifier = Modifier.size(size).graphicsLayer {
            translationX = size.toPx() * 0.18f * sin(p * 5 * 2 * PI).toFloat() * (1f - p)
        },
    )
}

/** An icon with a one-shot motion driven by [transform] over progress 0..1. */
@Composable
private fun IconGlyph(icon: ImageVector, color: Color, size: Dp, durationMs: Int = 1_100, transform: androidx.compose.ui.graphics.GraphicsLayerScope.(Float) -> Unit) {
    val p = rememberReveal(durationMs)
    Icon(icon, null, tint = color, modifier = Modifier.size(size).graphicsLayer { transform(p) })
}

/** The plane glides in from the left, leaving two fading contrails. */
@Composable
private fun AirplaneIn(color: Color, size: Dp) {
    val p = rememberReveal(1_300)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val trail = (1f - p) * this.size.width * 0.9f
            if (trail > 1f) {
                for (dy in listOf(-0.12f, 0.12f)) {
                    val y = this.size.height * (0.5f + dy)
                    drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.55f)), startX = -trail, endX = this.size.width * 0.3f),
                        Offset(-trail, y),
                        Offset(this.size.width * 0.3f, y),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Icon(
            Icons.Rounded.AirplanemodeActive,
            null,
            tint = color,
            modifier = Modifier.size(size).graphicsLayer {
                translationX = -size.toPx() * 1.1f * (1f - p)
                rotationZ = 90f - 12f * (1f - p)
                alpha = p.coerceAtLeast(0.15f)
            },
        )
    }
}

/** On: a warm glow blooms behind the torch and breathes. Off: the glow folds away. */
@Composable
private fun Torch(on: Boolean, color: Color, size: Dp) {
    val p = rememberReveal(900)
    // A lit torch stays on the island; it breathes for a few seconds, then holds still.
    var breathing by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        kotlinx.coroutines.delay(BREATHE_MS)
        breathing = false
    }
    val now = rememberOnceClock(1_600)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 1.6f)) {
            val breathe = if (breathing) 0.85f + 0.15f * sin((now % 1_600) / 1_600f * 2 * PI).toFloat() else 1f
            val strength = if (on) p * breathe else 1f - p
            if (strength > 0.01f) {
                drawCircle(
                    Brush.radialGradient(listOf(TorchWarm.copy(alpha = 0.55f * strength), Color.Transparent), radius = this.size.minDimension / 2),
                    radius = this.size.minDimension / 2,
                )
            }
        }
        Icon(if (on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, null, tint = color, modifier = Modifier.size(size))
    }
}

/** A battery drawn in place, filling up to the level: yellow for the saver, green when it's off. */
@Composable
private fun BatteryFill(level: Int, color: Color, size: Dp) {
    val p = rememberReveal(1_500)
    Canvas(Modifier.size(size)) {
        val w = this.size.width * 0.82f
        val h = this.size.height * 0.46f
        val left = (this.size.width - w) / 2 - this.size.width * 0.04f
        val top = (this.size.height - h) / 2
        val stroke = 1.6.dp.toPx()
        val r = h * 0.3f
        drawRoundRect(color.copy(alpha = 0.9f), Offset(left, top), Size(w, h), CornerRadius(r), style = Stroke(stroke))
        drawRoundRect(color.copy(alpha = 0.9f), Offset(left + w + stroke, top + h * 0.3f), Size(stroke * 1.4f, h * 0.4f), CornerRadius(stroke))
        val inset = stroke * 1.8f
        val fill = (w - 2 * inset) * (level.coerceIn(0, 100) / 100f) * p
        if (fill > 0f) {
            drawRoundRect(color, Offset(left + inset, top + inset), Size(fill, h - 2 * inset), CornerRadius(r * 0.6f))
        }
    }
}

/** Monster mode: the flame pops with an overshoot, then flickers over a red glow. */
@Composable
private fun MonsterFlare(color: Color, size: Dp) {
    val pop = rememberSpringIn(0.3f, 1f, 0.4f, Spring.StiffnessMedium)
    val now = rememberOnceClock(1_800)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 1.5f)) {
            val flicker = 0.75f + 0.25f * sin((now % 300) / 300f * 2 * PI).toFloat()
            drawCircle(
                Brush.radialGradient(listOf(MonsterRed.copy(alpha = 0.45f * flicker), Color.Transparent), radius = this.size.minDimension / 2),
                radius = this.size.minDimension / 2,
            )
        }
        Icon(
            Icons.Rounded.LocalFireDepartment,
            null,
            tint = color,
            modifier = Modifier.size(size).graphicsLayer {
                val jitter = 1f + 0.04f * sin((now % 180) / 180f * 2 * PI).toFloat()
                scaleX = pop * jitter
                scaleY = pop * jitter
                transformOrigin = TransformOrigin(0.5f, 0.9f)
            },
        )
    }
}

/**
 * Sunset into dark mode: the sun sinks below the horizon as the moon rises, and stars come out.
 * In reverse for light mode: the moon sets, the sun comes up and its rays turn.
 */
@Composable
private fun Horizon(color: Color, size: Dp, night: Boolean) {
    val p = rememberReveal(1_600)
    val now = rememberOnceClock(1_800)
    Box(Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val horizon = this.size.height * 0.86f
            val r = this.size.minDimension * 0.2f
            val sink = if (night) p else 1f - p
            // The sun, going down (night) or coming up (day).
            val sunY = horizon - r * 1.3f + (r * 3f) * sink
            clipRect(bottom = horizon) {
                drawCircle(SaverYellow.copy(alpha = 1f - 0.5f * sink), r, Offset(this.size.width * 0.32f, sunY))
            }
            drawLine(Color.White.copy(alpha = 0.5f), Offset(this.size.width * 0.05f, horizon), Offset(this.size.width * 0.95f, horizon), 1.2.dp.toPx(), StrokeCap.Round)
            if (night && p > 0.6f) {
                val t = (now % 1_400) / 1_400f
                val a = ((p - 0.6f) / 0.4f) * (0.4f + 0.6f * ((sin(t * 2 * PI).toFloat() + 1f) / 2f))
                drawCircle(Color.White.copy(alpha = a), this.size.minDimension * 0.04f, Offset(this.size.width * 0.12f, this.size.height * 0.15f))
                drawCircle(Color.White.copy(alpha = a * 0.7f), this.size.minDimension * 0.03f, Offset(this.size.width * 0.3f, this.size.height * 0.05f))
            }
        }
        val rise = if (night) p else 1f - p
        Icon(
            if (night) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
            null,
            tint = color,
            modifier = Modifier.align(Alignment.TopEnd).size(size * 0.62f).graphicsLayer {
                translationY = size.toPx() * 0.5f * (1f - (if (night) rise else 1f - rise))
                alpha = if (night) rise else 1f
                rotationZ = if (night) -30f * (1f - p) else 180f * p
            },
        )
    }
}

/** Wi-Fi arcs light up from the dot outwards; disconnecting dims them from the outside in. */
@Composable
private fun WifiArcs(color: Color, size: Dp, connected: Boolean) {
    val p = rememberReveal(1_500)
    Canvas(Modifier.size(size)) {
        val cx = this.size.width / 2
        val base = this.size.height * 0.82f
        val stroke = this.size.minDimension * 0.1f
        fun lit(i: Int): Float {
            // i = 0 is the dot, 3 the outer arc.
            val step = if (connected) (p * 4 - i).coerceIn(0f, 1f) else (1f - (p * 4 - (3 - i))).coerceIn(0f, 1f)
            return 0.2f + 0.8f * step
        }
        drawCircle(color.copy(alpha = lit(0)), stroke * 0.75f, Offset(cx, base))
        for (i in 1..3) {
            val r = this.size.minDimension * (0.2f + 0.2f * i)
            drawArc(
                color.copy(alpha = lit(i)),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, base - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (!connected && p > 0.6f) {
            val q = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
            drawLine(color, Offset(this.size.width * 0.2f, this.size.height * 0.2f), Offset(this.size.width * (0.2f + 0.6f * q), this.size.height * (0.2f + 0.6f * q)), stroke * 0.8f, StrokeCap.Round)
        }
    }
}

/** The clipboard, and a green tick that springs onto its corner. */
@Composable
private fun Clipboard(color: Color, size: Dp) {
    val tick = rememberSpringIn(0f, 1f, 0.45f, Spring.StiffnessMediumLow, delayMs = 180)
    val fly = rememberReveal(500)
    Box(Modifier.size(size)) {
        Icon(Icons.Rounded.ContentPaste, null, tint = color, modifier = Modifier.size(size))
        // The copied text, as a little card, flies onto the clipboard.
        Canvas(Modifier.size(size)) {
            if (fly < 1f) {
                val w = this.size.width * (0.5f - 0.15f * fly)
                val x = this.size.width * (-0.6f + 0.85f * fly)
                val y = this.size.height * (-0.3f + 0.7f * fly)
                drawRoundRect(Color.White.copy(alpha = 1f - fly * 0.6f), Offset(x, y), Size(w, w * 0.62f), CornerRadius(w * 0.12f))
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(size * 0.12f, size * 0.12f)
                .size(size * 0.5f)
                .graphicsLayer {
                    scaleX = tick
                    scaleY = tick
                }
                .clip(CircleShape)
                .background(CallGreen),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(size * 0.36f)) }
    }
}

/** Like iOS: the bell rings once, then falls silent with a slash. */
@Composable
private fun SilentBell(color: Color, size: Dp) {
    val p = rememberReveal(1_500)
    val swing = sin(p * 6 * PI).toFloat() * (1f - p) * 20f
    Icon(
        if (p < 0.55f) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
        null,
        tint = color,
        modifier = Modifier.size(size).graphicsLayer {
            rotationZ = swing
            transformOrigin = TransformOrigin(0.5f, 0.1f)
        },
    )
}

/** The earlier glances: bell swings, phone buzzes, bolt pops, headphones nod. */
@Composable
private fun LegacyGlyph(event: SystemEvent, color: Color, size: Dp) {
    val shake = rememberShake()
    val flash = rememberReveal(1_000)
    // Everything without a motion of its own pops in with a little spring.
    val pop = rememberSpringIn(0.5f, 1f, 0.45f, Spring.StiffnessMediumLow)
    Icon(
        systemIcon(event),
        null,
        tint = color,
        modifier = Modifier.size(size).graphicsLayer {
            when (event) {
                SystemEvent.RINGER_NORMAL -> {
                    rotationZ = 22f * shake
                    transformOrigin = TransformOrigin(0.5f, 0.1f)
                }
                SystemEvent.RINGER_VIBRATE -> translationX = 3.dp.toPx() * sin(flash * 18 * PI).toFloat() * (1f - flash)
                SystemEvent.CHARGING -> {
                    val pop = 1f + 0.35f * sin(flash * PI).toFloat()
                    scaleX = pop
                    scaleY = pop
                }
                SystemEvent.HEADPHONES_CONNECTED, SystemEvent.HEADPHONES_DISCONNECTED -> rotationZ = 10f * shake
                else -> {
                    scaleX = pop
                    scaleY = pop
                    alpha = ((pop - 0.5f) * 2f).coerceIn(0f, 1f)
                }
            }
        },
    )
}

internal fun systemIcon(event: SystemEvent): ImageVector = when (event) {
    SystemEvent.CHARGING -> Icons.Rounded.Bolt
    SystemEvent.UNPLUGGED -> Icons.Rounded.BatteryStd
    SystemEvent.BATTERY_LOW -> Icons.Rounded.BatteryAlert
    SystemEvent.RINGER_NORMAL -> Icons.Rounded.NotificationsActive
    SystemEvent.RINGER_VIBRATE -> Icons.Rounded.Vibration
    SystemEvent.RINGER_SILENT -> Icons.Rounded.NotificationsOff
    SystemEvent.HEADPHONES_CONNECTED, SystemEvent.HEADPHONES_DISCONNECTED -> Icons.Rounded.Headphones
    SystemEvent.AIRPLANE_ON -> Icons.Rounded.AirplanemodeActive
    SystemEvent.AIRPLANE_OFF -> Icons.Rounded.AirplanemodeInactive
    SystemEvent.FLASHLIGHT_ON -> Icons.Rounded.FlashlightOn
    SystemEvent.FLASHLIGHT_OFF -> Icons.Rounded.FlashlightOff
    SystemEvent.SAVER_ON, SystemEvent.SAVER_OFF -> Icons.Rounded.BatteryStd
    SystemEvent.MONSTER_ON, SystemEvent.MONSTER_OFF -> Icons.Rounded.LocalFireDepartment
    SystemEvent.DARK_MODE -> Icons.Rounded.DarkMode
    SystemEvent.LIGHT_MODE -> Icons.Rounded.LightMode
    SystemEvent.WIFI_CONNECTED, SystemEvent.WIFI_DISCONNECTED, SystemEvent.WIFI_ON -> Icons.Rounded.Wifi
    SystemEvent.WIFI_OFF -> Icons.Rounded.WifiOff
    SystemEvent.BLUETOOTH_ON -> Icons.Rounded.Bluetooth
    SystemEvent.BLUETOOTH_OFF -> Icons.Rounded.BluetoothDisabled
    SystemEvent.CLIPBOARD -> Icons.Rounded.ContentPaste
    SystemEvent.LOCATION_ON -> Icons.Rounded.LocationOn
    SystemEvent.LOCATION_OFF -> Icons.Rounded.LocationOff
    SystemEvent.DND_ON -> Icons.Rounded.DoNotDisturbOn
    SystemEvent.DND_OFF -> Icons.Rounded.DoNotDisturbOff
    SystemEvent.BEDTIME_ON -> Icons.Rounded.Bedtime
    SystemEvent.BEDTIME_OFF -> Icons.Rounded.BedtimeOff
    SystemEvent.DATA_ON, SystemEvent.DATA_OFF -> Icons.Rounded.SwapVert
    SystemEvent.UNLOCKED -> Icons.Rounded.LockOpen
    SystemEvent.AUTH_FAILED -> Icons.Rounded.Fingerprint
    SystemEvent.HOTSPOT_ON -> Icons.Rounded.WifiTethering
    SystemEvent.HOTSPOT_OFF -> Icons.Rounded.WifiTetheringOff
    SystemEvent.EYE_ON, SystemEvent.EYE_OFF -> Icons.Rounded.Visibility
    SystemEvent.LINK_ON -> Icons.Rounded.Link
    SystemEvent.LINK_OFF -> Icons.Rounded.LinkOff
}

/** As [systemColor], with the battery's own level: low battery is orange, red at 10% or below. */
internal fun systemColor(system: dev.indla.island.model.LiveContent.System, accent: Color): Color =
    if (system.event == SystemEvent.BATTERY_LOW && (system.level ?: 0) > 10) TimerOrange else systemColor(system.event, accent)

internal fun systemColor(event: SystemEvent, accent: Color): Color = when (event) {
    SystemEvent.CHARGING, SystemEvent.SAVER_OFF -> CallGreen
    SystemEvent.BATTERY_LOW, SystemEvent.RINGER_SILENT -> DeclineRed
    SystemEvent.AIRPLANE_ON -> AirplaneOrange
    SystemEvent.FLASHLIGHT_ON -> TorchWarm
    SystemEvent.SAVER_ON, SystemEvent.LIGHT_MODE -> SaverYellow
    SystemEvent.MONSTER_ON -> MonsterRed
    SystemEvent.DARK_MODE -> NightIndigo
    SystemEvent.WIFI_CONNECTED, SystemEvent.WIFI_ON, SystemEvent.BLUETOOTH_ON -> WifiBlue
    SystemEvent.CLIPBOARD -> ClipTeal
    SystemEvent.LOCATION_ON -> WifiBlue
    SystemEvent.DND_ON -> NightIndigo
    SystemEvent.BEDTIME_ON -> BedtimeTeal
    SystemEvent.DATA_ON, SystemEvent.UNLOCKED -> CallGreen
    SystemEvent.AUTH_FAILED -> DeclineRed
    SystemEvent.HOTSPOT_ON, SystemEvent.LINK_ON -> WifiBlue
    SystemEvent.EYE_ON -> EyeAmber
    SystemEvent.HOTSPOT_OFF, SystemEvent.EYE_OFF, SystemEvent.LINK_OFF -> Secondary
    SystemEvent.WIFI_OFF, SystemEvent.BLUETOOTH_OFF -> Secondary
    SystemEvent.LOCATION_OFF, SystemEvent.DND_OFF, SystemEvent.BEDTIME_OFF, SystemEvent.DATA_OFF -> Secondary
    SystemEvent.UNPLUGGED, SystemEvent.HEADPHONES_DISCONNECTED, SystemEvent.AIRPLANE_OFF,
    SystemEvent.FLASHLIGHT_OFF, SystemEvent.MONSTER_OFF, SystemEvent.WIFI_DISCONNECTED -> Secondary
    else -> accent
}

internal fun systemTitle(event: SystemEvent): String = when (event) {
    SystemEvent.CHARGING -> "Charging"
    SystemEvent.UNPLUGGED -> "Unplugged"
    SystemEvent.BATTERY_LOW -> "Battery low"
    SystemEvent.RINGER_NORMAL -> "Ring"
    SystemEvent.RINGER_VIBRATE -> "Vibrate"
    SystemEvent.RINGER_SILENT -> "Silent mode"
    SystemEvent.HEADPHONES_CONNECTED -> "Connected"
    SystemEvent.HEADPHONES_DISCONNECTED -> "Disconnected"
    SystemEvent.AIRPLANE_ON, SystemEvent.AIRPLANE_OFF -> "Airplane mode"
    SystemEvent.FLASHLIGHT_ON, SystemEvent.FLASHLIGHT_OFF -> "Flashlight"
    SystemEvent.SAVER_ON, SystemEvent.SAVER_OFF -> "Battery saver"
    SystemEvent.MONSTER_ON, SystemEvent.MONSTER_OFF -> "Monster mode"
    SystemEvent.DARK_MODE -> "Dark mode"
    SystemEvent.LIGHT_MODE -> "Light mode"
    SystemEvent.WIFI_CONNECTED, SystemEvent.WIFI_DISCONNECTED, SystemEvent.WIFI_ON, SystemEvent.WIFI_OFF -> "Wi-Fi"
    SystemEvent.CLIPBOARD -> "Copied"
    SystemEvent.LOCATION_ON, SystemEvent.LOCATION_OFF -> "Location"
    SystemEvent.DND_ON, SystemEvent.DND_OFF -> "Do Not Disturb"
    SystemEvent.BEDTIME_ON, SystemEvent.BEDTIME_OFF -> "Bedtime"
    SystemEvent.DATA_ON, SystemEvent.DATA_OFF -> "Mobile data"
    SystemEvent.UNLOCKED -> "Unlocked"
    SystemEvent.AUTH_FAILED -> "No match"
    SystemEvent.HOTSPOT_ON, SystemEvent.HOTSPOT_OFF -> "Hotspot"
    SystemEvent.EYE_ON, SystemEvent.EYE_OFF -> "Eye protection"
    SystemEvent.LINK_ON, SystemEvent.LINK_OFF -> "Link to Windows"
    SystemEvent.BLUETOOTH_ON, SystemEvent.BLUETOOTH_OFF -> "Bluetooth"
}
