package dev.indla.island.service.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Play/pause and stop for apps that give other apps no controls (vivo Recorder has no
 * notification actions at all). The island has already opened the app; this finds the app's
 * own button on screen, presses it, and takes the user back to what they were doing. Only ever
 * runs right after the user tapped the matching island button.
 */
internal class ControlPress(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
) {
    enum class Kind { TOGGLE, STOP }

    private var job: Job? = null

    /**
     * @param returnTo the app in front before the island opened [packageName]; null or the
     *   launcher goes home.
     */
    fun press(packageName: String, kind: Kind, returnTo: String?) {
        job?.cancel()
        job = scope.launch {
            val deadline = SystemClock.uptimeMillis() + WAIT_MS
            while (SystemClock.uptimeMillis() < deadline) {
                delay(POLL_MS)
                val root = rootOf(packageName) ?: continue
                val button = find(root, kind) ?: continue
                if (!click(button)) continue
                Log.i(TAG, "pressed $kind in $packageName")
                delay(SETTLE_MS)
                goBack(packageName, returnTo)
                return@launch
            }
            Log.i(TAG, "no $kind button found in $packageName; leaving it open")
        }
    }

    private fun rootOf(packageName: String): AccessibilityNodeInfo? =
        runCatching { service.windows.mapNotNull { it.root }.firstOrNull { it.packageName == packageName } }.getOrNull()
            ?: service.rootInActiveWindow?.takeIf { it.packageName == packageName }

    /** Breadth first, so the big main controls win over list rows deeper down. */
    private fun find(root: AccessibilityNodeInfo, kind: Kind): AccessibilityNodeInfo? {
        val words = if (kind == Kind.TOGGLE) TOGGLE_WORDS else STOP_WORDS
        val ids = if (kind == Kind.TOGGLE) TOGGLE_IDS else STOP_IDS
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var seen = 0
        while (queue.isNotEmpty() && seen++ < MAX_NODES) {
            val node = queue.removeFirst()
            val label = listOfNotNull(node.contentDescription, node.text).joinToString(" ")
            val id = node.viewIdResourceName.orEmpty().substringAfter('/')
            if (node.isVisibleToUser && (words.containsMatchIn(label) || ids.containsMatchIn(id))) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    /** The node itself, or the nearest clickable parent (icons often sit inside the button). */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var hops = 0
        while (target != null && !target.isClickable && hops++ < 4) target = target.parent
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun goBack(packageName: String, returnTo: String?) {
        val launch = returnTo?.takeIf { it != packageName }?.let { service.packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) {
            runCatching { service.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
                .onFailure { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    private companion object {
        const val TAG = "IslandControl"
        const val WAIT_MS = 3_000L
        const val POLL_MS = 150L
        const val SETTLE_MS = 300L
        const val MAX_NODES = 400
        val TOGGLE_WORDS = Regex("(?i)^\\s*(pause|resume|continue|play)\\b|\\b(pause|resume) recording\\b")
        val STOP_WORDS = Regex("(?i)^\\s*(stop|save|done|finish|complete|end)\\b|\\b(stop|save|end) recording\\b")
        val TOGGLE_IDS = Regex("(?i)pause|resume|continue|play_?btn|record_?btn|btn_?record|btn_?play")
        val STOP_IDS = Regex("(?i)stop|save|done|finish|complete|confirm|btn_?ok")
    }
}
