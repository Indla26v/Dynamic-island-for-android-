package dev.indla.island.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.indla.island.model.IslandItem
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Damping ~0.8 and low-medium stiffness: settles without bounce-back. */
private val MorphSpring = spring<IslandRect>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
private const val FADE_OUT_MS = 90
private const val CONTENT_DELAY_MS = 80L
private const val FADE_IN_MS = 170

private val RectConverter = TwoWayConverter<IslandRect, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.left + it.width / 2, it.top, it.width, it.height) },
    convertFromVector = { v ->
        // Radius follows the height so the pill never pinches mid-morph; cards cap at 30 dp.
        IslandRect(v.v1 - v.v3 / 2, v.v2, v.v3, v.v4, minOf(v.v4 / 2, IslandLayout.EXPANDED_RADIUS))
    },
)

/**
 * The island. One spring animates a single shape between states; everything per-frame happens
 * in draw and layer lambdas, so nothing recomposes while it moves. Content of the previous state
 * fades out before a shrink, and new content fades in ~80 ms after the shape starts moving.
 *
 * @param hitRegistry receives the bounds of interactive spots (the overlay's hit window uses it).
 * @param drawReplyHint draw the reply placeholder; the overlay's hit window draws the real field.
 * @param backdropBlur a real blur window sits behind the expanded card, so the tint can be lighter.
 */
@Composable
fun IslandSurface(
    state: IslandUiState,
    layout: IslandLayout,
    modifier: Modifier = Modifier,
    hitRegistry: HitRegistry? = null,
    reduceTransparency: Boolean = false,
    drawReplyHint: Boolean = true,
    backdropBlur: Boolean = false,
) {
    val target = layout.rectFor(state)
    val shape = remember { Animatable(target, RectConverter) }
    val contentAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(state) }
    val latest by rememberUpdatedState(state)
    val key = contentKey(state)

    LaunchedEffect(key, target) {
        val changed = contentKey(shown) != key
        val shrinking = target.width * target.height < shape.value.width * shape.value.height - 1f
        if (changed && shrinking) contentAlpha.animateTo(0f, tween(FADE_OUT_MS))
        // Launched outside this effect so a retarget keeps the running spring's velocity.
        scope.launch { shape.animateTo(target, MorphSpring) }
        if (changed) {
            if (!shrinking) contentAlpha.animateTo(0f, tween(CONTENT_DELAY_MS.toInt()))
            else delay(CONTENT_DELAY_MS)
            shown = latest
            contentAlpha.animateTo(1f, tween(FADE_IN_MS))
        } else {
            shown = latest
            if (contentAlpha.value < 1f) contentAlpha.animateTo(1f, tween(FADE_IN_MS))
        }
    }

    val visibility by animateFloatAsState(if (state.visible) 1f else 0f, tween(160), label = "visibility")
    // With the resting pill off, the shape shrinks onto the camera and then fades away; it comes
    // back instantly when something new arrives, so the morph still starts from the camera.
    val hiddenRest = state.mode == IslandMode.REST && !state.showsRestPill
    val shapeAlpha by animateFloatAsState(
        if (hiddenRest) 0f else 1f,
        if (hiddenRest) tween(durationMillis = 180, delayMillis = 220) else snap(),
        label = "shapeAlpha",
    )
    val bubbleScale by animateFloatAsState(
        if (state.mode == IslandMode.SPLIT && state.visible) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "bubble",
    )
    val appearance = state.appearance
    val accent = Color(appearance.accentArgb)
    val intensity = appearance.glassIntensity.coerceIn(0f, 1f)
    val tint by animateFloatAsState(
        when {
            // Solid black by default: the expanded card matches the pill and the camera.
            !appearance.glassCard || reduceTransparency -> 1f
            backdropBlur -> 0.5f + 0.3f * intensity
            else -> 0.78f + 0.17f * intensity
        },
        tween(220),
        label = "tint",
    )
    val attached = layout.attachedToTop
    val edge = remember { GlassEdge() }

    Box(modifier.fillMaxSize().graphicsLayer { alpha = visibility }) {
        Canvas(Modifier.fillMaxSize()) {
            val r = shape.value
            // 0 while a pill, 1 once it's a card: the pill is solid black so it merges with
            // the camera hole; the card becomes dark frosted glass with a lit edge.
            val glass = ((r.height - layout.restHeight) / 56f).coerceIn(0f, 1f)
            if (shapeAlpha <= 0.01f) return@Canvas
            drawIsland(r, attached, Color.Black.copy(alpha = (1f - (1f - tint) * glass) * shapeAlpha))
            if (glass > 0.01f && appearance.glassCard) {
                val px = r.toPx(this)
                drawIslandOutline(r, attached, edge.brush(Size(px.width, px.height), 0.55f * glass), px.topLeft)
            }
            if (state.positionEditing) {
                drawIsland(r, attached, accent, stroke = Stroke(width = 2.dp.toPx()))
            }
        }

        if (bubbleScale > 0.01f) {
            state.secondary?.let { Bubble(it, layout.bubble(), bubbleScale, accent, hitRegistry, HitRegistry.BUBBLE) }
            state.tertiary?.let { Bubble(it, layout.bubbleLeft(), bubbleScale, accent, hitRegistry, HitRegistry.BUBBLE_LEFT) }
        }

        val shownRect = layout.rectFor(shown)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha.value
                    this.shape = IslandClip(shape.value, attached)
                    clip = true
                },
        ) {
            Box(
                Modifier
                    .offset(shownRect.left.dp, shownRect.top.dp)
                    .requiredSize(shownRect.width.dp, shownRect.height.dp),
            ) {
                IslandContent(shown, layout, shownRect, accent, hitRegistry, drawReplyHint)
            }
        }
    }
}

