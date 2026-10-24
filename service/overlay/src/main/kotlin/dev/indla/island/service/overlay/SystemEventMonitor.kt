package dev.indla.island.service.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.indla.island.model.LiveContent
import dev.indla.island.model.SystemEvent

/**
 * Charging, battery low, ringer mode and headphones, as brief island glances. Broadcasts and an
 * audio device callback only: no polling, and no Bluetooth permission (the audio device's
 * product name is enough to say "Buds connected").
 */
internal class SystemEventMonitor(
    private val context: Context,
    private val emit: (LiveContent.System) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val knownDevices = HashSet<Int>()
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Sticky broadcasts replay on registration; that isn't an event.
            if (isInitialStickyBroadcast) return
            val level = level()
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> emit(LiveContent.System(SystemEvent.CHARGING, level, "$level%"))
                Intent.ACTION_POWER_DISCONNECTED -> emit(LiveContent.System(SystemEvent.UNPLUGGED, level, "$level%"))
                Intent.ACTION_BATTERY_LOW -> emit(LiveContent.System(SystemEvent.BATTERY_LOW, level, "$level%"))
                AudioManager.RINGER_MODE_CHANGED_ACTION -> when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                    AudioManager.RINGER_MODE_NORMAL -> emit(LiveContent.System(SystemEvent.RINGER_NORMAL, null, "Ring"))
                    AudioManager.RINGER_MODE_VIBRATE -> emit(LiveContent.System(SystemEvent.RINGER_VIBRATE, null, "Vibrate"))
                    AudioManager.RINGER_MODE_SILENT -> emit(LiveContent.System(SystemEvent.RINGER_SILENT, null, "Silent"))
                }
            }
        }
    }

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            added.filter { it.isSink && it.type in HEADPHONES && knownDevices.add(it.id) && started }
                .forEach { emit(LiveContent.System(SystemEvent.HEADPHONES_CONNECTED, null, name(it))) }
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            removed.filter { it.isSink && it.type in HEADPHONES && knownDevices.remove(it.id) }
                .forEach { emit(LiveContent.System(SystemEvent.HEADPHONES_DISCONNECTED, null, name(it))) }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // The callback reports devices already connected right away; remember them silently.
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in HEADPHONES }.forEach { knownDevices += it.id }
        audio.registerAudioDeviceCallback(devices, handler)
        started = true
    }

    fun stop() {
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        audio.unregisterAudioDeviceCallback(devices)
    }

    private fun level() = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

    private fun name(device: AudioDeviceInfo) =
        device.productName?.toString()?.takeIf { it.isNotBlank() && it != android.os.Build.MODEL } ?: "Headphones"

    private companion object {
        val HEADPHONES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
