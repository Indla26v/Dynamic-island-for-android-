package dev.indla.island.ui.island.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.indla.island.model.SystemEvent
import dev.indla.island.ui.island.CallGreen

/**
 * Every glance's scene, in one place: adding a glance means adding a line here and a scene in
 * its family file (power, sky, connection, sound, security). All scenes play once and hold their
 * last frame.
 */
internal fun hasScene(event: SystemEvent) = event != SystemEvent.HEADPHONES_CONNECTED

/**
 * How wide each scene is, dp. The glance bar is sized from it (and the words left of the camera),
 * so a small scene makes a small bar; the scene sits flush with the bar's right edge.
 */
internal fun sceneWidth(event: SystemEvent): Float = when (event) {
    SystemEvent.WIFI_CONNECTED, SystemEvent.WIFI_DISCONNECTED, SystemEvent.WIFI_ON, SystemEvent.WIFI_OFF -> 64f
    SystemEvent.BLUETOOTH_ON, SystemEvent.BLUETOOTH_OFF -> 56f
    SystemEvent.DATA_ON, SystemEvent.DATA_OFF -> 52f
    SystemEvent.LOCATION_ON, SystemEvent.LOCATION_OFF -> 60f
    SystemEvent.AIRPLANE_ON, SystemEvent.AIRPLANE_OFF -> 104f
    SystemEvent.HOTSPOT_ON, SystemEvent.HOTSPOT_OFF -> 96f
    SystemEvent.LINK_ON, SystemEvent.LINK_OFF -> 84f
    SystemEvent.CHARGING, SystemEvent.FLASHLIGHT_ON, SystemEvent.FLASHLIGHT_OFF -> 112f
    SystemEvent.SAVER_ON, SystemEvent.SAVER_OFF -> 108f
    SystemEvent.UNPLUGGED -> 88f
    SystemEvent.BATTERY_LOW -> 64f
    SystemEvent.DARK_MODE, SystemEvent.LIGHT_MODE -> 100f
    SystemEvent.BEDTIME_ON, SystemEvent.BEDTIME_OFF -> 84f
    SystemEvent.MONSTER_ON, SystemEvent.MONSTER_OFF -> 88f
    SystemEvent.EYE_ON, SystemEvent.EYE_OFF -> 64f
    SystemEvent.UNLOCKED, SystemEvent.AUTH_FAILED -> 80f
    SystemEvent.RINGER_NORMAL, SystemEvent.RINGER_VIBRATE, SystemEvent.RINGER_SILENT -> 76f
    SystemEvent.DND_ON, SystemEvent.DND_OFF -> 64f
    SystemEvent.CLIPBOARD -> 64f
    SystemEvent.HEADPHONES_CONNECTED, SystemEvent.HEADPHONES_DISCONNECTED -> 56f
}

@Composable
internal fun SystemScene(event: SystemEvent, args: SceneArgs, modifier: Modifier) {
    when (event) {
        SystemEvent.CHARGING -> ChargingScene(args, modifier)
        SystemEvent.UNPLUGGED -> UnplugScene(args, modifier)
        SystemEvent.BATTERY_LOW -> DrainScene(args, modifier)
        SystemEvent.SAVER_ON -> SaverScene(args, on = true, modifier)
        SystemEvent.SAVER_OFF -> SaverScene(args, on = false, modifier)
        SystemEvent.MONSTER_ON -> MonsterScene(args, on = true, modifier)
        SystemEvent.MONSTER_OFF -> MonsterScene(args, on = false, modifier)
        SystemEvent.FLASHLIGHT_ON -> TorchScene(args, on = true, modifier)
        SystemEvent.FLASHLIGHT_OFF -> TorchScene(args, on = false, modifier)
        SystemEvent.DARK_MODE -> DayNightScene(night = true, args.color, modifier)
        SystemEvent.LIGHT_MODE -> DayNightScene(night = false, args.color, modifier)
        SystemEvent.DND_ON -> DndScene(on = true, args.color, modifier)
        SystemEvent.DND_OFF -> DndScene(on = false, args.color, modifier)
        SystemEvent.BEDTIME_ON -> BedtimeScene(on = true, args.color, modifier)
        SystemEvent.BEDTIME_OFF -> BedtimeScene(on = false, args.color, modifier)
        SystemEvent.AIRPLANE_ON -> AirplaneScene(on = true, args.color, modifier)
        SystemEvent.AIRPLANE_OFF -> AirplaneScene(on = false, args.color, modifier)
        SystemEvent.WIFI_CONNECTED -> WifiScene(on = true, joined = true, args.color, modifier)
        SystemEvent.WIFI_DISCONNECTED -> WifiScene(on = false, joined = false, args.color, modifier)
        SystemEvent.WIFI_ON -> WifiScene(on = true, joined = false, args.color, modifier)
        SystemEvent.WIFI_OFF -> WifiScene(on = false, joined = false, args.color, modifier)
        SystemEvent.BLUETOOTH_ON -> BluetoothScene(on = true, args.color, modifier)
        SystemEvent.BLUETOOTH_OFF -> BluetoothScene(on = false, args.color, modifier)
        SystemEvent.DATA_ON -> DataScene(on = true, args.color, modifier)
        SystemEvent.DATA_OFF -> DataScene(on = false, args.color, modifier)
        SystemEvent.LOCATION_ON -> LocationScene(on = true, args.color, modifier)
        SystemEvent.LOCATION_OFF -> LocationScene(on = false, args.color, modifier)
        SystemEvent.RINGER_NORMAL -> RingScene(args, modifier)
        SystemEvent.RINGER_VIBRATE -> VibrateScene(args, modifier)
        SystemEvent.RINGER_SILENT -> SilentScene(args, modifier)
        SystemEvent.HEADPHONES_CONNECTED -> AudioScene(args.device, connected = true, args.color, modifier)
        SystemEvent.HEADPHONES_DISCONNECTED -> AudioScene(args.device, connected = false, args.color, modifier)
        SystemEvent.UNLOCKED -> UnlockScene(args.face, ok = true, args.color, modifier)
        SystemEvent.AUTH_FAILED -> UnlockScene(args.face, ok = false, args.color, modifier)
        SystemEvent.CLIPBOARD -> ClipboardScene(args, modifier)
        SystemEvent.HOTSPOT_ON -> HotspotScene(on = true, args.color, modifier)
        SystemEvent.HOTSPOT_OFF -> HotspotScene(on = false, args.color, modifier)
        SystemEvent.EYE_ON -> EyeScene(on = true, args.color, modifier)
        SystemEvent.EYE_OFF -> EyeScene(on = false, args.color, modifier)
        SystemEvent.LINK_ON -> LinkScene(on = true, args.color, modifier)
        SystemEvent.LINK_OFF -> LinkScene(on = false, args.color, modifier)
    }
}

