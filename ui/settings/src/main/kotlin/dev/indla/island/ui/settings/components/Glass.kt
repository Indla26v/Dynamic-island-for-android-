package dev.indla.island.ui.settings.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.ui.island.GlassEdge
import dev.indla.island.ui.settings.theme.LocalGlass

/** One spacing scale for the whole app: a 4 dp grid. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp

    /** Side margin of every screen. */
    val gutter = 16.dp

    /** Gap between cards. */
    val cardGap = 12.dp

    /** Inner horizontal padding of rows, so every label in a card lines up. */
    val rowPadding = 16.dp
    val rowMinHeight = 52.dp
    val iconSize = 22.dp

    /** Where text starts in a row that has an icon; dividers align to it. */
    val textInset = rowPadding + iconSize + m
}

val CardShape = RoundedCornerShape(28.dp)

/**
 * The app background glass shows through: a soft gradient tinted from the wallpaper via
 * dynamic colour, with two large colour pools so translucent surfaces have something to show.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalGlass.current.dark
    Box(
        modifier
            .fillMaxSize()
            .background(scheme.surface)
            .drawWithCache {
                val base = Brush.verticalGradient(
                    listOf(
                        scheme.primaryContainer.copy(alpha = if (dark) 0.55f else 0.7f),
                        scheme.surface,
                        scheme.tertiaryContainer.copy(alpha = if (dark) 0.35f else 0.5f),
                    ),
                )
                val poolA = Brush.radialGradient(
                    listOf(scheme.primary.copy(alpha = if (dark) 0.35f else 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.18f),
                    radius = size.width * 0.8f,
                )
                val poolB = Brush.radialGradient(
                    listOf(scheme.tertiary.copy(alpha = if (dark) 0.3f else 0.2f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.7f),
                    radius = size.width * 0.9f,
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(poolA)
                    drawRect(poolB)
                }
            },
        content = content,
    )
}

/** Translucent tint + lit edge. No blur here: blur is reserved for at most two containers. */
fun Modifier.glassSurface(shape: Shape = CardShape, strong: Boolean = false): Modifier = composed {
    val glass = LocalGlass.current
    val edge = remember { GlassEdge() }
    this
        .clip(shape)
        .background(if (strong) glass.tintStrong else glass.tint, shape)
        .then(
            if (glass.edgeStrength > 0f) {
                Modifier.drawWithCache {
                    val brush = edge.brush(size, glass.edgeStrength)
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius
                        ?: CornerRadius(size.height / 2)
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(brush, cornerRadius = radius, style = Stroke(1.dp.toPx()))
                    }
                }
            } else {
                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            },
        )
}

/** Small caps label above a group, with an optional action on the right. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = Space.rowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            color = LocalGlass.current.contentSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().glassSurface().padding(vertical = Space.s)) {
        if (title != null) SectionLabel(title, Modifier.padding(top = Space.xs), action)
        content()
    }
}

/** Press = spring squish to 96% + haptic. */
fun Modifier.squishable(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pressed) { if (pressed) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey) }
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "squish",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun GlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    Row(
        modifier
            .squishable(interaction)
            .height(if (compact) 36.dp else 48.dp)
            .then(
                // Disabled stays opaque: a pinned button must never let content show through.
                if (primary) Modifier.clip(CircleShape).background(if (enabled) scheme.primary else scheme.surfaceContainerHighest)
                else Modifier.glassSurface(CircleShape, strong = true),
            )
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = when {
            primary && enabled -> scheme.onPrimary
            primary -> scheme.onSurfaceVariant
            else -> glass.content
        }
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(if (compact) 16.dp else 20.dp))
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        }
        Text(
            text,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 13.sp else 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An iOS-Settings-style inset row. Leading icon, title (+ subtitle), trailing control. */
@Composable
fun GlassRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val glass = LocalGlass.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(interaction, null, onClick = onClick) else Modifier)
            .heightIn(min = Space.rowMinHeight)
            // 4 dp: a 48 dp switch already brings its own touch padding.
            .padding(horizontal = Space.rowPadding, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(Space.m))
            }
            icon != null -> {
                Icon(icon, null, tint = iconTint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(Space.iconSize))
                Spacer(Modifier.width(Space.m))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = glass.content, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
            if (subtitle != null) Text(subtitle, color = glass.contentSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        trailing()
    }
}

@Composable
fun GlassToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    GlassRow(title, subtitle = subtitle, icon = icon, onClick = { onChange(!checked) }) {
        Spacer(Modifier.width(Space.m))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Label, slider and value on one line. The value is local while dragging so DataStore is
 * written once on release, not every frame.
 */
@Composable
fun GlassSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: (Float) -> String,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    val glass = LocalGlass.current
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.rowMinHeight).padding(horizontal = Space.rowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = glass.content, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(112.dp))
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
            modifier = Modifier.weight(1f),
        )
        Text(
            label(shown),
            color = glass.contentSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}

/** Pill-shaped segmented control. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    Row(
        modifier.fillMaxWidth().height(40.dp).glassSurface(CircleShape, strong = true).padding(Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(CircleShape)
                    .then(if (active) Modifier.background(scheme.primary) else Modifier)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (active) scheme.onPrimary else glass.content,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun RowDivider(start: Dp = Space.textInset) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalGlass.current.contentSecondary.copy(alpha = 0.18f)),
    )
}

@Composable
fun StatusDot(ok: Boolean, size: Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (ok) Color(0xFF2FB36A) else Color(0xFFF59E0B)),
    )
}
