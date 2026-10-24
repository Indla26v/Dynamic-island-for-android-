package dev.indla.island.ui.settings.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.GestureAction
import dev.indla.island.model.GestureMap
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.StylePreset
import dev.indla.island.model.WhenToShow
import dev.indla.island.ui.settings.components.GlassCard
import dev.indla.island.ui.settings.components.GlassSliderRow
import dev.indla.island.ui.settings.components.GlassToggleRow
import dev.indla.island.ui.settings.components.IslandPreview
import dev.indla.island.ui.settings.components.RowDivider
import dev.indla.island.ui.settings.components.Space
import dev.indla.island.ui.settings.components.TabScaffold
import dev.indla.island.ui.settings.components.glassSurface
import dev.indla.island.ui.settings.theme.LocalGlass
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun LookScreen(
    settings: IslandSettings,
    reduceTransparency: Boolean,
    onAppearance: ((IslandAppearance) -> IslandAppearance) -> Unit,
    onWhenToShow: ((WhenToShow) -> WhenToShow) -> Unit,
    onGestures: ((GestureMap) -> GestureMap) -> Unit,
    onAutoHide: (Int) -> Unit,
    onPositionEditing: (Boolean) -> Unit,
    onPreviewSize: (Float?) -> Unit,
    onNudge: (Float, Float) -> Unit,
    onResetPosition: () -> Unit,
) {
    val a = settings.appearance
    val glass = LocalGlass.current
    var editing by rememberSaveable { mutableStateOf(false) }
    // Size slider: the preview and the real island follow the finger, then the island stays
    // visible a moment after release so the new size can be seen on the camera.
    var liveSize by remember { mutableStateOf<Float?>(null) }
    var sizeTouches by remember { mutableIntStateOf(0) }
    var showingSize by remember { mutableStateOf(false) }
    LaunchedEffect(a.sizeScale) {
        liveSize = null
        onPreviewSize(null)
    }
    LaunchedEffect(sizeTouches) {
        if (sizeTouches == 0) return@LaunchedEffect
        showingSize = true
        delay(SIZE_SHOW_MS)
        showingSize = false
    }
    DisposableEffect(editing || showingSize) {
        onPositionEditing(editing || showingSize)
        onDispose { onPositionEditing(false) }
    }
    val previewAppearance = liveSize?.let { a.copy(sizeScale = it) } ?: a

    TabScaffold(title = "Look & feel", pinned = { IslandPreview(previewAppearance, reduceTransparency, focusCompact = sizeTouches) }) {
        item {
            GlassCard(title = "Appearance") {
                Row(
                    Modifier.padding(horizontal = Space.rowPadding, vertical = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    StylePreset.entries.forEach { preset ->
                        PresetTile(preset, selected = a.preset == preset, modifier = Modifier.weight(1f)) {
                            onAppearance { it.copy(preset = preset) }
                        }
                    }
                }
                Swatches(
                    selected = a.accentArgb,
                    includeAuto = false,
                    onPick = { argb -> if (argb != null) onAppearance { it.copy(accentArgb = argb) } },
                    modifier = Modifier.padding(horizontal = Space.rowPadding, vertical = Space.m),
                )
                RowDivider(start = Space.rowPadding)
                GlassSliderRow(
                    "Size",
                    a.sizeScale,
                    0.8f..1.3f,
                    { "${(it * 100).roundToInt()}%" },
                    { v -> onAppearance { it.copy(sizeScale = v) } },
                    onLive = { v ->
                        if (v != null) {
                            if (liveSize == null) sizeTouches++
                            liveSize = v
                            onPreviewSize(v)
                        } else {
                            sizeTouches++
                        }
                    },
                )
                GlassToggleRow(
                    "Glass card",
                    a.glassCard,
                    { v -> onAppearance { it.copy(glassCard = v) } },
                    subtitle = "Frosted, blurred expanded card instead of solid black",
                )
                GlassSliderRow("Glass", a.glassIntensity, 0f..1f, { if (it < 0.5f) "Clear" else "Tinted" }, { v ->
                    onAppearance { it.copy(glassIntensity = v) }
                })
                if (reduceTransparency) {
                    Text(
                        "Reduce transparency is on in system settings, so surfaces are solid.",
                        color = glass.contentSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = Space.rowPadding, vertical = Space.xs),
                    )
                }
                GlassToggleRow(
                    "Colour from app icon",
                    a.autoColour,
                    { v -> onAppearance { it.copy(autoColour = v) } },
                    subtitle = "Tint each app's glance with its icon colour",
                )
                GlassToggleRow(
                    "Resting pill",
                    a.showRestPill,
                    { v -> onAppearance { it.copy(showRestPill = v) } },
                    subtitle = "Keep a small pill around the camera",
                )
            }
        }

        item {
            GlassCard(
                title = "Position",
                action = if (a.offsetXDp != 0f || a.offsetYDp != 0f) {
                    { TextButton(onClick = onResetPosition) { Text("Reset") } }
                } else {
                    null
                },
            ) {
                GlassToggleRow(
                    "Adjust on screen",
                    editing,
                    { editing = it },
                    subtitle = "Drag the real island, or nudge 1 dp",
                    icon = Icons.Rounded.OpenWith,
                )
                if (editing) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Space.textInset, end = Space.rowPadding, bottom = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${a.offsetXDp.roundToInt()}, ${a.offsetYDp.roundToInt()} dp",
                            color = glass.contentSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        NudgeButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left") { onNudge(-1f, 0f) }
                        NudgeButton(Icons.Rounded.KeyboardArrowUp, "Up") { onNudge(0f, -1f) }
                        NudgeButton(Icons.Rounded.KeyboardArrowDown, "Down") { onNudge(0f, 1f) }
                        NudgeButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right") { onNudge(1f, 0f) }
                    }
                }
            }
        }

        item {
            val w = settings.whenToShow
            GlassCard(title = "When to show") {
                GlassToggleRow("Not for the app I'm in", w.hideInForegroundApp, { v -> onWhenToShow { it.copy(hideInForegroundApp = v) } })
                GlassToggleRow("Hide in full screen", w.hideInFullscreen, { v -> onWhenToShow { it.copy(hideInFullscreen = v) } })
                GlassToggleRow("Hide in landscape", w.hideInLandscape, { v -> onWhenToShow { it.copy(hideInLandscape = v) } })
                GlassToggleRow("Hide with the shade open", w.hideWhenShadeOpen, { v -> onWhenToShow { it.copy(hideWhenShadeOpen = v) } })
                GlassToggleRow("Show on the lockscreen", w.showOnLockscreen, { v -> onWhenToShow { it.copy(showOnLockscreen = v) } })
                GlassToggleRow(
                    "Keep missed notifications",
                    w.keepMissed,
                    { v -> onWhenToShow { it.copy(keepMissed = v) } },
                    subtitle = "Unopened ones stay as a small pile; tap it to catch up",
                )
                GlassToggleRow(
                    "Stack each app",
                    w.stackByApp,
                    { v -> onWhenToShow { it.copy(stackByApp = v) } },
                    subtitle = "One app's notifications share a slot; swipe to page through",
                )
                RowDivider(start = Space.rowPadding)
                GlassSliderRow(
                    "Stay for",
                    settings.autoHideSeconds.toFloat(),
                    3f..12f,
                    { "${it.roundToInt()} s" },
                    { onAutoHide(it.roundToInt()) },
                    steps = 8,
                )
            }
        }

        item {
            val g = settings.gestures
            GlassCard(
                title = "Gestures",
                action = if (g != GestureMap()) {
                    { TextButton(onClick = { onGestures { GestureMap() } }) { Text("Defaults") } }
                } else {
                    null
                },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.rowPadding, vertical = Space.xs)) {
                    Spacer(Modifier.weight(1f))
                    ColumnHeader("Compact")
                    Spacer(Modifier.width(Space.s))
                    ColumnHeader("Expanded")
                }
                GestureRow("Tap", g.compactTap, g.expandedTap,
                    { v -> onGestures { it.copy(compactTap = v) } }, { v -> onGestures { it.copy(expandedTap = v) } })
                GestureRow("Swipe down", g.compactSwipeDown, g.expandedSwipeDown,
                    { v -> onGestures { it.copy(compactSwipeDown = v) } }, { v -> onGestures { it.copy(expandedSwipeDown = v) } })
                GestureRow("Swipe up", g.compactSwipeUp, g.expandedSwipeUp,
                    { v -> onGestures { it.copy(compactSwipeUp = v) } }, { v -> onGestures { it.copy(expandedSwipeUp = v) } })
                GestureRow("Sideways", g.compactSwipeSide, g.expandedSwipeSide,
                    { v -> onGestures { it.copy(compactSwipeSide = v) } }, { v -> onGestures { it.copy(expandedSwipeSide = v) } })
                GestureRow("Long press", g.compactLongPress, g.expandedLongPress,
                    { v -> onGestures { it.copy(compactLongPress = v) } }, { v -> onGestures { it.copy(expandedLongPress = v) } })
            }
        }
    }
}

