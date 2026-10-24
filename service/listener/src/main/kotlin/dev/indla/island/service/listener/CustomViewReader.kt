package dev.indla.island.service.listener

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
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
    class Timer(val label: String?, val baseWallMs: Long?, val countDown: Boolean, val shownTime: String?)

    /** Only for ongoing notifications whose content lives entirely in a custom view. */
    fun timer(n: Notification): Timer? {
        @Suppress("DEPRECATION")
        val views = n.contentView ?: n.bigContentView ?: return null
        val root = runCatching { views.apply(context, FrameLayout(context)) }.getOrNull() ?: return null
        val chronometer = find(root) { it is Chronometer } as? Chronometer ?: return null
        val texts = buildList { collectTexts(root, this) }.filter { it.isNotBlank() && !TIME.matches(it) }
        // A paused timer offers Resume/Start instead of Pause; its chronometer is frozen.
        val paused = n.actions.orEmpty().any { PAUSED_ACTION.containsMatchIn(it.title?.toString().orEmpty()) }
        val base = chronometer.base
        return Timer(
            label = texts.firstOrNull(),
            baseWallMs = if (paused) null else System.currentTimeMillis() + (base - SystemClock.elapsedRealtime()),
            countDown = chronometer.isCountDown,
            shownTime = chronometer.text?.toString(),
        )
    }

    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) find(view.getChildAt(i), predicate)?.let { return it }
        }
        return null
    }

    private fun collectTexts(view: View, out: MutableList<String>) {
        if (view is TextView && view !is Chronometer && view.visibility == View.VISIBLE) out += view.text?.toString().orEmpty()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectTexts(view.getChildAt(i), out)
    }

    private companion object {
        val TIME = Regex("""-?\d{1,2}(:\d{2}){1,2}""")
        val PAUSED_ACTION = Regex("""(?i)\b(resume|start|continue)\b""")
    }
}
