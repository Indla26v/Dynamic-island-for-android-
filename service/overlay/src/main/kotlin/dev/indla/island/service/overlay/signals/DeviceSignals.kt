package dev.indla.island.service.overlay.signals

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import dev.indla.island.model.LiveContent
import dev.indla.island.model.SystemEvent

/** Plugged in, unplugged, battery low, and the battery saver. */
internal class PowerSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    /** Low-battery warnings already given since the phone was last charging. */
    private val warned = HashSet<Int>()
    private var lastLevel: Int? = null

    // Not ACTION_BATTERY_LOW: some skins (vivo) repeat it at every percent below their threshold.
    // The level itself says when 30% and 10% are crossed, once each.
    override val actions = listOf(
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED,
        Intent.ACTION_BATTERY_CHANGED,
        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
    )

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            onLevel(intent, initial)
            return
        }
        if (initial) return
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> emit(LiveContent.System(SystemEvent.CHARGING, level, "$level%"))
            Intent.ACTION_POWER_DISCONNECTED -> emit(LiveContent.System(SystemEvent.UNPLUGGED, level, "$level%"))
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                val on = power?.isPowerSaveMode == true
                emit(LiveContent.System(if (on) SystemEvent.SAVER_ON else SystemEvent.SAVER_OFF, level, if (on) "On · $level%" else "Off"))
            }
        }
    }

    /** Warns once on the way down through 30% (orange) and once through 10% (red). */
    private fun onLevel(intent: Intent, initial: Boolean) {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (raw < 0) return
        val level = (raw * 100 / scale).coerceIn(0, 100)
        val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val before = lastLevel
        lastLevel = level
        if (charging) {
            warned.clear()
            return
        }
        // At start, thresholds already passed count as given: no warning for where it already is.
        if (initial || before == null) {
            warned += LOW_LEVELS.filter { level <= it }
            return
        }
        val crossed = LOW_LEVELS.filter { level <= it && it !in warned }
        if (crossed.isEmpty() || level >= before) return
        warned += crossed
        emit(LiveContent.System(SystemEvent.BATTERY_LOW, level, "$level%"))
    }

    private companion object {
        val LOW_LEVELS = listOf(30, 10)
    }
}

/** Ring, vibrate, silent. */
internal class RingerSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    override val actions = listOf(AudioManager.RINGER_MODE_CHANGED_ACTION)

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        if (initial) return
        when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
            AudioManager.RINGER_MODE_NORMAL -> emit(LiveContent.System(SystemEvent.RINGER_NORMAL, null, "Sound on"))
            AudioManager.RINGER_MODE_VIBRATE -> emit(LiveContent.System(SystemEvent.RINGER_VIBRATE, null, "Vibrate only"))
            AudioManager.RINGER_MODE_SILENT -> emit(LiveContent.System(SystemEvent.RINGER_SILENT, null, "Sounds off"))
        }
    }
}

internal class AirplaneSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    override val actions = listOf(Intent.ACTION_AIRPLANE_MODE_CHANGED)

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        if (initial) return
        val on = intent.getBooleanExtra("state", false)
        emit(LiveContent.System(if (on) SystemEvent.AIRPLANE_ON else SystemEvent.AIRPLANE_OFF, null, onOff(on)))
    }
}

internal class LocationSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    private val location = context.getSystemService(LocationManager::class.java)
    private var on: Boolean? = null

    override val actions = listOf(LocationManager.MODE_CHANGED_ACTION)

    override fun start() {
        on = location?.isLocationEnabled
        super.start()
    }

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        if (initial) return
        val now = location?.isLocationEnabled == true
        if (now == on) return
        on = now
        emit(LiveContent.System(if (now) SystemEvent.LOCATION_ON else SystemEvent.LOCATION_OFF, null, onOff(now)))
    }
}

/** Do Not Disturb, with which level: priority only, alarms only, total silence. */
internal class DndSignal(context: Context, emit: Emit) : BroadcastSignal(context, emit) {
    override val actions = listOf(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        if (initial) return
        val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: return
        val label = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority only"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "Total silence"
            else -> "Off"
        }
        val on = filter != NotificationManager.INTERRUPTION_FILTER_ALL
        emit(LiveContent.System(if (on) SystemEvent.DND_ON else SystemEvent.DND_OFF, null, label))
    }
}

/** The torch: its callback reports every camera's state on registration; only changes count. */
internal class TorchSignal(context: Context, private val emit: Emit) : SignalSource {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val torches = HashMap<String, Boolean>()

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            val before = torches.put(cameraId, enabled)
            if (before == null || before == enabled) return
            emit(LiveContent.System(if (enabled) SystemEvent.FLASHLIGHT_ON else SystemEvent.FLASHLIGHT_OFF, null, onOff(enabled)))
        }
    }

    override fun start() {
        runCatching { camera?.registerTorchCallback(callback, handler) }
    }

    override fun stop() {
        runCatching { camera?.unregisterTorchCallback(callback) }
    }
}

/**
 * A phone switch that lives in a system setting (vivo's Monster mode, the hotspot, Eye
 * Protection). One class for all of them: which setting, which value means on, and which glance
 * on and off.
 */