/** A black circle beside the pill showing another item: a ring, art, a call, an avatar. */
@Composable
private fun Bubble(item: IslandItem, rect: IslandRect, scale: Float, accent: Color, registry: HitRegistry?, id: String) {
    Box(
        Modifier
            .offset(rect.left.dp, rect.top.dp)
            .size(rect.width.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.shape = androidx.compose.foundation.shape.CircleShape
                clip = true
            }
            .hitRegion(registry, id),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawCircle(Color.Black) }
        BubbleContent(item, rect.width.dp, accent)
    }
}

/**
 * What makes the content crossfade. A live activity only counts its stable part (a new track, a
 * call answered), so a download tick or a timer second updates in place instead of reloading.
 */
private fun contentKey(state: IslandUiState): Any {
    val item = state.current
    val content: Any? = if (item?.live != null) listOf(item.kind, item.title, item.live?.stableKey) else item?.contentHash
    return listOf(state.mode, item?.key, content, state.replyOpen, state.quickActionsOpen)
}

private data class PxRect(val topLeft: Offset, val width: Float, val height: Float, val radius: Float)

private fun IslandRect.toPx(density: Density) = with(density) {
    PxRect(Offset(left.dp.toPx(), top.dp.toPx()), width.dp.toPx(), height.dp.toPx(), radius.dp.toPx())
}

private fun roundRect(px: PxRect, attached: Boolean): RoundRect {
    val corner = CornerRadius(px.radius)
    val top = if (attached) CornerRadius.Zero else corner
    return RoundRect(
        left = px.topLeft.x,
        top = px.topLeft.y,
        right = px.topLeft.x + px.width,
        bottom = px.topLeft.y + px.height,
        topLeftCornerRadius = top,
        topRightCornerRadius = top,
        bottomRightCornerRadius = corner,
        bottomLeftCornerRadius = corner,
    )
}

private fun DrawScope.drawIsland(r: IslandRect, attached: Boolean, color: Color, stroke: Stroke? = null) {
    val px = r.toPx(this)
    if (px.width <= 0f || px.height <= 0f) return
    val path = Path().apply { addRoundRect(roundRect(px, attached)) }
    if (stroke == null) drawPath(path, color) else drawPath(path, color, style = stroke)
}

private fun DrawScope.drawIslandOutline(
    r: IslandRect,
    attached: Boolean,
    brush: androidx.compose.ui.graphics.Brush,
    origin: Offset,
) {
    val px = r.toPx(this)
    val path = Path().apply { addRoundRect(roundRect(px, attached)) }
    // The shader works in shape-local space, so draw it translated to the shape's origin.
    path.translate(Offset(-origin.x, -origin.y))
    translate(origin.x, origin.y) {
        drawPath(path, brush, style = Stroke(width = 1.dp.toPx()))
    }
}

/** Clips content to the island's current (animating) shape. */
private class IslandClip(private val rect: IslandRect, private val attached: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(roundRect(rect.toPx(density), attached))
}
