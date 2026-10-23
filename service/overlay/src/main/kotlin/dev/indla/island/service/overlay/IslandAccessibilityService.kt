package dev.indla.island.service.overlay

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.indla.island.data.SettingsRepository
import dev.indla.island.data.icons.IconStore
import dev.indla.island.engine.IslandEngine
import dev.indla.island.model.Environment
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandUiState
import dev.indla.island.ui.island.HitRegistry
import dev.indla.island.ui.island.IslandHitCallbacks
import dev.indla.island.ui.island.IslandHitLayer
import dev.indla.island.ui.island.IslandLayout
import dev.indla.island.ui.island.IslandRect
import dev.indla.island.ui.island.IslandSurface
import dev.indla.island.ui.island.LocalIconStore
import dev.indla.island.ui.island.Transparency
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the island over the status bar. A normal TYPE_APPLICATION_OVERLAY renders below the
 * status bar and can't sit on the cutout, so this uses TYPE_ACCESSIBILITY_OVERLAY.
 *
 * Two windows, so nothing is resized per frame:
 * 1. Render window: fixed at the largest size, not touchable, transparent. Compose morphs the
 *    island inside it on the GPU.
 * 2. Hit window: invisible, sized to the island's resting bounds, updated once per state change.
 *    It catches taps and swipes; everything else passes through. It becomes focusable only while
 *    the reply box is open, so the keyboard can attach.
 */
@AndroidEntryPoint
class IslandAccessibilityService : AccessibilityService() {
    @Inject lateinit var engine: IslandEngine
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var icons: IconStore

    private val scope = MainScope()
    private val owner = OverlayLifecycleOwner()
    private val registry = HitRegistry()
    private lateinit var windowManager: WindowManager
    private lateinit var keyguard: KeyguardManager

    private var renderView: ComposeView? = null
    private var hitHost: HitHostView? = null
    private val renderParams = baseParams(LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_NOT_FOCUSABLE)
    private val hitParams = baseParams(hitFlags(touchable = false, focusable = false))

    private val metrics = mutableStateOf<ScreenMetrics?>(null)
    /** Live offset while the user drags the island in position-edit mode, in dp. */
    private val dragOffset = mutableStateOf<Offset?>(null)
    private val hitOrigin = mutableStateOf(IntOffset.Zero)
    private val reduceTransparency = mutableStateOf(false)
    private var imePackages: Set<String> = emptySet()
    private var lastMode = IslandMode.REST

