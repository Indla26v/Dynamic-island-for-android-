package dev.indla.island.service.overlay

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.indla.island.model.LiveContent
import dev.indla.island.model.SystemEvent

/**
 * "Copied" glances. Android lets only the app in front (and the keyboard) read the clipboard,
 * so Island watches for the copy itself: it remembers the text the user last selected, and when
 * a Copy button is tapped (or a "Copied" toast appears) it shows that text. Password fields are
 * never remembered, and nothing is kept beyond the last selection.
 */
internal class CopyWatcher(private val emit: (LiveContent.System) -> Unit) {
    private var selection: String? = null
    private var selectedAt = 0L
    private var lastCopyAt = 0L

    fun onSelection(event: AccessibilityEvent) {
        if (event.isPassword) {
            selection = null
            return
        }
        val text = event.text.firstOrNull()?.toString() ?: return
        val from = minOf(event.fromIndex, event.toIndex)
        val to = maxOf(event.fromIndex, event.toIndex)
        if (from < 0 || to > text.length || to - from < 1) return
        selection = text.substring(from, to)
        selectedAt = SystemClock.uptimeMillis()
    }

    fun onAction(event: AccessibilityEvent) {
        val label = (event.text.joinToString(" ") + " " + (event.contentDescription ?: "")).trim()
        val copied = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> COPY_BUTTON.matches(label)
            // Toasts arrive as notification-state events; the class name says Toast.
            else -> event.className?.contains("Toast") == true && COPIED_TOAST.containsMatchIn(label)
        }
        if (!copied) return
        val now = SystemClock.uptimeMillis()
        // A Copy tap and its toast are one copy.
        if (now - lastCopyAt < DEBOUNCE_MS) return
        lastCopyAt = now
        val text = selection?.takeIf { now - selectedAt < SELECTION_TTL_MS }
            ?.replace(WHITESPACE, " ")?.trim()?.take(MAX_CHARS)
        emit(LiveContent.System(SystemEvent.CLIPBOARD, null, text?.takeIf { it.isNotEmpty() } ?: "To clipboard"))
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
        const val SELECTION_TTL_MS = 60_000L
        const val MAX_CHARS = 120
        val COPY_BUTTON = Regex("""(?i)\s*(copy|copy text|copy link|copy to clipboard)\s*""")
        val COPIED_TOAST = Regex("""(?i)\bcopied\b""")
        val WHITESPACE = Regex("""\s+""")
    }
}
