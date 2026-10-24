package dev.indla.island.ui.island

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.ActionRole
import dev.indla.island.model.CallState
import dev.indla.island.model.IslandAction
import dev.indla.island.model.IslandItem
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandUiState
import dev.indla.island.model.ItemKind
import dev.indla.island.model.LiveContent
import dev.indla.island.model.SystemEvent
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal val Secondary = Color(0xA6FFFFFF)
private val ButtonFill = Color(0x2EFFFFFF)
private val Tabular = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

@Composable
internal fun IslandContent(
    state: IslandUiState,
    layout: IslandLayout,
    rect: IslandRect,
    accent: Color,
    registry: HitRegistry?,
    drawReplyHint: Boolean,
) {
    val item = state.current ?: return
    val itemAccent = item.accentArgb?.let { Color(it) } ?: accent
    when (state.mode) {
        IslandMode.REST -> Unit
        IslandMode.COMPACT, IslandMode.SPLIT -> CompactContent(item, layout, rect, itemAccent)
        IslandMode.EXPANDED -> ExpandedContent(state, item, layout, rect, itemAccent, registry, drawReplyHint)
    }
}

private val IslandItem.isHidden get() = kind == ItemKind.HIDDEN_BY_ANDROID || kind == ItemKind.HIDDEN_ON_LOCKSCREEN

// ---- Compact -----------------------------------------------------------------------------------

/** Leading glance left of the camera, trailing glance right of it. */
@Composable
private fun CompactContent(item: IslandItem, layout: IslandLayout, rect: IslandRect, accent: Color) {
    val h = rect.height
    val icon = (h - 10f).coerceAtMost(26f)
    val sideWidth = ((rect.width - layout.cameraGap) / 2 - h / 2).coerceAtLeast(0f)
    val inset = ((h - icon) / 2).dp
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = inset).width((sideWidth + h / 2).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { CompactLeading(item, icon.dp, accent) }
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = (h / 2 - 2f).coerceAtLeast(6f).dp).width(sideWidth.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) { CompactTrailing(item, accent, layout.minimal) }
    }
}

