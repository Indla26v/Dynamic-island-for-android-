package dev.indla.island.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.indla.island.ui.settings.theme.LocalGlass

private val HeaderMax = 96.dp
private val HeaderMin = 52.dp

/** Height the floating tab bar occupies above the navigation bar, including its margin. */
private val TabBarClearance = 52.dp + Space.m + Space.l

/**
 * One UI-style screen: a large title that collapses on scroll (with an optional control on the
 * right, in thumb reach once collapsed), the live preview pinned under it if the screen has
 * one, and the content.
 */
@Composable
fun TabScaffold(
    title: String,
    pinned: (@Composable () -> Unit)? = null,
    headerAction: (@Composable () -> Unit)? = null,
    itemSpacing: Dp = Space.cardGap,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val maxPx = with(density) { HeaderMax.toPx() }
    val minPx = with(density) { HeaderMin.toPx() }
    var headerPx by remember { mutableFloatStateOf(maxPx) }
    val connection = remember(maxPx, minPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val next = (headerPx + available.y).coerceAtLeast(minPx)
                val used = next - headerPx
                headerPx = next
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val next = (headerPx + available.y).coerceAtMost(maxPx)
                val used = next - headerPx
                headerPx = next
                return Offset(0f, used)
            }
        }
    }
    val progress = ((headerPx - minPx) / (maxPx - minPx)).coerceIn(0f, 1f)
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().nestedScroll(connection)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier
                .fillMaxWidth()
                .height(with(density) { headerPx.toDp() })
                .padding(start = Space.xl, end = Space.l, bottom = lerp(Space.s, Space.m, progress)),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                title,
                color = LocalGlass.current.content,
                fontSize = lerp(22.sp, 34.sp, progress),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            headerAction?.invoke()
        }
        if (pinned != null) Box(Modifier.padding(horizontal = Space.gutter)) { pinned() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadeTopEdge(Space.cardGap),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = Space.cardGap,
                bottom = TabBarClearance + navBar,
            ),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            content = content,
        )
    }
}

/** Cards scrolling under the pinned preview dissolve instead of meeting a hard edge. */
private fun Modifier.fadeTopEdge(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = height.toPx() / size.height
        drawRect(Brush.verticalGradient(0f to Color.Transparent, fade to Color.Black), blendMode = BlendMode.DstIn)
    }

data class TabItem(val label: String, val icon: ImageVector)

/** Floating glass pill tab bar. One of the (at most two) blurred layers on screen. */
@Composable
fun GlassTabBar(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val scheme = MaterialTheme.colorScheme
    val style = HazeBlurStyle {
        blurRadius(24.dp)
        backgroundColor(scheme.surface)
        colorEffects(listOf(HazeColorEffect.tint(if (glass.dark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.55f))))
    }
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = Space.m)
            .clip(CircleShape)
            .then(if (glass.solid) Modifier else Modifier.hazeBlur(HazeInput.Sources(hazeState), style))
            .glassSurface(CircleShape, strong = glass.solid)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        tabs.forEachIndexed { index, tab ->
            val interaction = remember { MutableInteractionSource() }
            val active = index == selected
            Row(
                Modifier
                    .squishable(interaction)
                    .height(40.dp)
                    .clip(CircleShape)
                    .then(if (active) Modifier.glassSurface(CircleShape, strong = true) else Modifier)
                    .clickable(interaction, null) { onSelect(index) }
                    .padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, tab.label, tint = if (active) scheme.primary else glass.contentSecondary, modifier = Modifier.size(20.dp))
                if (active) {
                    Spacer(Modifier.size(Space.s))
                    Text(tab.label, color = glass.content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}
