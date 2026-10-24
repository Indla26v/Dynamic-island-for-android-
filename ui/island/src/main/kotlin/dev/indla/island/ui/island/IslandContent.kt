package dev.indla.island.ui.island

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

private val Secondary = Color(0xA6FFFFFF)
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
        is LiveContent.System -> Icon(systemIcon(live.event), null, tint = systemColor(live.event, accent), modifier = Modifier.size(size * 0.85f))
        is LiveContent.Progress -> ItemIcon(item, size)
        is LiveContent.Timer -> Icon(Icons.Rounded.Timer, null, tint = TimerOrange, modifier = Modifier.size(size * 0.85f))
        null -> ItemAvatar(item, item.avatarKey, size)
    }
}

@Composable
private fun CompactTrailing(item: IslandItem, accent: Color, minimal: Boolean) {
    when (val live = item.live) {
        is LiveContent.Media -> WaveBars(accent, live.playing, Modifier.size(22.dp, 16.dp))
        is LiveContent.Call -> when (live.state) {
            CallState.ONGOING -> WaveBars(CallGreen, true, Modifier.size(22.dp, 16.dp))
            else -> GlanceText(item.glance, CallGreen)
        }
        is LiveContent.Progress -> {
            if (item.glance.isNotEmpty() && !minimal) {
                GlanceText(item.glance, accent)
                Spacer(Modifier.width(6.dp))
            }
            ProgressRing(live.fraction, accent, Modifier.size(18.dp))
        }
        is LiveContent.Timer -> {
            val now by rememberNow()
            GlanceText(live.display(now), TimerOrange)
        }
        is LiveContent.System -> {
            val color = systemColor(live.event, accent)
            if (live.level != null) {
                GlanceText(live.label, color)
                Spacer(Modifier.width(6.dp))
                ProgressRing(live.level!! / 100f, color, Modifier.size(18.dp))
            } else {
                GlanceText(live.label, color)
            }
        }
        null -> if (minimal) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        } else {
            GlanceText(item.glance, accent)
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
        is LiveContent.Progress -> Box(contentAlignment = Alignment.Center) {
            ProgressRing(live.fraction, itemAccent, Modifier.size(inner), stroke = 3.dp)
            ItemIcon(item, inner * 0.55f)
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
    Column(Modifier.fillMaxSize().padding(horizontal = pad.dp)) {
        Band(item, layout, rect, pad, accent)
        when {
            state.quickActionsOpen -> QuickActions(item, registry)
            item.live is LiveContent.Call -> CallBody(item, item.live as LiveContent.Call, registry)
            item.live is LiveContent.Media -> MediaBody(item, item.live as LiveContent.Media, registry)
            item.live is LiveContent.Progress -> ProgressBody(item, item.live as LiveContent.Progress, accent, registry)
            item.live is LiveContent.System -> SystemBody(item.live as LiveContent.System, accent)
            item.live is LiveContent.Timer -> TimerBody(item, item.live as LiveContent.Timer, registry)
            else -> NotificationBody(item, state.replyOpen, registry, drawReplyHint)
        }
    }
}

/** The camera row: small accessories left and right of the camera, nothing under it. */
@Composable
private fun Band(item: IslandItem, layout: IslandLayout, rect: IslandRect, pad: Float, accent: Color) {
    val camera = layout.cameraOffsetIn(rect)
    val leftWidth = (camera - layout.cameraGap / 2 - pad).coerceAtLeast(0f)
    val rightWidth = (rect.width - camera - layout.cameraGap / 2 - pad).coerceAtLeast(0f)
    Row(Modifier.fillMaxWidth().height(layout.bandHeight.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(leftWidth.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.kind != ItemKind.SYSTEM && !isIslandPackage(item.packageName)) {
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
                is LiveContent.Progress -> live.fraction?.let {
                    Text("${(it * 100).toInt()}%", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
                }
                is LiveContent.System -> Unit
                is LiveContent.Timer -> Unit
                null -> {
                    val now by rememberNow(30_000)
                    Text(relativeTime(item.postTime, now), color = Secondary, fontSize = 12.sp)
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
private fun MediaBody(item: IslandItem, media: LiveContent.Media, registry: HitRegistry?) {
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
    val position = remember(now, media) { media.positionNow() }
    val fraction = if (media.durationMs > 0) position.toFloat() / media.durationMs else 0f
    Row(Modifier.fillMaxWidth().height(IslandLayout.SCRUBBER.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeLabel(formatDuration(position), Modifier.width(40.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (media.canSeek && media.durationMs > 0) Modifier.hitRegion(registry, HitRegistry.MEDIA_SEEK) else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp).clip(CircleShape).background(Color.White))
        }
        TimeLabel(if (media.durationMs > 0) "-" + formatDuration(media.durationMs - position) else "", Modifier.width(44.dp), end = true)
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

@Composable
private fun TimeLabel(text: String, modifier: Modifier, end: Boolean = false) {
    Text(
        text,
        color = Secondary,
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

/** Charging, ringer or headphones, larger: a ring around the icon and a label. */
@Composable
private fun SystemBody(system: LiveContent.System, accent: Color) {
    val color = systemColor(system.event, accent)
    Row(Modifier.fillMaxWidth().height(IslandLayout.SYSTEM_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            system.level?.let { ProgressRing(it / 100f, color, Modifier.size(48.dp), stroke = 4.dp) }
            Icon(systemIcon(system.event), null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(systemTitle(system.event), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(system.label, color = Secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

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
    Row(Modifier.fillMaxWidth().height(IslandLayout.CALL_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(TimerOrange.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Timer, null, tint = TimerOrange, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(timer.label, color = Secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(timer.display(now), color = TimerOrange, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, style = Tabular, maxLines = 1)
    }
    if (item.actions.isNotEmpty()) {
        Spacer(Modifier.height(IslandLayout.GAP.dp))
        ButtonRow {
            item.actions.forEach { action ->
                PillButton(action.title, null, registry, HitRegistry.action(action.index), Modifier.weight(1f))
            }
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
private fun PillButton(label: String, icon: ImageVector?, registry: HitRegistry?, id: String, modifier: Modifier) {
    Row(
        modifier
            .height(IslandLayout.BUTTON_ROW.dp)
            .clip(CircleShape)
            .background(ButtonFill)
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

private fun systemIcon(event: SystemEvent): ImageVector = when (event) {
    SystemEvent.CHARGING -> Icons.Rounded.Bolt
    SystemEvent.UNPLUGGED -> Icons.Rounded.BatteryStd
    SystemEvent.BATTERY_LOW -> Icons.Rounded.BatteryAlert
    SystemEvent.RINGER_NORMAL -> Icons.Rounded.NotificationsActive
    SystemEvent.RINGER_VIBRATE -> Icons.Rounded.Vibration
    SystemEvent.RINGER_SILENT -> Icons.Rounded.NotificationsOff
    SystemEvent.HEADPHONES_CONNECTED, SystemEvent.HEADPHONES_DISCONNECTED -> Icons.Rounded.Headphones
}

private fun systemColor(event: SystemEvent, accent: Color): Color = when (event) {
    SystemEvent.CHARGING -> CallGreen
    SystemEvent.BATTERY_LOW, SystemEvent.RINGER_SILENT -> DeclineRed
    SystemEvent.UNPLUGGED, SystemEvent.HEADPHONES_DISCONNECTED -> Secondary
    else -> accent
}

private fun systemTitle(event: SystemEvent): String = when (event) {
    SystemEvent.CHARGING -> "Charging"
    SystemEvent.UNPLUGGED -> "Unplugged"
    SystemEvent.BATTERY_LOW -> "Battery low"
    SystemEvent.RINGER_NORMAL -> "Ring"
    SystemEvent.RINGER_VIBRATE -> "Vibrate"
    SystemEvent.RINGER_SILENT -> "Silent mode"
    SystemEvent.HEADPHONES_CONNECTED -> "Connected"
    SystemEvent.HEADPHONES_DISCONNECTED -> "Disconnected"
}
