package dev.indla.island.ui.settings.components

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.IslandAppearance
import dev.indla.island.service.overlay.ScreenMetrics
import dev.indla.island.ui.island.CutoutKind
import dev.indla.island.ui.island.IslandLayout
import dev.indla.island.ui.island.IslandSurface
import dev.indla.island.ui.island.PreviewSamples

private val PreviewHeight = 200.dp

/** The mock phone is shown smaller than the card so even the expanded card fits with a margin. */
private const val MockScale = 0.74f

/**
 * The live preview pinned on top of every tab: the real island composable on a mock of this
 * phone's top edge, laid out against this device's actual cutout. Tap to cycle states.
 */
@Composable
fun IslandPreview(appearance: IslandAppearance, reduceTransparency: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val metrics = remember { ScreenMetrics.read(context, context.getSystemService(WindowManager::class.java)) }
    var step by rememberSaveable { mutableIntStateOf(2) }
    val state = PreviewSamples.state(step, context.packageName, appearance)
    val layout = remember(metrics, appearance) {
        IslandLayout.compute(metrics.cutout, metrics.widthDp, metrics.statusBarDp, appearance, metrics.heightDp)
    }
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .squishable(interaction)
            .glassSurface()
            .clickable(interaction, null) { step++ },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = (maxWidth.value / metrics.widthDp).coerceAtMost(1f) * MockScale
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .requiredSize(metrics.widthDp.dp, (PreviewHeight.value / scale).dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = 12.dp.toPx()
                        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp)
                        clip = true
                    },
            ) {
                MockWallpaper(layout, metrics.statusBarDp)
                IslandSurface(state, layout, reduceTransparency = reduceTransparency)
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(PreviewSamples.stepNames[step.mod(4)], color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text("· tap to cycle", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MockWallpaper(layout: IslandLayout, statusBarDp: Float) {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondaryContainer)))
        // Status bar: time on the left, signal and battery on the right, all generic shapes.
        val sb = statusBarDp.dp.toPx()
        val y = sb / 2
        val ink = Color.White.copy(alpha = 0.9f)
        drawRoundRect(ink, Offset(22.dp.toPx(), y - 5.dp.toPx()), Size(30.dp.toPx(), 10.dp.toPx()), CornerRadius(5.dp.toPx()))
        val right = size.width - 22.dp.toPx()
        drawRoundRect(ink, Offset(right - 22.dp.toPx(), y - 5.dp.toPx()), Size(22.dp.toPx(), 10.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawCircle(ink, 4.dp.toPx(), Offset(right - 34.dp.toPx(), y))
        if (layout.kind != CutoutKind.NONE) {
            val hole = layout.hole
            drawRoundRect(
                Color(0xFF050505),
                Offset(hole.left.dp.toPx(), hole.top.dp.toPx()),
                Size(hole.width.dp.toPx(), hole.height.dp.toPx()),
                CornerRadius(hole.radius.dp.toPx()),
            )
        }
    }
}
