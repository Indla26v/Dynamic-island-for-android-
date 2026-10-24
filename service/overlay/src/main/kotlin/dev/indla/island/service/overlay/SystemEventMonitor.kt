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
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
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

    private val power = context.getSystemService(PowerManager::class.java)
    private val camera = context.getSystemService(CameraManager::class.java)
    private var wifiConnected: Boolean? = null
    private val torches = HashMap<String, Boolean>()
    private var monster: Int? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                onWifi(intent, initial = isInitialStickyBroadcast)
                return
            }
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
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val on = intent.getBooleanExtra("state", false)
                    emit(LiveContent.System(if (on) SystemEvent.AIRPLANE_ON else SystemEvent.AIRPLANE_OFF, null, if (on) "On" else "Off"))
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val on = power?.isPowerSaveMode == true
                    emit(LiveContent.System(if (on) SystemEvent.SAVER_ON else SystemEvent.SAVER_OFF, level, if (on) "On · $level%" else "Off"))
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                    AudioManager.RINGER_MODE_NORMAL -> emit(LiveContent.System(SystemEvent.RINGER_NORMAL, null, "Sound on"))
                    AudioManager.RINGER_MODE_VIBRATE -> emit(LiveContent.System(SystemEvent.RINGER_VIBRATE, null, "Vibrate only"))
                    AudioManager.RINGER_MODE_SILENT -> emit(LiveContent.System(SystemEvent.RINGER_SILENT, null, "Sounds off"))
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
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        // Delivered only once Nearby devices is allowed; harmless to ask for before that.
        filter.addAction(ACTION_BATTERY_LEVEL_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // The callback reports devices already connected right away; remember them silently.
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in HEADPHONES }.forEach { knownDevices += it.id }
        audio.registerAudioDeviceCallback(devices, handler)
        runCatching { camera?.registerTorchCallback(torch, handler) }
        monster = readMonster()
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(MONSTER_KEY), false, monsterObserver)
        started = true
    }

    fun stop() {
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        audio.unregisterAudioDeviceCallback(devices)
        runCatching { camera?.unregisterTorchCallback(torch) }
        context.contentResolver.unregisterContentObserver(monsterObserver)
    }

    /** Fires once per camera on registration with the current state; only changes are events. */
    private val torch = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            val before = torches.put(cameraId, enabled)
            if (before == null || before == enabled) return
            emit(LiveContent.System(if (enabled) SystemEvent.FLASHLIGHT_ON else SystemEvent.FLASHLIGHT_OFF, null, if (enabled) "On" else "Off"))
        }
    }

    /** vivo keeps Monster mode in a system setting; watching it costs nothing until it changes. */
    private val monsterObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val now = readMonster() ?: return
            val before = monster
            monster = now
            if (before == null || before == now) return
            val on = now != 0
            emit(LiveContent.System(if (on) SystemEvent.MONSTER_ON else SystemEvent.MONSTER_OFF, null, if (on) "On" else "Off"))
        }
    }

    private fun readMonster(): Int? = runCatching { Settings.System.getInt(context.contentResolver, MONSTER_KEY) }.getOrNull()

    @Suppress("DEPRECATION") // NetworkInfo is the broadcast's payload; it needs no network permission.
    private fun onWifi(intent: Intent, initial: Boolean) {
        val info = intent.parcelable<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO) ?: return
        val connected = info.isConnected
        val before = wifiConnected
        wifiConnected = connected
        // Wi-Fi passes through several states per change; only connected <-> not is news.
        if (initial || before == null || before == connected) return
        emit(
            if (connected) {
                LiveContent.System(SystemEvent.WIFI_CONNECTED, null, wifiName() ?: "Connected")
            } else {
                LiveContent.System(SystemEvent.WIFI_DISCONNECTED, null, "Disconnected")
            },
        )
    }

    /** The network's name when Android shares it (it needs location access; Island has none). */
    @Suppress("DEPRECATION")
    private fun wifiName(): String? = runCatching {
        context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
    }.getOrNull()?.trim('"')?.takeIf { it.isNotBlank() && !it.contains("unknown ssid", ignoreCase = true) }

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
        const val MONSTER_KEY = "pemMonsterState"
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
