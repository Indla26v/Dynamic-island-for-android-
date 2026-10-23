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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.GestureAction
import dev.indla.island.model.GestureMap
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.StylePreset
import dev.indla.island.model.WhenToShow
import dev.indla.island.ui.settings.components.GlassCard
import dev.indla.island.ui.settings.components.GlassPill
import dev.indla.island.ui.settings.components.GlassToggleRow
import dev.indla.island.ui.settings.components.IslandPreview
import dev.indla.island.ui.settings.components.RowDivider
import dev.indla.island.ui.settings.components.TabScaffold
import dev.indla.island.ui.settings.components.glassSurface
import dev.indla.island.ui.settings.theme.LocalGlass
import kotlin.math.roundToInt

@Composable
fun LookScreen(
    settings: IslandSettings,
    reduceTransparency: Boolean,
    onAppearance: ((IslandAppearance) -> IslandAppearance) -> Unit,
    onWhenToShow: ((WhenToShow) -> WhenToShow) -> Unit,
    onGestures: ((GestureMap) -> GestureMap) -> Unit,
    onAutoHide: (Int) -> Unit,
    onPositionEditing: (Boolean) -> Unit,
    onNudge: (Float, Float) -> Unit,
    onResetPosition: () -> Unit,
) {
    val a = settings.appearance
    val glass = LocalGlass.current
    var editing by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(editing) {
        onPositionEditing(editing)
        onDispose { onPositionEditing(false) }
    }

    TabScaffold(title = "Look & feel", pinned = { IslandPreview(a, reduceTransparency) }) {
        item {
            GlassCard(title = "Style") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StylePreset.entries.forEach { preset ->
                        PresetTile(preset, selected = a.preset == preset, modifier = Modifier.weight(1f)) {
                            onAppearance { it.copy(preset = preset) }
                        }
                    }
                }
            }
        }

        item {
            GlassCard(title = "Colour") {
                Swatches(
                    selected = a.accentArgb,
                    includeAuto = false,
                    onPick = { argb -> if (argb != null) onAppearance { it.copy(accentArgb = argb) } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        item {
            GlassCard(title = "Size & position") {
                SliderRow("Size", "${(a.sizeScale * 100).roundToInt()}%", a.sizeScale, 0.8f..1.3f) { v ->
                    onAppearance { it.copy(sizeScale = v) }
                }
                GlassToggleRow("Show resting pill", a.showRestPill, { v -> onAppearance { it.copy(showRestPill = v) } },
                    subtitle = "A small black pill around the camera when nothing is active")
                RowDivider()
                GlassToggleRow("Adjust on screen", editing, { editing = it }, icon = Icons.Rounded.OpenWith,
                    subtitle = "Drag the real island at the top of your screen")
                if (editing) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NudgeButton(Icons.Rounded.KeyboardArrowLeft, "Left") { onNudge(-1f, 0f) }
                        NudgeButton(Icons.Rounded.KeyboardArrowUp, "Up") { onNudge(0f, -1f) }
                        NudgeButton(Icons.Rounded.KeyboardArrowDown, "Down") { onNudge(0f, 1f) }
                        NudgeButton(Icons.Rounded.KeyboardArrowRight, "Right") { onNudge(1f, 0f) }
                        NudgeButton(Icons.Rounded.Refresh, "Reset", onResetPosition)
                    }
                    Text(
                        "Offset ${a.offsetXDp.roundToInt()} dp, ${a.offsetYDp.roundToInt()} dp · arrows move 1 dp",
                        color = glass.contentSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                    )
                }
            }
        }

        item {
            GlassCard(title = "Glass") {
                SliderRow("Glass intensity", if (a.glassIntensity < 0.5f) "Clear" else "Tinted", a.glassIntensity, 0f..1f) { v ->
                    onAppearance { it.copy(glassIntensity = v) }
                }
                if (reduceTransparency) {
                    Text(
                        "Reduce transparency is on in system settings, so Island uses solid surfaces.",
                        color = glass.contentSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
        }

        item {
            val w = settings.whenToShow
            GlassCard(title = "When to show") {
                GlassToggleRow("Not for the app I'm using", w.hideInForegroundApp, { v -> onWhenToShow { it.copy(hideInForegroundApp = v) } },
                    subtitle = "No island for a chat that's already open")
                GlassToggleRow("Hide during full-screen video and games", w.hideInFullscreen, { v -> onWhenToShow { it.copy(hideInFullscreen = v) } })
                GlassToggleRow("Hide in landscape", w.hideInLandscape, { v -> onWhenToShow { it.copy(hideInLandscape = v) } })
                GlassToggleRow("Show on the lockscreen", w.showOnLockscreen, { v -> onWhenToShow { it.copy(showOnLockscreen = v) } })
                GlassToggleRow("Hide while the notification shade is open", w.hideWhenShadeOpen, { v -> onWhenToShow { it.copy(hideWhenShadeOpen = v) } })
                RowDivider()
                SliderRow("Stay on screen", "${settings.autoHideSeconds} s", settings.autoHideSeconds.toFloat(), 3f..12f, steps = 8) { v ->
                    onAutoHide(v.roundToInt())
                }
            }
        }

        item {
            val g = settings.gestures
            GlassCard(title = "Gestures") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Spacer(Modifier.weight(1f))
                    Text("Compact", color = glass.contentSecondary, fontSize = 12.sp, modifier = Modifier.width(118.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Expanded", color = glass.contentSecondary, fontSize = 12.sp, modifier = Modifier.width(118.dp))
                }
                GestureRow("Tap", g.compactTap, g.expandedTap,
                    { v -> onGestures { it.copy(compactTap = v) } }, { v -> onGestures { it.copy(expandedTap = v) } })
                GestureRow("Swipe down", g.compactSwipeDown, g.expandedSwipeDown,
                    { v -> onGestures { it.copy(compactSwipeDown = v) } }, { v -> onGestures { it.copy(expandedSwipeDown = v) } })
                GestureRow("Swipe up", g.compactSwipeUp, g.expandedSwipeUp,
                    { v -> onGestures { it.copy(compactSwipeUp = v) } }, { v -> onGestures { it.copy(expandedSwipeUp = v) } })
                GestureRow("Swipe sideways", g.compactSwipeSide, g.expandedSwipeSide,
                    { v -> onGestures { it.copy(compactSwipeSide = v) } }, { v -> onGestures { it.copy(expandedSwipeSide = v) } })
                GestureRow("Long press", g.compactLongPress, g.expandedLongPress,
                    { v -> onGestures { it.copy(compactLongPress = v) } }, { v -> onGestures { it.copy(expandedLongPress = v) } })
                GlassPill("Restore defaults", { onGestures { GestureMap() } }, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun PresetTile(preset: StylePreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .glassSurface(RoundedCornerShape(20.dp), strong = selected)
            .then(if (selected) Modifier.border(2.dp, primary, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(42.dp)) {
            val w = size.width
            when (preset) {
                StylePreset.CLASSIC_PILL -> drawRoundRect(Color.Black, Offset(w * 0.18f, 10.dp.toPx()), Size(w * 0.64f, 22.dp.toPx()), CornerRadius(11.dp.toPx()))
                StylePreset.MINIMAL -> {
                    drawRoundRect(Color.Black, Offset(w * 0.3f, 12.dp.toPx()), Size(w * 0.4f, 18.dp.toPx()), CornerRadius(9.dp.toPx()))
                    drawCircle(primary, 3.dp.toPx(), Offset(w * 0.62f, 21.dp.toPx()))
                }
                StylePreset.NOTCH_ATTACHED -> drawRoundRect(Color.Black, Offset(w * 0.18f, 0f), Size(w * 0.64f, 28.dp.toPx()), CornerRadius(12.dp.toPx()))
            }
        }
        Text(
            when (preset) {
                StylePreset.CLASSIC_PILL -> "Classic pill"
                StylePreset.MINIMAL -> "Minimal"
                StylePreset.NOTCH_ATTACHED -> "Notch-attached"
            },
            color = glass.content,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            minLines = 2,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val glass = LocalGlass.current
    // Local while dragging so DataStore isn't written every frame.
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(current) }
    val shown = if (dragging) local else current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(title, color = glass.content, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value, color = glass.contentSecondary, fontSize = 14.sp)
        }
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                onChange(local)
            },
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun NudgeButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .glassSurface(CircleShape, strong = true)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = LocalGlass.current.content) }
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = glass.content, fontSize = 15.sp, modifier = Modifier.weight(1f))
        ActionPicker(compact, onCompact)
        Spacer(Modifier.width(8.dp))
        ActionPicker(expanded, onExpanded)
    }
}

@Composable
private fun ActionPicker(value: GestureAction, onPick: (GestureAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val glass = LocalGlass.current
    Box {
        Box(
            Modifier
                .width(118.dp)
                .height(36.dp)
                .clip(CircleShape)
                .glassSurface(CircleShape, strong = true)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text(actionLabel(value), color = glass.content, fontSize = 13.sp, maxLines = 1) }
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
    GestureAction.QUICK_ACTIONS -> "Quick actions"
    GestureAction.NONE -> "Nothing"
}
