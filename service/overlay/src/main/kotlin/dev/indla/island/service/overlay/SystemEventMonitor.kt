package dev.indla.island.service.overlay

import android.content.Context
import android.util.Log
import dev.indla.island.model.LiveContent
import dev.indla.island.service.overlay.signals.AirplaneSignal
import dev.indla.island.service.overlay.signals.BluetoothSignal
import dev.indla.island.service.overlay.signals.DndSignal
import dev.indla.island.service.overlay.signals.HeadphoneSignal
import dev.indla.island.service.overlay.signals.HotspotBroadcastSignal
import dev.indla.island.service.overlay.signals.LocationSignal
import dev.indla.island.service.overlay.signals.MobileDataSignal
import dev.indla.island.service.overlay.signals.PowerSignal
import dev.indla.island.service.overlay.signals.RingerSignal
import dev.indla.island.service.overlay.signals.SignalSource
import dev.indla.island.service.overlay.signals.ToggleSettingSignal
import dev.indla.island.service.overlay.signals.TorchSignal
import dev.indla.island.service.overlay.signals.WifiRadioSignal
import dev.indla.island.service.overlay.signals.WifiSignal

/**
 * Every phone state Island turns into a glance, as one switch. Each source watches one thing
 * with broadcasts, callbacks or setting observers, no polling; a new glance is a new source in
 * this list. Theme, clipboard and unlock come from the accessibility service itself.
 *
 * Sources watch the state, never the button: a switch flipped in quick settings, deep in the
 * Settings app, by an assistant or by a schedule raises the same glance, on any phone.
 */
internal class SystemEventMonitor(
    context: Context,
    sink: (LiveContent.System) -> Unit,
) {
    /** Each change once in logcat (tag IslandSignals): what a phone that misses one reports. */
    private val emit: (LiveContent.System) -> Unit = {
        Log.i(TAG, it.event.name)
        sink(it)
    }

    private val sources: List<SignalSource> = listOf(
        PowerSignal(context, emit),
        RingerSignal(context, emit),
        HeadphoneSignal(context, emit),
        AirplaneSignal(context, emit),
        WifiSignal(context, emit),
        WifiRadioSignal(context, emit),
        BluetoothSignal(context, emit),
        ToggleSettingSignal.bluetooth(context, emit),
        MobileDataSignal(context, emit),
        LocationSignal(context, emit),
        DndSignal(context, emit),
        TorchSignal(context, emit),
        ToggleSettingSignal.monster(context, emit),
        HotspotBroadcastSignal(context, emit),
        ToggleSettingSignal.hotspot(context, emit),
    ) + ToggleSettingSignal.eyeProtection(context, emit)

    fun start() = sources.forEach { runCatching { it.start() } }

    fun stop() = sources.forEach { runCatching { it.stop() } }

    private companion object {
        const val TAG = "IslandSignals"
    }
}