private val PickerWidth = 112.dp

@Composable
private fun ColumnHeader(text: String) {
    Text(
        text,
        color = LocalGlass.current.contentSecondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(PickerWidth),
    )
}

@Composable
private fun PresetTile(preset: StylePreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .glassSurface(shape, strong = selected)
            .then(if (selected) Modifier.border(2.dp, primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = Space.s, horizontal = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val w = size.width
            when (preset) {
                StylePreset.CLASSIC_PILL -> drawRoundRect(Color.Black, Offset(w * 0.2f, 6.dp.toPx()), Size(w * 0.6f, 18.dp.toPx()), CornerRadius(9.dp.toPx()))
                StylePreset.MINIMAL -> {
                    drawRoundRect(Color.Black, Offset(w * 0.3f, 8.dp.toPx()), Size(w * 0.4f, 14.dp.toPx()), CornerRadius(7.dp.toPx()))
                    drawCircle(primary, 2.5.dp.toPx(), Offset(w * 0.62f, 15.dp.toPx()))
                }
                StylePreset.NOTCH_ATTACHED -> drawRoundRect(Color.Black, Offset(w * 0.2f, 0f), Size(w * 0.6f, 22.dp.toPx()), CornerRadius(10.dp.toPx()))
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            when (preset) {
                StylePreset.CLASSIC_PILL -> "Classic"
                StylePreset.MINIMAL -> "Minimal"
                StylePreset.NOTCH_ATTACHED -> "Attached"
            },
            color = glass.content,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun NudgeButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = Space.xs)
            .size(40.dp)
            .clip(CircleShape)
            .glassSurface(CircleShape, strong = true)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = LocalGlass.current.content, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun GestureRow(
    label: String,
    compact: GestureAction,
    expanded: GestureAction,
    onCompact: (GestureAction) -> Unit,
    onExpanded: (GestureAction) -> Unit,
) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.rowPadding, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = glass.content, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
        ActionPicker(compact, onCompact)
        Spacer(Modifier.width(Space.s))
        ActionPicker(expanded, onExpanded)
    }
}

@Composable
private fun ActionPicker(value: GestureAction, onPick: (GestureAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val glass = LocalGlass.current
    Box {
        Row(
            Modifier
                .width(PickerWidth)
                .height(36.dp)
                .clip(CircleShape)
                .glassSurface(CircleShape, strong = true)
                .clickable { open = true }
                .padding(start = Space.m, end = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                actionLabel(value),
                color = glass.content,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.UnfoldMore, null, tint = glass.contentSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            GestureAction.entries.forEach { action ->
                DropdownMenuItem(text = { Text(actionLabel(action)) }, onClick = {
                    onPick(action)
                    open = false
                })
            }
        }
    }
}

private fun actionLabel(a: GestureAction) = when (a) {
    GestureAction.EXPAND -> "Expand"
    GestureAction.OPEN_APP -> "Open app"
    GestureAction.HIDE -> "Hide"
    GestureAction.COLLAPSE -> "Collapse"
    GestureAction.SWITCH_ITEM -> "Switch item"
    GestureAction.QUICK_ACTIONS -> "Quick menu"
    GestureAction.NONE -> "Nothing"
}

private const val SIZE_SHOW_MS = 2500L