internal class ToggleSettingSignal(
    context: Context,
    emit: Emit,
    private val name: String,
    private val table: Table,
    private val onEvent: SystemEvent,
    private val offEvent: SystemEvent,
    private val isOn: (Int) -> Boolean = { it != 0 },
) : SettingSignal(context, emit) {
    enum class Table { SYSTEM, GLOBAL, SECURE }

    private var state: Boolean? = null

    override val uris = listOf(
        when (table) {
            Table.SYSTEM -> Settings.System.getUriFor(name)
            Table.GLOBAL -> Settings.Global.getUriFor(name)
            Table.SECURE -> Settings.Secure.getUriFor(name)
        },
    )

    override fun prime() {
        state = read()
        // Says once whether Android lets Island read this switch at all.
        android.util.Log.i(TAG, "$name (${table.name.lowercase()}) = ${state ?: "unreadable"}")
    }

    override fun onSettled() {
        val now = read() ?: return
        val before = state
        state = now
        if (before == now) return
        emit(LiveContent.System(if (now) onEvent else offEvent, null, onOff(now)))
    }

    private fun read(): Boolean? = runCatching {
        val resolver = context.contentResolver
        when (table) {
            Table.SYSTEM -> Settings.System.getInt(resolver, name)
            Table.GLOBAL -> Settings.Global.getInt(resolver, name)
            Table.SECURE -> Settings.Secure.getInt(resolver, name)
        }.let(isOn)
    }.getOrNull()

    companion object {
        private const val TAG = "IslandSignals"

        // vivo keeps its power mode in one number: 5 is Monster, -1 normal, others the savers.
        // (pemMonsterState only says the phone supports Monster; it never changes.)
        fun monster(context: Context, emit: Emit) =
            ToggleSettingSignal(context, emit, "power_save_type", Table.SYSTEM, SystemEvent.MONSTER_ON, SystemEvent.MONSTER_OFF) { it == MONSTER }

        private const val MONSTER = 5

        fun hotspot(context: Context, emit: Emit) =
            ToggleSettingSignal(context, emit, "soft_ap_on", Table.GLOBAL, SystemEvent.HOTSPOT_ON, SystemEvent.HOTSPOT_OFF)

        /**
         * Every skin names its blue light filter differently; a phone has one of these and the
         * others stay unreadable and silent.
         */
        fun eyeProtection(context: Context, emit: Emit) = EYE_SETTINGS.map { (name, table) ->
            ToggleSettingSignal(context, emit, name, table, SystemEvent.EYE_ON, SystemEvent.EYE_OFF)
        }

        private val EYE_SETTINGS = listOf(
            // Android's Night Light: Pixel, vivo, OPPO, realme, Motorola, Nothing.
            "night_display_activated" to Table.SECURE,
            // Samsung One UI's Eye comfort shield.
            "blue_light_filter" to Table.SYSTEM,
            // Xiaomi, Redmi and POCO's Reading mode.
            "screen_paper_mode_enabled" to Table.SYSTEM,
            // OnePlus's Eye comfort.
            "oem_eyecare_enable" to Table.SYSTEM,
            // Huawei and Honor's Eye comfort.
            "eyes_protection_mode" to Table.SYSTEM,
        )

        /**
         * The Bluetooth switch as Android saves it (0 off, 1 on, 2 on but held off by airplane
         * mode): readable without any permission, in case a skin holds the state broadcast back.
         * It agrees with [BluetoothSignal], so the two make one glance.
         */
        fun bluetooth(context: Context, emit: Emit) =
            ToggleSettingSignal(context, emit, "bluetooth_on", Table.GLOBAL, SystemEvent.BLUETOOTH_ON, SystemEvent.BLUETOOTH_OFF)
    }
}

/**
 * Mobile data: per-SIM global settings (mobile_data1, 2...); a toggle flips the data SIM's one,
 * sometimes several at once, so changes settle briefly first. Needs no permission.
 */
internal class MobileDataSignal(context: Context, emit: Emit) : SettingSignal(context, emit) {
    private var on: Boolean? = null

    override val uris = KEYS.map { Settings.Global.getUriFor(it) }
    override val settleMs = 400L

    override fun prime() {
        on = read()
    }

    override fun onSettled() {
        val now = read() ?: return
        val before = on
        on = now
        if (before == null || before == now) return
        emit(LiveContent.System(if (now) SystemEvent.DATA_ON else SystemEvent.DATA_OFF, null, onOff(now)))
    }

    /** On when the data SIM's switch is on; unreadable settings leave the glance off. */
    private fun read(): Boolean? {
        val values = KEYS.mapNotNull { key -> runCatching { Settings.Global.getInt(context.contentResolver, key) }.getOrNull() }
        if (values.isEmpty()) return null
        val sub = runCatching { android.telephony.SubscriptionManager.getDefaultDataSubscriptionId() }.getOrDefault(-1)
        val own = runCatching { Settings.Global.getInt(context.contentResolver, "mobile_data$sub") }.getOrNull()
        return (own ?: values.first()) != 0
    }

    private companion object {
        val KEYS = listOf("mobile_data", "mobile_data1", "mobile_data2", "mobile_data3", "mobile_data4")
    }
}
