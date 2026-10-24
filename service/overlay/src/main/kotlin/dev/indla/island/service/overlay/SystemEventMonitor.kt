package dev.indla.island.service.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.indla.island.model.LiveContent
import dev.indla.island.model.SystemEvent

/**
 * Charging, battery low, ringer mode and headphones, as brief island glances. Broadcasts and an
 * audio device callback only, no polling. Headphones need no permission (the audio device's
 * product name is enough to say "Buds connected"); with the optional Nearby devices permission
 * a Bluetooth pair also shows its own name and battery, which often arrives a moment after the
 * connection and updates the card in place.
 */
internal class SystemEventMonitor(
    private val context: Context,
    private val emit: (LiveContent.System) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val knownDevices = HashSet<Int>()
    /** Bluetooth address -> the connected card, so a late battery report can update it. */
    private val recent = HashMap<String, LiveContent.System>()
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Sticky broadcasts replay on registration; that isn't an event.
            if (isInitialStickyBroadcast) return
            if (intent.action == ACTION_BATTERY_LEVEL_CHANGED) {
                onHeadsetBattery(intent)
                return
            }
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
                .forEach { emit(connected(it)) }
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            removed.filter { it.isSink && it.type in HEADPHONES && knownDevices.remove(it.id) }
                .forEach {
                    recent.remove(it.address)
                    emit(LiveContent.System(SystemEvent.HEADPHONES_DISCONNECTED, null, bluetooth(it)?.let(::btName) ?: name(it)))
                }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        // Delivered only once Nearby devices is allowed; harmless to ask for before that.
        filter.addAction(ACTION_BATTERY_LEVEL_CHANGED)
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

    private fun connected(device: AudioDeviceInfo): LiveContent.System {
        val bt = bluetooth(device)
        val battery = bt?.let(::btBattery)
        val card = LiveContent.System(
            SystemEvent.HEADPHONES_CONNECTED,
            battery,
            bt?.let(::btName) ?: name(device),
            earbuds = looksLikeEarbuds(bt?.let(::btName) ?: name(device)),
        )
        if (bt != null) recent[device.address] = card
        return card
    }

    /** A battery report for a pair that just connected: same card, now with its level. */
    private fun onHeadsetBattery(intent: Intent) {
        val device = intent.parcelable<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it in 0..100 } ?: return
        val card = recent[device.address] ?: return
        if (card.level == level) return
        val updated = card.copy(level = level)
        recent[device.address] = updated
        emit(updated)
    }

    private fun canUseBluetooth() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun bluetooth(device: AudioDeviceInfo): BluetoothDevice? {
        if (device.type !in BLUETOOTH || !canUseBluetooth()) return null
        val address = device.address.takeIf { BluetoothAdapter.checkBluetoothAddress(it) } ?: return null
        return runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address) }.getOrNull()
    }

    @SuppressLint("MissingPermission") // canUseBluetooth() checked by bluetooth()
    private fun btName(device: BluetoothDevice): String? = runCatching {
        val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null
        (alias ?: device.name)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Not public API, but present on every Android since 8.1 and what Settings itself shows. */
    private fun btBattery(device: BluetoothDevice): Int? = runCatching {
        BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int
    }.getOrNull()?.takeIf { it in 0..100 }

    private fun looksLikeEarbuds(name: String) = EARBUDS.containsMatchIn(name)

    private fun name(device: AudioDeviceInfo) =
        device.productName?.toString()?.takeIf { it.isNotBlank() && it != android.os.Build.MODEL } ?: "Headphones"

    private companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        val EARBUDS = Regex("(?i)buds|pods|dots|tws|earbud|freebuds|nord|liberty")
        val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET)
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

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.parcelable(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, T::class.java) else getParcelableExtra(name)
