package dev.indla.island.ui.island

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.IslandGesture
import dev.indla.island.model.IslandUiState
import kotlin.math.abs

/** What the hit window reports back. Region ids come from [HitRegistry]. */
class IslandHitCallbacks(
    val onGesture: (IslandGesture) -> Unit,
    /** Region id and the tap point in render-window px (the scrubber needs where). */
    val onRegionTap: (String, Offset) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onReplySend: (String) -> Unit,
    val onReplyCancel: () -> Unit,
    /** The finger went down on (true) or left (false) the music bar; keeps the card open meanwhile. */
    val onScrub: (Boolean) -> Unit = {},
)

/**
 * Content of the invisible hit window. It sits exactly over the island's resting bounds, turns
 * touches into gestures or taps on regions the render window registered, and hosts the real
 * reply field (the only time the window takes focus, so the keyboard can attach).
 *
 * @param origin this window's top-left in render-window px.
 */
@Composable
fun IslandHitLayer(
    state: IslandUiState,
    registry: HitRegistry,
    origin: IntOffset,
    callbacks: IslandHitCallbacks,
) {
    val density = LocalDensity.current
    val latestState by rememberUpdatedState(state)
    val latestOrigin by rememberUpdatedState(origin)
    val swipeX = with(density) { 36.dp.toPx() }
    val swipeY = with(density) { 22.dp.toPx() }
    val seekSlopX = with(density) { 10.dp.toPx() }
    val seekSlopY = with(density) { 14.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state.replyOpen) {
                if (state.replyOpen) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val originPx = Offset(latestOrigin.x.toFloat(), latestOrigin.y.toFloat())
                    val bar = registry[HitRegistry.MEDIA_SEEK]
                    // The music bar takes the touch at once: no long-press wait, no slop, and a
                    // target taller than the thin track so a thumb lands on it.
                    if (bar != null && !latestState.positionEditing &&
                        Rect(bar.left - seekSlopX, bar.top - seekSlopY, bar.right + seekSlopX, bar.bottom + seekSlopY)
                            .contains(start + originPx)
                    ) {
                        fun fractionAt(p: Offset) = ((p.x + originPx.x - bar.left) / bar.width).coerceIn(0f, 1f)
                        registry.scrubbing = true
                        registry.scrub = fractionAt(start)
                        callbacks.onScrub(true)
                        var last = start
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            last = change.position
                            registry.scrub = fractionAt(last)
                            change.consume()
                        }
                        registry.scrubReleasedAt = android.os.SystemClock.elapsedRealtime()
                        registry.scrubbing = false
                        callbacks.onRegionTap(HitRegistry.MEDIA_SEEK, last + originPx)
                        callbacks.onScrub(false)
                        return@awaitEachGesture
                    }
                    var outcome = Outcome.LONG_PRESS
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                outcome = Outcome.TAP
                                break
                            }
                            if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                                outcome = Outcome.DRAG
                                break
                            }
                        }
                    }
                    when (outcome) {
                        Outcome.TAP -> {
                            val point = start + Offset(latestOrigin.x.toFloat(), latestOrigin.y.toFloat())
                            val region = registry.find(point)
                            if (region != null) callbacks.onRegionTap(region, point) else callbacks.onGesture(IslandGesture.TAP)
                        }
                        Outcome.LONG_PRESS -> {
                            if (!latestState.positionEditing) callbacks.onGesture(IslandGesture.LONG_PRESS)
                            // Swallow the rest of this touch.
                            while (awaitPointerEvent().changes.any { it.pressed }) Unit
                        }
                        Outcome.DRAG -> {
                            var last = start
                            var end = start
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (latestState.positionEditing) {
                                    callbacks.onDrag(change.position - last)
                                    change.consume()
                                }
                                last = change.position
                                end = change.position
                                if (!change.pressed) break
                            }
                            if (latestState.positionEditing) {
                                callbacks.onDragEnd()
                            } else {
                                val d = end - start
                                val gesture = when {
                                    abs(d.x) > abs(d.y) && abs(d.x) > swipeX ->
                                        if (d.x < 0) IslandGesture.SWIPE_LEFT else IslandGesture.SWIPE_RIGHT
                                    abs(d.y) > swipeY -> if (d.y < 0) IslandGesture.SWIPE_UP else IslandGesture.SWIPE_DOWN
                                    else -> null
                                }
                                gesture?.let(callbacks.onGesture)
                            }
                        }
                    }
                }
            },
    ) {
        val replyRect = registry[HitRegistry.REPLY_ROW]
        if (state.replyOpen && replyRect != null) {
            ReplyField(
                hint = "Reply to ${state.current?.title.orEmpty()}",
                accent = Color(state.appearance.accentArgb),
                modifier = Modifier
                    .offset { IntOffset(replyRect.left.toInt() - origin.x, replyRect.top.toInt() - origin.y) }
                    .size(with(density) { replyRect.width.toDp() }, with(density) { replyRect.height.toDp() }),
                onSend = callbacks.onReplySend,
                onCancel = callbacks.onReplyCancel,
            )
        }
    }
}

private enum class Outcome { TAP, LONG_PRESS, DRAG }

@Composable
private fun ReplyField(
    hint: String,
    accent: Color,
    modifier: Modifier,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // The window turns focusable on the same state change; give it two frames to apply.
        withFrameNanos { }
        withFrameNanos { }
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Row(modifier.clip(CircleShape).background(Color(0xFF2A2A2E)), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Close, "Cancel reply", tint = Color(0xB3FFFFFF), modifier = Modifier.size(18.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(hint, color = Color(0x80FFFFFF), fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend(text) }),
                modifier = Modifier.focusRequester(focus),
            )
        }
        IconButton(
            onClick = { if (text.isNotBlank()) onSend(text) },
            modifier = Modifier.padding(end = 4.dp).size(40.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                "Send",
                tint = if (text.isBlank()) Color(0x66FFFFFF) else accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