@Composable
private fun RowScope.CompactLeading(item: IslandItem, size: Dp, accent: Color) {
    when (val live = item.live) {
        is LiveContent.Media -> Art(item.avatarKey, item.packageName, size, RoundedCornerShape(7.dp))
        is LiveContent.Call -> if (live.state == CallState.ONGOING) {
            val now by rememberNow()
            Icon(Icons.Rounded.Call, null, tint = CallGreen, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(formatDuration(now - live.startedAtMs), color = CallGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = Tabular, maxLines = 1)
        } else {
            ItemAvatar(item, item.avatarKey, size)
        }
        is LiveContent.System -> if (live.event == SystemEvent.HEADPHONES_CONNECTED) {
            Icon(headphonesIcon(live), null, tint = Color.White, modifier = Modifier.size(size * 0.85f))
        } else {
            SystemGlyph(live.event, systemColor(live.event, accent), size * 0.85f, live.level)
        }
        is LiveContent.Progress -> if (live.navigation) TurnArrow(item, size) else ItemIcon(item, size)
        is LiveContent.Timer -> if (live.recording) {
            RecordDot(live.baseMs != null, size * 0.7f)
            Spacer(Modifier.width(4.dp))
            RecordingWave(live.baseMs, Modifier.size(30.dp, size * 0.7f), playhead = 1f, bar = 1.5.dp, step = 3.dp)
        } else {
            val tick = tickScale(live.baseMs != null)
            Icon(
                Icons.Rounded.Timer,
                null,
                tint = TimerOrange,
                modifier = Modifier.size(size * 0.85f).graphicsLayer {
                    scaleX = tick
                    scaleY = tick
                },
            )
        }
        null -> ItemAvatar(item, item.avatarKey, size)
    }
}

/** How many notifications a stack holds: a small pill in the app's colour. */
@Composable
private fun CountBadge(count: Int, accent: Color, missed: Boolean) {
    Row(
        Modifier
            .height(18.dp)
            .clip(CircleShape)
            .background(accent)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (accent.luminance() > 0.5f) Color.Black else Color.White
        if (missed) {
            Icon(Icons.Rounded.History, null, tint = ink, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
        }
        Text("$count", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = Tabular, maxLines = 1)
    }
}

@Composable
private fun RowScope.CompactTrailing(item: IslandItem, accent: Color, minimal: Boolean) {
    when (val live = item.live) {
        is LiveContent.Media -> WaveBars(accent, live.playing, Modifier.size(22.dp, 16.dp))
        is LiveContent.Call -> when (live.state) {
            CallState.ONGOING -> VoiceWave(CallGreen, Modifier.size(24.dp, 16.dp))
            else -> {
                val angle = ringingAngle()
                Icon(Icons.Rounded.Call, null, tint = CallGreen, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = angle })
                Spacer(Modifier.width(4.dp))
                GlanceText(item.glance, CallGreen)
            }
        }
        is LiveContent.Progress -> if (live.navigation) {
            // The distance to the next turn, large and white: readable at a glance while driving.
            Text(item.glance, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = Tabular)
        } else {
            if (item.glance.isNotEmpty() && !minimal) {
                GlanceText(item.glance, accent)
                Spacer(Modifier.width(6.dp))
            }
            ProgressRing(live.fraction, accent, Modifier.size(18.dp))
        }
        is LiveContent.Timer -> {
            val now by rememberNow()
            GlanceText(live.display(now), if (live.recording) RecordRed else TimerOrange)
        }
        is LiveContent.System -> {
            val color = systemColor(live.event, accent)
            if (live.event == SystemEvent.CLIPBOARD) {
                GlanceText("Copied", color)
            } else if (live.event == SystemEvent.UNLOCKED || live.event == SystemEvent.AUTH_FAILED) {
                GlanceText(if (live.event == SystemEvent.UNLOCKED) "Unlocked" else "Retry", color)
            } else if (live.event == SystemEvent.RINGER_SILENT || live.event == SystemEvent.RINGER_VIBRATE || live.event == SystemEvent.RINGER_NORMAL) {
                GlanceText(systemTitle(live.event).removeSuffix(" mode"), color)
            } else if (live.event == SystemEvent.SAVER_ON || live.event == SystemEvent.SAVER_OFF) {
                GlanceText(if (live.event == SystemEvent.SAVER_ON) "Saver on" else "Saver off", color)
            } else if (live.event == SystemEvent.HEADPHONES_CONNECTED && live.level != null) {
                BatteryRing(live.level!!, 22.dp)
            } else if (live.level != null) {
                GlanceText(live.label, color)
                Spacer(Modifier.width(6.dp))
                ProgressRing(live.level!! / 100f, color, Modifier.size(18.dp))
            } else {
                GlanceText(live.label, color)
            }
        }
        null -> {
            val stack = item.stack
            // Who on the left (the contact's photo), which app on the right (its logo).
            val logoRight = item.avatarKey != null && !isIslandPackage(item.packageName) && !item.isHidden
            when {
                logoRight -> {
                    if (stack != null) {
                        CountBadge(stack.size, accent, stack.missed)
                        Spacer(Modifier.width(6.dp))
                    }
                    AppIconImage(item.packageName, if (minimal) 18.dp else 22.dp)
                }
                minimal -> Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                stack != null -> {
                    if (!stack.missed) {
                        Box(Modifier.weight(1f, fill = false)) { GlanceText(item.glance, accent) }
                        Spacer(Modifier.width(6.dp))
                    }
                    CountBadge(stack.size, accent, stack.missed)
                }
                else -> GlanceText(item.glance, accent)
            }
        }
    }
}

@Composable
private fun GlanceText(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Tabular)
}

/** Content of a bubble beside the pill in the split state. */
@Composable
internal fun BubbleContent(item: IslandItem, size: Dp, accent: Color) {
    val itemAccent = item.accentArgb?.let { Color(it) } ?: accent
    val inner = size * 0.72f
    when (val live = item.live) {
        is LiveContent.Media -> Art(item.avatarKey, item.packageName, inner, CircleShape)
        is LiveContent.Call -> Box(Modifier.size(inner).clip(CircleShape).background(CallGreen), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(inner * 0.6f))
        }
        is LiveContent.Progress -> if (live.navigation) {
            TurnArrow(item, inner)
        } else {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(live.fraction, itemAccent, Modifier.size(inner), stroke = 3.dp)
                ItemIcon(item, inner * 0.55f)
            }
        }
        is LiveContent.Timer -> Icon(Icons.Rounded.Timer, null, tint = TimerOrange, modifier = Modifier.size(inner * 0.7f))
        is LiveContent.System -> Box(contentAlignment = Alignment.Center) {
            val color = systemColor(live.event, itemAccent)
            live.level?.let { ProgressRing(it / 100f, color, Modifier.size(inner), stroke = 3.dp) }
            Icon(systemIcon(live.event), null, tint = color, modifier = Modifier.size(inner * 0.55f))
        }
        null -> ItemAvatar(item, item.avatarKey, inner)
    }
}

