package dev.indla.island.service.overlay

import android.content.Context
import dev.indla.island.model.LiveContent
import dev.indla.island.service.overlay.signals.AirplaneSignal
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
import dev.indla.island.service.overlay.signals.WifiSignal

/**
 * Every phone state Island turns into a glance, as one switch. Each source watches one thing
 * with broadcasts, callbacks or setting observers, no polling; a new glance is a new source in
 * this list. Theme, clipboard and unlock come from the accessibility service itself.
 */
internal class SystemEventMonitor(
    context: Context,
    emit: (LiveContent.System) -> Unit,
) {
    private val sources: List<SignalSource> = listOf(
        PowerSignal(context, emit),
        RingerSignal(context, emit),
        HeadphoneSignal(context, emit),
        AirplaneSignal(context, emit),
        WifiSignal(context, emit),
        MobileDataSignal(context, emit),
        LocationSignal(context, emit),
        DndSignal(context, emit),
        TorchSignal(context, emit),
        ToggleSettingSignal.monster(context, emit),
        ToggleSettingSignal.eyeProtection(context, emit),
        HotspotBroadcastSignal(context, emit),
        ToggleSettingSignal.hotspot(context, emit),
    )

    fun start() = sources.forEach { runCatching { it.start() } }

    fun stop() = sources.forEach { runCatching { it.stop() } }
}
