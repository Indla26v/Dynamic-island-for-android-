package dev.indla.island.service.overlay

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.indla.island.model.LiveContent
import dev.indla.island.model.SystemEvent
import dev.indla.island.ui.island.FACE

/**
 * Face and fingerprint unlock glances. Android tells no app how the phone was unlocked, so:
 * a failed attempt is read from the lock screen's own spoken announcement ("Fingerprint not
 * recognized", "Face not recognised, try again"), and an unlock (USER_PRESENT) borrows the face
 * or fingerprint symbol from what the lock screen last said, else from the sensors the phone has.
 */
internal class BiometricWatcher(context: Context, private val emit: (LiveContent.System) -> Unit) {
    private val hasFace = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
    private val hasFingerprint = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    private var lastFace: Boolean? = null
    private var lastSaidAt = 0L

    fun onAnnouncement(event: AccessibilityEvent) {
        if (event.packageName?.toString() != SYSTEM_UI) return
        val said = event.text.joinToString(" ")
        val face = FACE_WORDS.containsMatchIn(said)
        val finger = FINGER_WORDS.containsMatchIn(said)
        if (!face && !finger) return
        lastFace = face
        lastSaidAt = SystemClock.uptimeMillis()
        if (FAILED.containsMatchIn(said)) {
            emit(LiveContent.System(SystemEvent.AUTH_FAILED, if (face) FACE else 0, if (face) "Face not recognised" else "Fingerprint not recognised"))
        }
    }

    fun onUnlocked() {
        val recent = SystemClock.uptimeMillis() - lastSaidAt < RECENT_MS
        val face = if (recent) lastFace == true else hasFace && !hasFingerprint
        emit(LiveContent.System(SystemEvent.UNLOCKED, if (face) FACE else 0, if (face) "Face" else if (hasFingerprint || !hasFace) "Fingerprint" else "Unlocked"))
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val RECENT_MS = 15_000L
        val FACE_WORDS = Regex("""(?i)\bface\b""")
        val FINGER_WORDS = Regex("""(?i)fingerprint""")
        val FAILED = Regex("""(?i)not recogni[sz]ed|try again|no match|couldn.?t recogni|didn.?t recogni|failed""")
    }
}
