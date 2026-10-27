package dev.indla.island.service.overlay.signals

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.indla.island.model.AudioKind
import dev.indla.island.model.LiveContent
import dev.indla.island.model.SystemEvent

/** Wi-Fi connected / disconnected, from the state broadcast; needs no network permission. */
internal class WifiSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    private var connected: Boolean? = null

    override val actions = listOf(WifiManager.NETWORK_STATE_CHANGED_ACTION)

    @Suppress("DEPRECATION") // NetworkInfo is the broadcast's payload.
    override fun onBroadcast(intent: Intent, initial: Boolean) {
        val info = intent.parcelable<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO) ?: return
        val now = info.isConnected
        val before = connected
        connected = now
        // Wi-Fi passes through several states per change; only connected <-> not is news.
        if (initial || before == null || before == now) return
        emit(
            if (now) {
                LiveContent.System(SystemEvent.WIFI_CONNECTED, null, networkName() ?: "Connected")
            } else {
                LiveContent.System(SystemEvent.WIFI_DISCONNECTED, null, "Disconnected")
            },
        )
    }

    /** The network's name when Android shares it (it needs location access; Island has none). */
    @Suppress("DEPRECATION")
    private fun networkName(): String? = runCatching {
        context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
    }.getOrNull()?.trim('"')?.takeIf { it.isNotBlank() && !it.contains("unknown ssid", ignoreCase = true) }
}

/**
 * The Wi-Fi switch itself, from the sticky state broadcast every phone sends whichever screen
 * flipped it (quick settings, the Settings app, an assistant). [WifiSignal] only hears joining
 * and leaving a network, which a switch flipped with nothing in range never does.
 */
internal class WifiRadioSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    private var on: Boolean? = null

    override val actions = listOf(WifiManager.WIFI_STATE_CHANGED_ACTION)

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        // Enabling and disabling pass through on the way; only the settled states count.
        val now = when (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)) {
            WifiManager.WIFI_STATE_ENABLED -> true
            WifiManager.WIFI_STATE_DISABLED -> false
            else -> return
        }
        val before = on
        on = now
        if (initial || before == null || before == now) return
        emit(LiveContent.System(if (now) SystemEvent.WIFI_ON else SystemEvent.WIFI_OFF, null, onOff(now)))
    }
}

/**
 * The Bluetooth switch, from the adapter's state broadcast (it needs no runtime permission; the
 * install-time BLUETOOTH permission covers Android 11 and older). Settled states only: turning on
 * and turning off pass through first, so every on or off is a change.
 */
internal class BluetoothSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    override val actions = listOf(BluetoothAdapter.ACTION_STATE_CHANGED)

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        if (initial) return
        val on = when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
            BluetoothAdapter.STATE_ON -> true
            BluetoothAdapter.STATE_OFF -> false
            else -> return
        }
        emit(LiveContent.System(if (on) SystemEvent.BLUETOOTH_ON else SystemEvent.BLUETOOTH_OFF, null, onOff(on)))
    }
}

/**
 * Headphones connecting and disconnecting, from the audio device callback (no permission: the
 * audio device's product name says "Buds connected"). With Nearby devices allowed, a Bluetooth
 * pair also shows its own name and battery, which often arrives a moment later and updates the
 * card in place.
 */
