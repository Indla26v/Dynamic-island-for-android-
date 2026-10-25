package dev.indla.island.service.listener

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.ProgressBar
import android.widget.FrameLayout
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Some apps (vivo's Clock among them) draw their timer as a custom view with a running
 * Chronometer and leave the title, text and chronometer extras empty. Inflating the custom view
 * here, with the public RemoteViews API, recovers the countdown and the label.
 */
@Singleton
class CustomViewReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    class Timer(
        val label: String?,
        val baseWallMs: Long?,
        val countDown: Boolean,
        val shownTime: String?,
        /** The view has its own play/pause and stop buttons the island can press. */
        val hasToggle: Boolean = false,
        val hasStop: Boolean = false,
    )

    enum class Control { TOGGLE, STOP }

    /** What a custom-drawn notification says: its visible text in order, and its progress bar. */
    class Content(val texts: List<String>, val progress: Int?, val max: Int?)

    /**
     * Delivery and ride apps (Zomato, Swiggy, redBus...) often draw their tracking entirely in a
     * custom view and leave the title and text empty; inflate it and read what it shows.
     */
    fun content(n: Notification): Content? {
        @Suppress("DEPRECATION")
        val views = n.bigContentView ?: n.contentView ?: return null
        val root = runCatching { views.apply(context, FrameLayout(context)) }.getOrNull() ?: return null
        val texts = buildList { collectTexts(root, this) }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val bar = find(root) { it is ProgressBar && it.visibility == View.VISIBLE && !it.isIndeterminate && it.max > 0 } as? ProgressBar
        if (texts.isEmpty() && bar == null) return null
        return Content(texts.take(MAX_TEXTS), bar?.progress, bar?.max)
    }

    /** Only for ongoing notifications whose content lives entirely in a custom view. */
    fun timer(n: Notification): Timer? {
        @Suppress("DEPRECATION")
        val views = n.contentView ?: n.bigContentView ?: return null
        val root = runCatching { views.apply(context, FrameLayout(context)) }.getOrNull() ?: return null
        val chronometer = find(root) { it is Chronometer } as? Chronometer ?: return null
        val texts = buildList { collectTexts(root, this) }.filter { it.isNotBlank() && !TIME.matches(it) }
        // Paused shows up in one of three places: notification actions offering Resume/Start,
        // the custom view's own buttons and status ("Paused", a Continue button: vivo
        // Recorder), or the chronometer itself, stopped by RemoteViews.setChronometer.
        val paused = n.actions.orEmpty().any { PAUSED_ACTION.containsMatchIn(it.title?.toString().orEmpty()) } ||
            buildList { collectLabels(root, this) }.any { PAUSED_LABEL.containsMatchIn(it) } ||
            started(chronometer) == false
        val base = chronometer.base
        return Timer(
            label = texts.firstOrNull(),
            baseWallMs = if (paused) null else System.currentTimeMillis() + (base - SystemClock.elapsedRealtime()),
            countDown = chronometer.isCountDown,
            shownTime = chronometer.text?.toString(),
            hasToggle = control(root, Control.TOGGLE) != null,
            hasStop = control(root, Control.STOP) != null,
        )
    }

    /**
     * Presses a button inside the app's own notification view. The view is inflated here from
     * the posted RemoteViews, so its click sends the app's own PendingIntent, exactly as a tap
     * on it in the shade would; the app never has to open.
     */
    fun press(n: Notification, control: Control): Boolean {
        @Suppress("DEPRECATION")
        val views = n.contentView ?: n.bigContentView ?: return false
        val root = runCatching { views.apply(context, FrameLayout(context)) }.getOrNull() ?: return false
        val button = control(root, control) ?: return false
        return runCatching { button.performClick() }.getOrDefault(false)
    }

    /** A clickable view whose label says what it does ("Pause", "Recording", "Done"). */
    private fun control(root: View, control: Control): View? {
        val words = if (control == Control.TOGGLE) TOGGLE_LABEL else STOP_LABEL
        return find(root) { v ->
            v.visibility == View.VISIBLE && v.hasOnClickListeners() && words.matches(label(v))
        }
    }

    private fun label(v: View): String =
        (v.contentDescription ?: (v as? TextView)?.text)?.toString()?.trim().orEmpty()

    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) find(view.getChildAt(i), predicate)?.let { return it }
        }
        return null
    }

    /** Visible text and content descriptions: icon-only buttons say what they do here. */
    private fun collectLabels(view: View, out: MutableList<String>) {
        if (view.visibility != View.VISIBLE) return
        view.contentDescription?.let { out += it.toString() }
        if (view is TextView && view !is Chronometer) out += view.text?.toString().orEmpty()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectLabels(view.getChildAt(i), out)
    }

    /** Whether the chronometer was started; null where the platform hides the field. */
    private fun started(chronometer: Chronometer): Boolean? = runCatching {
        Chronometer::class.java.getDeclaredField("mStarted").apply { isAccessible = true }.getBoolean(chronometer)
    }.getOrNull()

    private fun collectTexts(view: View, out: MutableList<String>) {
        if (view is TextView && view !is Chronometer && view.visibility == View.VISIBLE) out += view.text?.toString().orEmpty()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectTexts(view.getChildAt(i), out)
    }

    private companion object {
        val TIME = Regex("""-?\d{1,2}(:\d{2}){1,2}""")
        const val MAX_TEXTS = 4
        val PAUSED_ACTION = Regex("""(?i)\b(resume|start|continue)\b""")
        val PAUSED_LABEL = Regex("""(?i)\b(paused|resume|continue)\b""")
        // Whole labels only: "Recording" is the resume button in vivo Recorder's view.
        val TOGGLE_LABEL = Regex("""(?i)(pause|resume|continue|play|start|record|recording|continue recording|pause recording)""")
        val STOP_LABEL = Regex("""(?i)(stop|done|end|save|finish|stop recording)""")
    }
}
