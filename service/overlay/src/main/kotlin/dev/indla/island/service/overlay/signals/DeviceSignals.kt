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

    override val actions = listOf(
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED,
        Intent.ACTION_BATTERY_LOW,
        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
    )

    override fun onBroadcast(intent: Intent, initial: Boolean) {
        if (initial) return
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> emit(LiveContent.System(SystemEvent.CHARGING, level, "$level%"))
            Intent.ACTION_POWER_DISCONNECTED -> emit(LiveContent.System(SystemEvent.UNPLUGGED, level, "$level%"))
            Intent.ACTION_BATTERY_LOW -> emit(LiveContent.System(SystemEvent.BATTERY_LOW, level, "$level%"))
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                val on = power?.isPowerSaveMode == true
                emit(LiveContent.System(if (on) SystemEvent.SAVER_ON else SystemEvent.SAVER_OFF, level, if (on) "On · $level%" else "Off"))
            }
        }
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

/** vivo / iQOO Monster mode, kept in a vendor system setting. */
internal class MonsterSignal(context: Context, emit: Emit) : SettingSignal(context, emit) {
    private var state: Int? = null

    override val uris = listOf(Settings.System.getUriFor(KEY))

    override fun prime() {
        state = read()
    }

    override fun onSettled() {
        val now = read() ?: return
        val before = state
        state = now
        if (before == null || before == now) return
        emit(LiveContent.System(if (now != 0) SystemEvent.MONSTER_ON else SystemEvent.MONSTER_OFF, null, onOff(now != 0)))
    }

    private fun read(): Int? = runCatching { Settings.System.getInt(context.contentResolver, KEY) }.getOrNull()

    private companion object {
        const val KEY = "pemMonsterState"
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
