package dev.indla.island.service.overlay

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.indla.island.model.LiveContent
import dev.indla.island.model.SystemEvent
import dev.indla.island.ui.island.FACE

/**
 * Unlock glances. Every unlock (USER_PRESENT: fingerprint, face, PIN or pattern alike) shows the
 * same lock opening to a tick. A failed face or fingerprint attempt is read from the lock screen's
 * own spoken announcement ("Fingerprint not recognized", "Face not recognised, try again").
 */
internal class BiometricWatcher(private val emit: (LiveContent.System) -> Unit) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun onAnnouncement(event: AccessibilityEvent) {
        if (event.packageName?.toString() != SYSTEM_UI) return
        val said = event.text.joinToString(" ")
        val face = FACE_WORDS.containsMatchIn(said)
        val finger = FINGER_WORDS.containsMatchIn(said)
        if (!face && !finger) return
        Log.i(TAG, "lock screen said: $said")
        if (FAILED.containsMatchIn(said)) {
            emit(LiveContent.System(SystemEvent.AUTH_FAILED, if (face) FACE else 0, if (face) "Face not recognised" else "Fingerprint not recognised"))
        }
    }

    /**
     * USER_PRESENT arrives while the lock screen is still leaving and the window list is in flux;
     * a glance raised then can be dropped or covered. Show it once the screen has settled.
     */
    fun onUnlocked() {
        // One glance for every unlock (fingerprint, face, PIN, pattern): the lock opens to a tick.
        val glance = LiveContent.System(SystemEvent.UNLOCKED, null, "Welcome back")
        Log.i(TAG, "unlocked; showing the glance in ${SETTLE_MS}ms")
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ emit(glance) }, SETTLE_MS)
    }

    private companion object {
        const val TAG = "IslandBiometric"
        const val SYSTEM_UI = "com.android.systemui"
        const val SETTLE_MS = 600L
        val FACE_WORDS = Regex("""(?i)\bface\b""")
        val FINGER_WORDS = Regex("""(?i)fingerprint""")
        val FAILED = Regex("""(?i)not recogni[sz]ed|try again|no match|couldn.?t recogni|didn.?t recogni|failed""")
    }
}
