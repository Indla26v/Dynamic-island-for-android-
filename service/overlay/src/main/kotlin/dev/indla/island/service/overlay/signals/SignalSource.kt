package dev.indla.island.service.overlay.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.indla.island.model.LiveContent

/** Where a source reports a phone state change; the engine decides whether it shows. */
typealias Emit = (LiveContent.System) -> Unit

/**
 * One kind of phone state (the ringer, Wi-Fi, the torch...). Each source watches one thing
 * and only reports changes: adding a new glance means adding a source, nothing else changes.
 */
internal interface SignalSource {
    fun start()
    fun stop()
}

/** A source fed by system broadcasts; registration and the sticky replay are handled here. */
internal abstract class BroadcastSignal(protected val context: Context, protected val emit: Emit) : SignalSource {
    protected abstract val actions: List<String>

    /** @param initial the sticky replay on registration: current state, not a change. */
    protected abstract fun onBroadcast(intent: Intent, initial: Boolean)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBroadcast(intent, isInitialStickyBroadcast)
    }

    override fun start() {
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        // Exported: some of these come from system apps rather than the system itself (Bluetooth's
        // from the Bluetooth app), which a non-exported receiver never hears. They're all
        // protected broadcasts, so no other app can send them.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }
}

/**
 * A source fed by system settings (vivo's Monster mode, per-SIM mobile data). Observing costs
 * nothing until the value changes; bursts of changes settle before [onSettled] runs.
 */
internal abstract class SettingSignal(protected val context: Context, protected val emit: Emit) : SignalSource {
    protected val handler = Handler(Looper.getMainLooper())
    protected abstract val uris: List<Uri>
    protected open val settleMs = 0L

    /** Called with the settings settled; compare with the last value and emit on a change. */
    protected abstract fun onSettled()

    /** Read the starting value, so the first change can be told from the current state. */
    protected abstract fun prime()

    private val settled = Runnable { onSettled() }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(settled)
            handler.postDelayed(settled, settleMs)
        }
    }

    override fun start() {
        prime()
        uris.forEach { context.contentResolver.registerContentObserver(it, false, observer) }
    }

    override fun stop() {
        handler.removeCallbacks(settled)
        context.contentResolver.unregisterContentObserver(observer)
    }
}

/** On/off pairs read the same way everywhere. */
internal fun onOff(on: Boolean) = if (on) "On" else "Off"

@Suppress("DEPRECATION")
internal inline fun <reified T : android.os.Parcelable> Intent.parcelable(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, T::class.java) else getParcelableExtra(name)