internal class HeadphoneSignal(private val context: Context, private val emit: Emit) : SignalSource {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val known = HashSet<Int>()
    /** Bluetooth address -> the connected card, so a late battery report can update it. */
    private val recent = HashMap<String, LiveContent.System>()
    private var started = false

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            added.filter { it.isSink && it.type in HEADPHONES && known.add(it.id) && started }.forEach { emit(connected(it)) }
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            removed.filter { it.isSink && it.type in HEADPHONES && known.remove(it.id) }.forEach {
                recent.remove(it.address)
                val bt = bluetooth(it)
                val name = bt?.let(::btName) ?: name(it)
                emit(LiveContent.System(SystemEvent.HEADPHONES_DISCONNECTED, null, name, device = kindOf(it, bt, name)))
            }
        }
    }

    /** Delivered only once Nearby devices is allowed; harmless to listen for before that. */
    private val batteryReports = object : BroadcastSignal(context, emit) {
        override val actions = listOf(ACTION_BATTERY_LEVEL_CHANGED)

        override fun onBroadcast(intent: Intent, initial: Boolean) {
            if (initial) return
            val device = intent.parcelable<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it in 0..100 } ?: return
            val card = recent[device.address] ?: return
            if (card.level == level) return
            val updated = card.copy(level = level)
            recent[device.address] = updated
            emit(updated)
        }
    }

    override fun start() {
        // The callback reports devices already connected right away; remember them silently.
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in HEADPHONES }.forEach { known += it.id }
        audio.registerAudioDeviceCallback(devices, handler)
        batteryReports.start()
        started = true
    }

    override fun stop() {
        started = false
        audio.unregisterAudioDeviceCallback(devices)
        batteryReports.stop()
    }

    private fun connected(device: AudioDeviceInfo): LiveContent.System {
        val bt = bluetooth(device)
        val name = bt?.let(::btName) ?: name(device)
        val card = LiveContent.System(SystemEvent.HEADPHONES_CONNECTED, bt?.let(::btBattery), name, device = kindOf(device, bt, name))
        if (bt != null) recent[device.address] = card
        return card
    }

    /**
     * Earbuds, over-ear headphones or a speaker: the Bluetooth device class says it best, the
     * name settles what the class leaves open (a "headset" can be either), and wired ones are
     * headphones.
     */
    @SuppressLint("MissingPermission") // bluetoothClass is only read for a device bluetooth() returned
    private fun kindOf(device: AudioDeviceInfo, bt: BluetoothDevice?, name: String): AudioKind {
        val cls = bt?.let { runCatching { it.bluetoothClass?.deviceClass }.getOrNull() }
        val byClass = when (cls) {
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> AudioKind.SPEAKER
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> AudioKind.HEADPHONES
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> return AudioKind.OTHER
            else -> null
        }
        val byName = when {
            SPEAKER_NAMES.containsMatchIn(name) -> AudioKind.SPEAKER
            EARBUD_NAMES.containsMatchIn(name) -> AudioKind.EARBUDS
            HEADPHONE_NAMES.containsMatchIn(name) -> AudioKind.HEADPHONES
            else -> null
        }
        return byClass ?: byName ?: when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioKind.HEADPHONES
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioKind.EARBUDS
            else -> AudioKind.OTHER
        }
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

    private fun name(device: AudioDeviceInfo) =
        device.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: "Headphones"

    private companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        val SPEAKER_NAMES = Regex("""(?i)speaker|boom|flip|charge \d|soundlink|soundcore motion|party|xtreme|go \d|clip \d|soundbar|home ?pod|echo""")
        val EARBUD_NAMES = Regex("(?i)buds|pods|dots|tws|earbud|freebuds|nord|liberty|wf-|airpods")
        val HEADPHONE_NAMES = Regex("""(?i)headphone|wh-|qc ?\d|quietcomfort|max|studio|over|momentum|tune \d{3}""")
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

/**
 * The hotspot from the system's soft-AP broadcast (the state setting may be unreadable on some
 * skins); 13 is enabled, 11 disabled.
 */
internal class HotspotBroadcastSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    private var on: Boolean? = null

    override val actions = listOf(ACTION)

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        val now = when (intent.getIntExtra("wifi_state", -1)) {
            13 -> true
            11 -> false
            else -> return
        }
        val before = on
        on = now
        if (initial || before == null || before == now) return
        emit(LiveContent.System(if (now) SystemEvent.HOTSPOT_ON else SystemEvent.HOTSPOT_OFF, null, onOff(now)))
    }

    private companion object {
        const val ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    }
}
