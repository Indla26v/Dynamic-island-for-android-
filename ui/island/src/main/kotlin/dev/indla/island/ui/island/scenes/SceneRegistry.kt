package dev.indla.island.ui.island.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.SystemEvent
import dev.indla.island.ui.island.CallGreen

/**
 * Every glance's scene, in one place: adding a glance means adding a line here and a scene in
 * its family file (power, sky, connection, sound, security). All scenes play once and hold their
 * last frame.
 */
internal fun hasScene(event: SystemEvent) = event != SystemEvent.HEADPHONES_CONNECTED

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
        SystemEvent.DARK_MODE -> SkyScene(night = true, modifier)
        SystemEvent.LIGHT_MODE -> SkyScene(night = false, modifier)
        SystemEvent.DND_ON -> DndScene(args, on = true, modifier)
        SystemEvent.DND_OFF -> DndScene(args, on = false, modifier)
        SystemEvent.BEDTIME_ON -> BedtimeScene(on = true, modifier)
        SystemEvent.BEDTIME_OFF -> BedtimeScene(on = false, modifier)
        SystemEvent.AIRPLANE_ON -> AirplaneScene(on = true, modifier)
        SystemEvent.AIRPLANE_OFF -> AirplaneScene(on = false, modifier)
        SystemEvent.WIFI_CONNECTED -> WifiScene(on = true, modifier)
        SystemEvent.WIFI_DISCONNECTED -> WifiScene(on = false, modifier)
        SystemEvent.DATA_ON -> OnlineScene(modifier)
        SystemEvent.DATA_OFF -> OfflineScene(modifier)
        SystemEvent.LOCATION_ON -> LocationScene(on = true, modifier)
        SystemEvent.LOCATION_OFF -> LocationScene(on = false, modifier)
        SystemEvent.RINGER_NORMAL -> RingScene(args, modifier)
        SystemEvent.RINGER_VIBRATE -> VibrateScene(args, modifier)
        SystemEvent.RINGER_SILENT -> SilentScene(modifier)
        SystemEvent.HEADPHONES_CONNECTED -> AudioScene(args.device, connected = true, args.color, modifier)
        SystemEvent.HEADPHONES_DISCONNECTED -> AudioScene(args.device, connected = false, args.color, modifier)
        SystemEvent.UNLOCKED -> UnlockScene(args.face, ok = true, modifier)
        SystemEvent.AUTH_FAILED -> UnlockScene(args.face, ok = false, modifier)
        SystemEvent.CLIPBOARD -> ClipboardScene(args, modifier)
    }
}

/**
 * Copied: the copied text flies in as a chip, the clipboard's clip snaps down on it and a green
 * tick springs on.
 */
@Composable
private fun ClipboardScene(args: SceneArgs, modifier: Modifier) {
    val p = rememberTimeline(1_600)
    val fly = p.segment(0f, 0.45f)
    val clip = p.segment(0.4f, 0.6f)
    val tick = overshoot(p.segment(0.6f, 0.9f), 0.3f)
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        Canvas(Modifier.fillMaxSize()) {
            // The clip, snapping down.
            val boardX = size.width - 40.dp.toPx()
            drawRoundRect(args.color, Offset(boardX + 10.dp.toPx(), 4.dp.toPx() - 6.dp.toPx() * (1f - clip)), Size(16.dp.toPx(), 7.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
        Icon(Icons.Rounded.ContentPaste, null, tint = args.color, modifier = Modifier.size(36.dp))
        Text(
            args.label.take(14),
            color = Color.Black,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    // From the scene's left edge onto the clipboard, shrinking as it lands.
                    translationX = 70.dp.toPx() * fly
                    alpha = 1f - p.segment(0.55f, 0.7f)
                    val s = 1f - 0.4f * fly
                    scaleX = s
                    scaleY = s
                }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(Color.White),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(16.dp)
                .graphicsLayer {
                    scaleX = tick
                    scaleY = tick
                }
                .clip(CircleShape)
                .background(CallGreen),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(12.dp)) }
    }
}