/**
 * Copied: the copied text flies in as a little chip and drops onto the clipboard, the clip snaps
 * down on it, its lines write themselves and a tick pops on the corner.
 */
@Composable
private fun ClipboardScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_700)
    Canvas(modifier) {
        val color = args.color
        val bw = 22.dp.toPx()
        val bh = 28.dp.toPx()
        val board = Offset(size.width - bw - 6.dp.toPx(), (size.height - bh) / 2 + 2.dp.toPx())
        drawRoundRect(color.copy(alpha = 0.22f), board, Size(bw, bh), CornerRadius(4.dp.toPx()))
        drawRoundRect(color, board, Size(bw, bh), CornerRadius(4.dp.toPx()), style = Stroke(1.8.dp.toPx()))
        // The clip, snapping down.
        val clip = overshoot(p.segment(0.45f, 0.62f), 0.4f)
        drawRoundRect(color, Offset(board.x + bw / 2 - 5.dp.toPx(), board.y - 3.dp.toPx() - 4.dp.toPx() * (1f - clip)), Size(10.dp.toPx(), 6.dp.toPx()), CornerRadius(2.dp.toPx()))
        // The chip, flying in from the left and landing on the board.
        val fly = p.segment(0f, 0.45f)
        if (fly < 1f) {
            val from = Offset(0f, size.height * 0.35f)
            val to = Offset(board.x + 3.dp.toPx(), board.y + bh * 0.3f)
            val at = quad(from, Offset((from.x + to.x) / 2, -size.height * 0.1f), to, fly)
            val k = 1f - 0.4f * fly
            drawRoundRect(color, at, Size(18.dp.toPx() * k, 8.dp.toPx() * k), CornerRadius(3.dp.toPx()))
            drawLine(Color.White, at + Offset(3.dp.toPx() * k, 4.dp.toPx() * k), at + Offset(14.dp.toPx() * k, 4.dp.toPx() * k), 1.2.dp.toPx(), StrokeCap.Round)
        }
        // Its text, written onto the board.
        for (i in 0 until 3) {
            val w = p.segment(0.5f + 0.1f * i, 0.65f + 0.1f * i)
            if (w > 0f) {
                val y = board.y + bh * (0.36f + 0.2f * i)
                val len = (bw - 8.dp.toPx()) * (if (i == 2) 0.6f else 1f) * w
                drawLine(Color.White, Offset(board.x + 4.dp.toPx(), y), Offset(board.x + 4.dp.toPx() + len, y), 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
        // The tick.
        val tick = overshoot(p.segment(0.78f, 1f), 0.4f)
        if (tick > 0f) {
            val c = Offset(board.x + bw, board.y + bh)
            val r = 6.5.dp.toPx() * tick
            drawCircle(Color.Black, r + 1.5.dp.toPx(), c)
            drawCircle(CallGreen, r, c)
            val path = Path().apply {
                moveTo(c.x - r * 0.45f, c.y)
                lineTo(c.x - r * 0.1f, c.y + r * 0.35f)
                lineTo(c.x + r * 0.5f, c.y - r * 0.35f)
            }
            drawPath(path, Color.White, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
