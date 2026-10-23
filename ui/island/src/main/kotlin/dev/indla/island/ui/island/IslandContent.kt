package dev.indla.island.ui.island

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.IslandItem
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandUiState
import dev.indla.island.model.ItemKind
import dev.indla.island.model.MessageLine

private val Secondary = Color(0xB3FFFFFF)
private val ButtonFill = Color(0x26FFFFFF)

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

/** Icon left of the camera, glance right of it. */
@Composable
private fun CompactContent(item: IslandItem, layout: IslandLayout, rect: IslandRect, accent: Color) {
    val h = rect.height
    val avatar = (h - 10f).coerceAtMost(26f)
    val sideWidth = ((rect.width - layout.cameraGap) / 2 - h / 2).coerceAtLeast(0f)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = ((h - avatar) / 2).dp)) {
            if (item.kind == ItemKind.HIDDEN_BY_ANDROID || item.kind == ItemKind.HIDDEN_ON_LOCKSCREEN) {
                AppIconImage(item.packageName, avatar.dp)
            } else {
                Avatar(item.avatarKey, item.packageName, avatar.dp)
            }
        }
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = (h / 2 - 2f).coerceAtLeast(6f).dp).width(sideWidth.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (layout.minimal) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            } else {
                Text(
                    item.glance,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

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
    val hPad = 16f
    val camera = layout.cameraOffsetIn(rect)
    val leftWidth = (camera - layout.cameraGap / 2 - hPad).coerceAtLeast(0f)
    val rightWidth = (rect.width - camera - layout.cameraGap / 2 - hPad).coerceAtLeast(0f)

    Column(Modifier.fillMaxSize().padding(horizontal = hPad.dp)) {
        Spacer(Modifier.height(IslandLayout.PADDING_TOP.dp))
        // Header row shares its line with the camera, so text stays left and right of it.
        Row(Modifier.fillMaxWidth().height(layout.headerHeight.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(leftWidth.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(item.packageName, 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(rightWidth.dp), contentAlignment = Alignment.CenterEnd) {
                Text(item.appLabel, color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        when {
            state.quickActionsOpen -> {
                Spacer(Modifier.height(IslandLayout.GAP.dp))
                ButtonRow {
                    PillButton("Mute ${item.appLabel}", Icons.Rounded.NotificationsOff, registry, HitRegistry.QUICK_MUTE, Modifier.weight(1f))
                    PillButton("Dismiss", Icons.Rounded.Close, registry, HitRegistry.QUICK_DISMISS, Modifier.weight(1f))
                }
            }
            state.replyOpen -> {
                item.lines.lastOrNull()?.let { Line(it, item) }
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
            else -> {
                item.lines.takeLast(3).forEach { Line(it, item) }
                if (item.actions.isNotEmpty()) {
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
    }
}

@Composable
private fun Line(line: MessageLine, item: IslandItem) {
    val hidden = item.kind == ItemKind.HIDDEN_BY_ANDROID || item.kind == ItemKind.HIDDEN_ON_LOCKSCREEN
    Row(Modifier.fillMaxWidth().height(IslandLayout.LINE.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            hidden -> Icon(Icons.Rounded.Lock, null, tint = Secondary, modifier = Modifier.size(20.dp))
            line.sender != null -> Avatar(line.avatarKey, item.packageName, 28.dp)
        }
        if (hidden || line.sender != null) Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (line.sender != null && !hidden) {
                Text(line.sender!!, color = Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                line.text,
                color = if (hidden) Secondary else Color.White,
                fontSize = 14.sp,
                fontStyle = if (hidden) FontStyle.Italic else FontStyle.Normal,
                maxLines = if (line.sender == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun ButtonRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
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
            .height(36.dp)
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