// ---- Expanded ----------------------------------------------------------------------------------

@Composable
private fun ExpandedContent(
    state: IslandUiState,
    item: IslandItem,
    layout: IslandLayout,
    rect: IslandRect,
    accent: Color,
    registry: HitRegistry?,
    drawReplyHint: Boolean,
) {
    val pad = 18f
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = pad.dp)) {
            Band(item, layout, rect, pad, accent)
            when {
                state.quickActionsOpen -> QuickActions(item, registry)
                item.live is LiveContent.Call -> CallBody(item, item.live as LiveContent.Call, registry)
                item.live is LiveContent.Media -> MediaBody(item, item.live as LiveContent.Media, accent, registry)
                item.live is LiveContent.Progress -> ProgressBody(item, item.live as LiveContent.Progress, accent, registry)
                // Expanded glances keep moving until they collapse.
                item.live is LiveContent.System -> CompositionLocalProvider(LocalLoopMotion provides true) {
                    SystemBody(item, item.live as LiveContent.System, accent, registry)
                }
                item.live is LiveContent.Timer -> TimerBody(item, item.live as LiveContent.Timer, registry)
                else -> NotificationBody(item, state.replyOpen, registry, drawReplyHint)
            }
        }
        val stack = item.stack
        if (stack != null && stack.size > 1 && !state.replyOpen && !state.quickActionsOpen) {
            PageDots(stack.size, stack.index, accent, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        }
    }
}

/** Where in a stack the card is: the current page is a longer dot in the app's colour. */
@Composable
private fun PageDots(count: Int, index: Int, accent: Color, modifier: Modifier) {
    // Long stacks show a window of seven around the current page.
    val shown = minOf(count, MAX_DOTS)
    val first = (index - MAX_DOTS / 2).coerceIn(0, count - shown)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in first until first + shown) {
            val current = i == index
            val width by animateDpAsState(if (current) 14.dp else 5.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "dot")
            val colour by animateColorAsState(if (current) accent else Color.White.copy(alpha = 0.3f), tween(200), label = "dotColour")
            // Dots at the window's edges shrink to hint that there's more.
            val edge = (i == first && first > 0) || (i == first + shown - 1 && first + shown < count)
            Box(Modifier.width(width).height(if (edge) 3.dp else 5.dp).clip(CircleShape).background(colour))
        }
    }
}

private const val MAX_DOTS = 7