    override fun onServiceConnected() {
        windowManager = getSystemService(WindowManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        imePackages = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
        reduceTransparency.value = Transparency.reduced(this)
        metrics.value = ScreenMetrics.read(this, windowManager)
        owner.start()
        addRenderWindow()
        addHitWindow()
        registerScreenReceiver()
        updateEnvironment { it.copy(landscape = metrics.value?.landscape == true, locked = keyguard.isKeyguardLocked) }

        scope.launch {
            combine(engine.state, snapshotFlow { metrics.value to dragOffset.value }) { s, _ -> s }
                .collect(::onState)
        }
        engine.setOverlayConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        when {
            pkg == SYSTEM_UI -> if (looksLikeShade(event)) updateEnvironment { it.copy(shadeOpen = true) }
            pkg in imePackages || cls.startsWith("android.inputmethodservice") -> Unit
            else -> updateEnvironment { it.copy(foregroundPackage = pkg, shadeOpen = false) }
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (renderView == null) return
        reduceTransparency.value = Transparency.reduced(this)
        val m = ScreenMetrics.read(this, windowManager)
        metrics.value = m
        updateRenderWindowSize()
        updateEnvironment { it.copy(landscape = m.landscape) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        scope.cancel()
        super.onDestroy()
    }

    // ---- Windows -------------------------------------------------------------------------------

    private fun addRenderWindow() {
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by engine.state.collectAsState()
                val layout = layoutFor(state.appearance) ?: return@setContent
                CompositionLocalProvider(LocalIconStore provides icons) {
                    IslandSurface(
                        state = state,
                        layout = layout,
                        hitRegistry = registry,
                        reduceTransparency = reduceTransparency.value,
                        drawReplyHint = false,
                    )
                }
            }
        }
        owner.attach(view)
        watchFullscreen(view)
        renderParams.apply {
            width = LayoutParams.MATCH_PARENT
            title = "Island"
        }
        renderView = view
        updateRenderWindowSize(add = true)
    }

    private fun addHitWindow() {
        val host = HitHostView(this, onOutside = { engine.onOutsideTouch() }, onBack = { engine.onReplyCancelled() })
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by engine.state.collectAsState()
                IslandHitLayer(state, registry, hitOrigin.value, callbacks)
            }
        }
        host.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        owner.attach(host)
        hitParams.apply {
            width = 1
            height = 1
            title = "Island touch"
            softInputMode = LayoutParams.SOFT_INPUT_ADJUST_NOTHING or LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }
        windowManager.addView(host, hitParams)
        hitHost = host
    }

    private fun updateRenderWindowSize(add: Boolean = false) {
        val view = renderView ?: return
        val m = metrics.value ?: return
        val layout = layoutFor(IslandAppearance()) ?: return
        // Tall enough for the biggest card, plus room for a downward position offset.
        val maxHeightDp = layout.top + 48f + layout.headerHeight + 3 * IslandLayout.LINE +
            IslandLayout.GAP + IslandLayout.BUTTON_ROW + IslandLayout.PADDING_BOTTOM + 96f
        renderParams.height = (maxHeightDp * m.density).roundToInt()
        if (add) windowManager.addView(view, renderParams) else runCatching { windowManager.updateViewLayout(view, renderParams) }
    }

    private fun onState(state: IslandUiState) {
        val host = hitHost ?: return
        val m = metrics.value ?: return
        val pending = dragOffset.value
        if (pending != null && state.appearance.offsetXDp == pending.x && state.appearance.offsetYDp == pending.y) {
            dragOffset.value = null
        }
        val layout = layoutFor(state.appearance) ?: return

        if (state.mode == IslandMode.EXPANDED && lastMode != IslandMode.EXPANDED && state.visible) {
            host.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        lastMode = state.mode

        val bounds: IslandRect? = when {
            !state.visible -> null
            state.positionEditing && state.mode == IslandMode.REST -> layout.rest().grow(14f)
            state.mode == IslandMode.REST -> null
            state.mode == IslandMode.EXPANDED -> layout.expanded(layout.expandedHeight(state))
            state.mode == IslandMode.SPLIT -> layout.compact().union(layout.bubble())
            else -> layout.compact()
        }
        val d = m.density
        val touchable = bounds != null
        val focusable = touchable && state.replyOpen
        val x = bounds?.let { (it.left * d).roundToInt() } ?: 0
        val y = bounds?.let { (it.top * d).roundToInt() } ?: 0
        val w = bounds?.let { max(1, (it.width * d).roundToInt()) } ?: 1
        val h = bounds?.let { max(1, (it.height * d).roundToInt()) } ?: 1
        val flags = hitFlags(touchable, focusable)
        if (hitParams.x != x || hitParams.y != y || hitParams.width != w || hitParams.height != h || hitParams.flags != flags) {
            hitParams.x = x
            hitParams.y = y
            hitParams.width = w
            hitParams.height = h
            hitParams.flags = flags
            hitOrigin.value = IntOffset(x, y)
            runCatching { windowManager.updateViewLayout(host, hitParams) }
        }
    }

    private fun layoutFor(appearance: IslandAppearance): IslandLayout? {
        val m = metrics.value ?: return null
        val drag = dragOffset.value
        val effective = if (drag != null) appearance.copy(offsetXDp = drag.x, offsetYDp = drag.y) else appearance
        return IslandLayout.compute(m.cutout, m.widthDp, m.statusBarDp, effective, m.heightDp)
    }

    private val callbacks = IslandHitCallbacks(
        onGesture = { gesture ->
            if (gesture == dev.indla.island.model.IslandGesture.LONG_PRESS) {
                hitHost?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            engine.onGesture(gesture)
        },
        onRegionTap = { id ->
            hitHost?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val state = engine.state.value
            val action = HitRegistry.actionIndex(id)
            when {
                action != null -> engine.onActionTapped(action)
                id == HitRegistry.BUBBLE -> engine.onSelectItem(state.items.indexOf(state.secondary))
                id == HitRegistry.QUICK_MUTE -> engine.onMuteApp()
                id == HitRegistry.QUICK_DISMISS -> engine.onDismissCurrent()
                else -> engine.onGesture(dev.indla.island.model.IslandGesture.TAP)
            }
        },
        onDrag = { delta ->
            val m = metrics.value
            if (m != null) {
                val a = engine.state.value.appearance
                val current = dragOffset.value ?: Offset(a.offsetXDp, a.offsetYDp)
                dragOffset.value = Offset(
                    (current.x + delta.x / m.density).coerceIn(-m.widthDp / 2, m.widthDp / 2),
                    (current.y + delta.y / m.density).coerceIn(-40f, 160f),
                )
            }
        },
        onDragEnd = {
            dragOffset.value?.let { offset ->
                scope.launch { settings.updateAppearance { it.copy(offsetXDp = offset.x, offsetYDp = offset.y) } }
            }
        },
        onReplySend = { engine.onReplySent(it) },
        onReplyCancel = { engine.onReplyCancelled() },
    )

    // ---- Environment ---------------------------------------------------------------------------

    private fun updateEnvironment(transform: (Environment) -> Environment) {
        engine.updateEnvironment(transform(engine.environment))
    }

    private fun watchFullscreen(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.setOnApplyWindowInsetsListener { v, insets ->
                val hidden = !insets.isVisible(WindowInsets.Type.statusBars())
                updateEnvironment { it.copy(fullscreen = hidden) }
                v.onApplyWindowInsets(insets)
            }
        } else {
            @Suppress("DEPRECATION")
            view.setOnSystemUiVisibilityChangeListener { flags ->
                updateEnvironment { it.copy(fullscreen = flags and View.SYSTEM_UI_FLAG_FULLSCREEN != 0) }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val locked = intent.action == Intent.ACTION_SCREEN_OFF || keyguard.isKeyguardLocked
            updateEnvironment { it.copy(locked = locked, shadeOpen = false) }
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun looksLikeShade(event: AccessibilityEvent): Boolean {
        val text = (event.text.joinToString(" ") + " " + (event.contentDescription ?: "")).lowercase()
        val cls = event.className?.toString().orEmpty()
        return "shade" in text || "quick settings" in text || "notification" in text ||
            "Shade" in cls || "NotificationPanel" in cls
    }

    private fun tearDown() {
        if (renderView == null && hitHost == null) return
        engine.setOverlayConnected(false)
        runCatching { unregisterReceiver(screenReceiver) }
        renderView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        hitHost?.let { runCatching { windowManager.removeViewImmediate(it) } }
        renderView = null
        hitHost = null
        owner.destroy()
    }

    /** Catches outside touches (collapse) and Back (close the reply box) for the hit window. */
    private class HitHostView(
        context: Context,
        private val onOutside: () -> Unit,
        private val onBack: () -> Unit,
    ) : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                onOutside()
                return true
            }
            return super.dispatchTouchEvent(ev)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"

        fun component(context: Context) = ComponentName(context, IslandAccessibilityService::class.java)

        private fun hitFlags(touchable: Boolean, focusable: Boolean): Int {
            var flags = LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                LayoutParams.FLAG_HARDWARE_ACCELERATED
            if (!focusable) flags = flags or LayoutParams.FLAG_NOT_FOCUSABLE
            if (!touchable) flags = flags or LayoutParams.FLAG_NOT_TOUCHABLE
            return flags
        }

        private fun baseParams(flags: Int) = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags or LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }

        private fun IslandRect.grow(by: Float) = IslandRect(left - by, top - by / 2, width + 2 * by, height + by, radius)

        private fun IslandRect.union(o: IslandRect): IslandRect {
            val l = minOf(left, o.left)
            val t = minOf(top, o.top)
            return IslandRect(l, t, maxOf(right, o.right) - l, maxOf(bottom, o.bottom) - t, radius)
        }
    }
}