/** The camera row: small accessories left and right of the camera, nothing under it. */
@Composable
private fun Band(item: IslandItem, layout: IslandLayout, rect: IslandRect, pad: Float, accent: Color) {
    val camera = layout.cameraOffsetIn(rect)
    val leftWidth = (camera - layout.cameraGap / 2 - pad).coerceAtLeast(0f)
    val rightWidth = (rect.width - camera - layout.cameraGap / 2 - pad).coerceAtLeast(0f)
    Row(Modifier.fillMaxWidth().height(layout.bandHeight.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(leftWidth.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.stack?.missed == true) {
                Icon(Icons.Rounded.History, null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Missed · ${item.appLabel}", color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (item.kind != ItemKind.SYSTEM && !isIslandPackage(item.packageName)) {
                AppIconImage(item.packageName, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(item.appLabel, color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(rightWidth.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            when (val live = item.live) {
                is LiveContent.Media -> WaveBars(accent, live.playing, Modifier.size(20.dp, 14.dp))
                is LiveContent.Call -> if (live.state == CallState.ONGOING) {
                    val now by rememberNow()
                    Text(formatDuration(now - live.startedAtMs), color = CallGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
                }
                is LiveContent.Progress -> if (live.navigation) {
                    live.eta?.let { Text(it, color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    live.fraction?.let {
                        Text("${(it * 100).toInt()}%", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
                    }
                }
                is LiveContent.System -> Unit
                is LiveContent.Timer -> Unit
                null -> {
                    val now by rememberNow(30_000)
                    item.stack?.takeIf { it.size > 1 }?.let { stack ->
                        Text("${stack.index + 1}/${stack.size}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
                        Text("  ·  ", color = Secondary, fontSize = 12.sp)
                    }
                    Text(relativeTime(item.postTime, now), color = Secondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Banner-style notification: avatar with app badge, title, last lines, actions. */
@Composable
private fun NotificationBody(item: IslandItem, replyOpen: Boolean, registry: HitRegistry?, drawReplyHint: Boolean) {
    val lines = if (replyOpen) item.lines.takeLast(1) else item.lines.takeLast(3)
    Row(Modifier.fillMaxWidth().height(maxOf(IslandLayout.AVATAR, IslandLayout.TEXT_ROW * (1 + lines.size)).dp)) {
        AvatarWithBadge(item, IslandLayout.AVATAR.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(IslandLayout.TEXT_ROW.dp),
            )
            lines.forEach { line ->
                Row(Modifier.height(IslandLayout.TEXT_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.isHidden) {
                        Icon(Icons.Rounded.Lock, null, tint = Secondary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (line.sender != null && !item.isHidden && item.lines.any { it.sender != line.sender } ) {
                        Text("${line.sender}  ", color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Text(
                        line.text,
                        color = if (item.isHidden) Secondary else Color.White.copy(alpha = 0.92f),
                        fontSize = 14.sp,
                        fontStyle = if (item.isHidden) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    when {
        replyOpen -> {
            Spacer(Modifier.height(IslandLayout.GAP.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(IslandLayout.REPLY_ROW.dp)
                    .clip(CircleShape)
                    .background(ButtonFill)
                    .hitRegion(registry, HitRegistry.REPLY_ROW)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (drawReplyHint) Text("Reply to ${item.title}", color = Secondary, fontSize = 15.sp, maxLines = 1)
            }
        }
        item.actions.isNotEmpty() -> {
            Spacer(Modifier.height(IslandLayout.GAP.dp))
            ButtonRow {
                item.actions.forEach { action ->
                    PillButton(
                        action.title,
                        if (action.isReply) Icons.AutoMirrored.Rounded.Reply else null,
                        registry,
                        HitRegistry.action(action.index),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Avatar, caller and round red/green buttons, like an incoming call. */
@Composable
private fun CallBody(item: IslandItem, call: LiveContent.Call, registry: HitRegistry?) {
    Row(Modifier.fillMaxWidth().height(IslandLayout.CALL_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemAvatar(item, item.avatarKey, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val sub = when (call.state) {
                CallState.INCOMING -> item.lines.firstOrNull()?.text?.lowercase() ?: "incoming call"
                CallState.SCREENING -> "screening"
                CallState.ONGOING -> item.lines.firstOrNull()?.text?.takeIf { it.length < 24 }?.lowercase() ?: "on call"
            }
            Text(sub, color = Secondary, fontSize = 13.sp, maxLines = 1)
            Text(call.caller, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item.actions.forEach { action ->
            Spacer(Modifier.width(12.dp))
            CallButton(action, registry)
        }
    }
}

@Composable
private fun CallButton(action: IslandAction, registry: HitRegistry?) {
    val (color, icon) = when (action.role) {
        ActionRole.ANSWER -> CallGreen to Icons.Rounded.Call
        ActionRole.DECLINE, ActionRole.HANG_UP -> DeclineRed to Icons.Rounded.CallEnd
        else -> ButtonGrey to null
    }
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(color)
            .hitRegion(registry, HitRegistry.action(action.index)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, action.title, tint = Color.White, modifier = Modifier.size(26.dp))
        } else {
            Text(action.title.take(6), color = Color.White, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** Art, track and artist, a scrubber with elapsed and remaining, and transport controls. */
@Composable
private fun MediaBody(item: IslandItem, media: LiveContent.Media, accent: Color, registry: HitRegistry?) {
    Row(Modifier.fillMaxWidth().height(IslandLayout.MEDIA_ART.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(item.avatarKey, item.packageName, IslandLayout.MEDIA_ART.dp, RoundedCornerShape(12.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(media.track, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            media.artist?.let { Text(it, color = Secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
    Spacer(Modifier.height(IslandLayout.GAP.dp))
    val now by rememberNow(500)
    val live = remember(now, media) { media.positionNow() }
    // While dragged, and briefly after, the bar follows the finger instead of the player.
    val scrub = registry?.scrub
    val scrubbing = registry?.scrubbing == true
    if (registry != null && scrub != null && !scrubbing) {
        LaunchedEffect(media.positionAtElapsedMs, registry.scrubReleasedAt) {
            if (media.positionAtElapsedMs < registry.scrubReleasedAt) delay(SCRUB_HOLD_MS)
            registry.clearScrub()
        }
    }
    val duration = media.durationMs
    val position = if (scrub != null && duration > 0) (duration * scrub).toLong() else live
    val fraction = scrub ?: if (duration > 0) live.toFloat() / duration else 0f
    Row(Modifier.fillMaxWidth().height(IslandLayout.SCRUBBER.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeLabel(formatDuration(position), Modifier.width(40.dp), active = scrubbing)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (media.canSeek && duration > 0) Modifier.hitRegion(registry, HitRegistry.MEDIA_SEEK) else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Scrubber(fraction.coerceIn(0f, 1f), accent, media.playing, scrubbing, follow = scrub != null)
        }
        TimeLabel(if (duration > 0) "-" + formatDuration(duration - position) else "", Modifier.width(44.dp), end = true, active = scrubbing)
    }
    Row(
        Modifier.fillMaxWidth().height(IslandLayout.MEDIA_CONTROLS.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaButton(Icons.Rounded.FastRewind, "Previous", media.canPrevious, 34.dp, registry, HitRegistry.MEDIA_PREVIOUS)
        MediaButton(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", true, 40.dp, registry, HitRegistry.MEDIA_PLAY_PAUSE)
        MediaButton(Icons.Rounded.FastForward, "Next", media.canNext, 34.dp, registry, HitRegistry.MEDIA_NEXT)
    }
}

private const val SCRUB_HOLD_MS = 1500L

/**
 * Island's own music bar. Playing, the fill glides between position ticks with a soft sheen
 * travelling along it; jumps (a seek, a new track) spring instead of crawling. Under the finger
 * the track swells, the thumb grows a halo and the fill tracks the finger 1:1.
 */
@Composable
private fun Scrubber(fraction: Float, accent: Color, playing: Boolean, scrubbing: Boolean, follow: Boolean) {
    val progress = remember { Animatable(fraction) }
    LaunchedEffect(fraction, follow) {
        when {
            follow -> progress.snapTo(fraction)
            abs(fraction - progress.value) > 0.03f ->
                progress.animateTo(fraction, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
            else -> progress.animateTo(fraction, tween(520, easing = LinearEasing))
        }
    }
    val swell by animateFloatAsState(if (scrubbing) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "swell")
    val frozen = LocalFrozenTime.current != null
    val loop = rememberInfiniteTransition(label = "scrubber")
    val sheen by loop.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "sheen",
    )
    val glow by loop.animateFloat(
        initialValue = 0.22f,
        targetValue = if (playing) 0.5f else 0.22f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glow",
    )
    val dim by animateFloatAsState(if (playing || scrubbing) 1f else 0.6f, tween(300), label = "dim")
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val track = (6f + 6f * swell).dp.toPx()
        val y = size.height / 2
        val x = size.width * progress.value
        val corner = androidx.compose.ui.geometry.CornerRadius(track / 2)
        val top = androidx.compose.ui.geometry.Offset(0f, y - track / 2)
        drawRoundRect(
            Color.White.copy(alpha = 0.10f + 0.06f * swell),
            topLeft = top,
            size = androidx.compose.ui.geometry.Size(size.width, track),
            cornerRadius = corner,
        )
        if (x > 0f) {
            val fill = androidx.compose.ui.geometry.Size(x.coerceAtLeast(track), track)
            drawRoundRect(
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f * dim), lighten(accent).copy(alpha = dim)), endX = fill.width),
                topLeft = top,
                size = fill,
                cornerRadius = corner,
            )
            if (playing && !scrubbing && !frozen) {
                // A highlight sweeping left to right inside the fill.
                val c = size.width * sheen
                val w = 36.dp.toPx()
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        startX = c - w,
                        endX = c + w,
                    ),
                    topLeft = top,
                    size = fill,
                    cornerRadius = corner,
                )
            }
        }
        val center = androidx.compose.ui.geometry.Offset(x, y)
        drawCircle(accent.copy(alpha = if (frozen) 0.3f else glow * (1f - swell) + 0.3f * swell), radius = (9f + 7f * swell).dp.toPx(), center = center)
        drawCircle(Color.White, radius = (5.5f + 2.5f * swell).dp.toPx(), center = center)
    }
}

private fun lighten(color: Color) = Color(
    red = color.red + (1f - color.red) * 0.45f,
    green = color.green + (1f - color.green) * 0.45f,
    blue = color.blue + (1f - color.blue) * 0.45f,
)

@Composable
private fun TimeLabel(text: String, modifier: Modifier, end: Boolean = false, active: Boolean = false) {
    val colour by animateColorAsState(if (active) Color.White else Secondary, tween(180), label = "timeLabel")
    Text(
        text,
        color = colour,
        fontSize = 11.sp,
        style = Tabular,
        textAlign = if (end) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, enabled: Boolean, size: Dp, registry: HitRegistry?, id: String) {
    Box(
        Modifier
            .size(52.dp)
            .then(if (enabled) Modifier.hitRegion(registry, id) else Modifier)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size)) }
}

/** Delivery, ride or download: title, status, a bar in the app's colour. */
@Composable
private fun ProgressBody(item: IslandItem, progress: LiveContent.Progress, accent: Color, registry: HitRegistry?) {
    if (progress.navigation) {
        NavigationBody(item, progress, registry)
        return
    }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        AvatarWithBadge(item, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress.status.isNotEmpty()) {
                Text(progress.status, color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (progress.fraction != null) {
        val fraction by animateFloatAsState(progress.fraction!!, tween(500), label = "bar")
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(accent.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(accent))
        }
    } else {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = accent,
            trackColor = accent.copy(alpha = 0.25f),
        )
    }
    Spacer(Modifier.height((IslandLayout.PROGRESS_BODY - 40f - 12f - 6f).dp))
    if (item.actions.isNotEmpty()) {
        Spacer(Modifier.height(IslandLayout.GAP.dp))
        ButtonRow {
            item.actions.forEach { action ->
                PillButton(action.title, null, registry, HitRegistry.action(action.index), Modifier.weight(1f))
            }
        }
    }
}

/** Turn-by-turn: the turn arrow, the distance large, the road under it, and Exit in red. */
@Composable
private fun NavigationBody(item: IslandItem, nav: LiveContent.Progress, registry: HitRegistry?) {
    Row(Modifier.fillMaxWidth().height(IslandLayout.PROGRESS_BODY.dp), verticalAlignment = Alignment.CenterVertically) {
        TurnArrow(item, 52.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = Tabular)
            if (nav.status.isNotEmpty()) {
                Text(nav.status, color = Secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (item.actions.isNotEmpty()) {
        Spacer(Modifier.height(IslandLayout.GAP.dp))
        ButtonRow {
            item.actions.forEach { action ->
                val exit = EXIT_NAVIGATION.containsMatchIn(action.title)
                PillButton(
                    action.title,
                    if (exit) Icons.Rounded.Close else null,
                    registry,
                    HitRegistry.action(action.index),
                    Modifier.weight(1f),
                    fill = if (exit) DeclineRed else ButtonFill,
                )
            }
        }
    }
}

private val EXIT_NAVIGATION = Regex("(?i)exit|end|stop")

/** The app's own turn arrow (its large icon), or a generic one when it sends none. */
@Composable
private fun TurnArrow(item: IslandItem, size: Dp) {
    val store = LocalIconStore.current
    val bitmap = remember(item.avatarKey, store) { store?.get(item.avatarKey)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(size * 0.8f))
    }
}

/** Charging, ringer or headphones, larger: a ring around the icon and a label. */
@Composable
private fun SystemBody(item: IslandItem, system: LiveContent.System, accent: Color, registry: HitRegistry?) {
    if (system.event == SystemEvent.HEADPHONES_CONNECTED) {
        HeadphonesBody(system)
        return
    }
    val color = systemColor(system.event, accent)
    val reveal = rememberReveal()
    Row(Modifier.fillMaxWidth().height(IslandLayout.SYSTEM_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            // The saver draws its own battery; a ring around it would say the same twice.
            if (system.event != SystemEvent.SAVER_ON && system.event != SystemEvent.SAVER_OFF) {
                system.level?.let { ProgressRing(it / 100f * reveal, color, Modifier.size(48.dp), stroke = 4.dp) }
            }
            SystemGlyph(system.event, color, if (system.event == SystemEvent.SAVER_ON || system.event == SystemEvent.SAVER_OFF) 36.dp else 26.dp, system.level)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(systemTitle(system.event), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(system.label, color = Secondary, fontSize = 14.sp, maxLines = if (system.event == SystemEvent.CLIPBOARD) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
    }
    // The flashlight's own off switch.
    if (item.actions.isNotEmpty()) {
        Spacer(Modifier.height(IslandLayout.GAP.dp))
        ButtonRow {
            item.actions.forEach { action ->
                PillButton(action.title, Icons.Rounded.FlashlightOff, registry, HitRegistry.action(action.index), Modifier.weight(1f), fill = Color(0xFF3A3A3C))
            }
        }
    }
}

/** Like AirPods on iPhone: the pair, "Connected" over its name, and its battery in a ring. */
@Composable
private fun HeadphonesBody(system: LiveContent.System) {
    Row(Modifier.fillMaxWidth().height(IslandLayout.SYSTEM_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        val nod = rememberShake()
        Icon(headphonesIcon(system), null, tint = Color.White, modifier = Modifier.size(36.dp).graphicsLayer { rotationZ = 10f * nod })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Connected", color = Secondary, fontSize = 13.sp, maxLines = 1)
            Text(system.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        system.level?.let { level ->
            Spacer(Modifier.width(12.dp))
            BatteryRing(level, 44.dp)
        }
    }
}

/** Battery as a ring with the number inside: green, amber under 30, red under 15. */
@Composable
private fun BatteryRing(level: Int, size: Dp) {
    val color = batteryColor(level)
    val reveal = rememberReveal()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        ProgressRing(level / 100f * reveal, color, Modifier.size(size), stroke = size * 0.08f)
        Text("${(level * reveal).roundToInt()}", color = color, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.SemiBold, style = Tabular)
    }
}


private fun batteryColor(level: Int) = when {
    level < 15 -> DeclineRed
    level < 30 -> TimerOrange
    else -> CallGreen
}

private fun headphonesIcon(system: LiveContent.System): ImageVector =
    if (system.earbuds) Icons.Rounded.Earbuds else Icons.Rounded.Headphones

@Composable
private fun QuickActions(item: IslandItem, registry: HitRegistry?) {
    Row(Modifier.fillMaxWidth().height(IslandLayout.AVATAR.dp), verticalAlignment = Alignment.CenterVertically) {
        AvatarWithBadge(item, 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(IslandLayout.GAP.dp))
    ButtonRow {
        PillButton("Mute ${item.appLabel}", Icons.Rounded.NotificationsOff, registry, HitRegistry.QUICK_MUTE, Modifier.weight(1f))
        PillButton(if (item.isLive) "Hide" else "Dismiss", Icons.Rounded.Close, registry, HitRegistry.QUICK_DISMISS, Modifier.weight(1f))
    }
}

// ---- Pieces ------------------------------------------------------------------------------------

/** Clock timer or stopwatch: big orange time, label, and the app's own buttons. */
@Composable
private fun TimerBody(item: IslandItem, timer: LiveContent.Timer, registry: HitRegistry?) {
    val now by rememberNow()
    if (timer.recording) {
        // The Recorder app's own look: the waveform running into a red playhead, then the time.
        Row(Modifier.fillMaxWidth().height(IslandLayout.CALL_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordDot(timer.baseMs != null, 18.dp)
            Spacer(Modifier.width(10.dp))
            RecordingWave(timer.baseMs, Modifier.weight(1f).height(40.dp))
            Spacer(Modifier.width(14.dp))
            Text(timer.display(now), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, style = Tabular, maxLines = 1)
        }
        TimerActions(item, timer, registry)
        return
    }
    Row(Modifier.fillMaxWidth().height(IslandLayout.CALL_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(TimerOrange.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            val running = timer.baseMs != null
            // A sweep for the seconds of the current minute, and a tick on each one.
            val now30 by rememberTicker(running)
            val sweep = if (running) (now30 % 60_000) / 60_000f else 0f
            ProgressRing(if (timer.countDown) 1f - sweep else sweep, TimerOrange, Modifier.size(46.dp), stroke = 2.5.dp)
            val tick = tickScale(running)
            Icon(
                Icons.Rounded.Timer,
                null,
                tint = TimerOrange,
                modifier = Modifier.size(26.dp).graphicsLayer {
                    scaleX = tick
                    scaleY = tick
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(timer.label, color = Secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(timer.display(now), color = TimerOrange, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, style = Tabular, maxLines = 1)
    }
    TimerActions(item, timer, registry)
}

@Composable
private fun TimerActions(item: IslandItem, timer: LiveContent.Timer, registry: HitRegistry?) {
    Spacer(Modifier.height(IslandLayout.GAP.dp))
    val pause = item.actions.firstOrNull { it.role == ActionRole.PAUSE }
    val resume = item.actions.firstOrNull { it.role == ActionRole.RESUME }
    val stop = item.actions.firstOrNull { it.role == ActionRole.STOP }
    val extras = item.actions.filter { it.role == ActionRole.NORMAL }.take(1)
    // Running or not: the app's own Pause/Resume offer says best, the chronometer otherwise.
    val running = when {
        pause != null -> true
        resume != null -> false
        else -> timer.baseMs != null
    }
    val tint = if (timer.recording) RecordRed else TimerOrange
    Row(
        Modifier.fillMaxWidth().height(IslandLayout.CONTROL_ROW.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        extras.forEach { action ->
            PillButton(action.title, null, registry, HitRegistry.action(action.index), Modifier.widthIn(max = 120.dp))
        }
        // One button for both: shows pause while running, play while paused. Apps that offer
        // no such action (vivo Recorder) get the same buttons, which open the app instead.
        ControlButton(
            icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            label = if (running) "Pause" else "Resume",
            fill = tint.copy(alpha = 0.22f),
            tint = tint,
            registry = registry,
            id = (pause ?: resume)?.let { HitRegistry.action(it.index) } ?: HitRegistry.OPEN_APP + ":toggle",
        )
        ControlButton(
            icon = Icons.Rounded.Stop,
            label = "Stop",
            fill = Color.White.copy(alpha = 0.14f),
            tint = Color.White,
            registry = registry,
            id = stop?.let { HitRegistry.action(it.index) } ?: HitRegistry.OPEN_APP + ":stop",
        )
    }
}

/** A round control: the toggle morphs its icon between pause and play. */
@Composable
private fun ControlButton(icon: ImageVector, label: String, fill: Color, tint: Color, registry: HitRegistry?, id: String) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(fill)
            .hitRegion(registry, id),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.Crossfade(icon, animationSpec = tween(180), label = "control") { shown ->
            Icon(shown, label, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * The app's icon, except for Island's own demo items, which get a neutral glyph: the Island
 * logo never appears on the island.
 */
@Composable
private fun ItemIcon(item: IslandItem, size: Dp) {
    if (!isIslandPackage(item.packageName)) {
        AppIconImage(item.packageName, size)
        return
    }
    val glyph = when (item.kind) {
        ItemKind.CALL -> Icons.Rounded.Call
        ItemKind.MEDIA -> Icons.Rounded.MusicNote
        ItemKind.PROGRESS -> Icons.Rounded.Download
        ItemKind.TIMER -> Icons.Rounded.Timer
        else -> Icons.Rounded.ChatBubble
    }
    Box(Modifier.size(size).clip(CircleShape).background(ButtonGrey), contentAlignment = Alignment.Center) {
        Icon(glyph, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** A person's photo when there is one; a green phone for calls; otherwise [ItemIcon]. */
@Composable
private fun ItemAvatar(item: IslandItem, key: String?, size: Dp) {
    val store = LocalIconStore.current
    val bitmap = remember(key, store) { store?.get(key)?.asImageBitmap() }
    when {
        bitmap != null && !item.isHidden -> Image(bitmap, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        item.kind == ItemKind.CALL -> Box(Modifier.size(size).clip(CircleShape).background(CallGreen), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
        }
        else -> ItemIcon(item, size)
    }
}

/** Sender avatar with the app's icon as a small badge, like a banner notification. */
@Composable
private fun AvatarWithBadge(item: IslandItem, size: Dp) {
    Box(Modifier.size(size)) {
        if (item.isHidden || item.avatarKey == null) {
            ItemIcon(item, size)
        } else {
            Avatar(item.avatarKey, item.packageName, size)
            if (!isIslandPackage(item.packageName)) AppIconImage(
                item.packageName,
                size * 0.42f,
                Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).border(1.5.dp, Color.Black, CircleShape),
            )
        }
    }
}

/** Album art from the icon store, a note on the accent colour when there is none. */
@Composable
private fun Art(key: String?, packageName: String, size: Dp, shape: androidx.compose.ui.graphics.Shape) {
    val store = LocalIconStore.current
    val bitmap = remember(key, store) { store?.get(key)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(shape).background(ButtonGrey), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IslandLayout.BUTTON_ROW.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PillButton(label: String, icon: ImageVector?, registry: HitRegistry?, id: String, modifier: Modifier, fill: Color = ButtonFill) {
    Row(
        modifier
            .height(IslandLayout.BUTTON_ROW.dp)
            .clip(CircleShape)
            .background(fill)
            .hitRegion(registry, id)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}



