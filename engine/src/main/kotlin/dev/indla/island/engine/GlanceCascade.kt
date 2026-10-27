package dev.indla.island.engine

import dev.indla.island.model.SystemEvent
import dev.indla.island.model.SystemEvent.AIRPLANE_OFF
import dev.indla.island.model.SystemEvent.AIRPLANE_ON
import dev.indla.island.model.SystemEvent.BEDTIME_OFF
import dev.indla.island.model.SystemEvent.BEDTIME_ON
import dev.indla.island.model.SystemEvent.BLUETOOTH_OFF
import dev.indla.island.model.SystemEvent.BLUETOOTH_ON
import dev.indla.island.model.SystemEvent.CHARGING
import dev.indla.island.model.SystemEvent.DARK_MODE
import dev.indla.island.model.SystemEvent.DATA_OFF
import dev.indla.island.model.SystemEvent.DATA_ON
import dev.indla.island.model.SystemEvent.DND_OFF
import dev.indla.island.model.SystemEvent.DND_ON
import dev.indla.island.model.SystemEvent.EYE_OFF
import dev.indla.island.model.SystemEvent.EYE_ON
import dev.indla.island.model.SystemEvent.HEADPHONES_CONNECTED
import dev.indla.island.model.SystemEvent.HEADPHONES_DISCONNECTED
import dev.indla.island.model.SystemEvent.HOTSPOT_OFF
import dev.indla.island.model.SystemEvent.HOTSPOT_ON
import dev.indla.island.model.SystemEvent.LIGHT_MODE
import dev.indla.island.model.SystemEvent.LINK_OFF
import dev.indla.island.model.SystemEvent.LINK_ON
import dev.indla.island.model.SystemEvent.MONSTER_OFF
import dev.indla.island.model.SystemEvent.MONSTER_ON
import dev.indla.island.model.SystemEvent.RINGER_NORMAL
import dev.indla.island.model.SystemEvent.RINGER_SILENT
import dev.indla.island.model.SystemEvent.RINGER_VIBRATE
import dev.indla.island.model.SystemEvent.SAVER_OFF
import dev.indla.island.model.SystemEvent.SAVER_ON
import dev.indla.island.model.SystemEvent.WIFI_CONNECTED
import dev.indla.island.model.SystemEvent.WIFI_DISCONNECTED
import dev.indla.island.model.SystemEvent.WIFI_OFF
import dev.indla.island.model.SystemEvent.WIFI_ON

/**
 * One switch often flips others: bedtime turns on Do Not Disturb, silences the ringer and warms
 * the screen; airplane mode drops Wi-Fi and data. The island shows only the cause. Whichever
 * order the events arrive in, a consequence near its cause is swallowed (or replaced, if it came
 * first).
 */
internal object GlanceCascade {
    /** How close together a cause and its consequences arrive (a schedule can lag a little). */
    const val WINDOW_MS = 4_000L

    private val consequences: Map<SystemEvent, Set<SystemEvent>> = mapOf(
        BEDTIME_ON to setOf(DND_ON, RINGER_SILENT, RINGER_VIBRATE, EYE_ON, DARK_MODE, LIGHT_MODE),
        BEDTIME_OFF to setOf(DND_OFF, RINGER_NORMAL, RINGER_VIBRATE, EYE_OFF, LIGHT_MODE, DARK_MODE),
        AIRPLANE_ON to setOf(WIFI_OFF, WIFI_DISCONNECTED, BLUETOOTH_OFF, DATA_OFF, HEADPHONES_DISCONNECTED, HOTSPOT_OFF, LINK_OFF),
        AIRPLANE_OFF to setOf(WIFI_ON, WIFI_CONNECTED, BLUETOOTH_ON, DATA_ON, HEADPHONES_CONNECTED, LINK_ON),
        SAVER_ON to setOf(DARK_MODE, MONSTER_OFF),
        SAVER_OFF to setOf(LIGHT_MODE),
        DND_ON to setOf(RINGER_SILENT, RINGER_VIBRATE),
        DND_OFF to setOf(RINGER_NORMAL, RINGER_VIBRATE),
        CHARGING to setOf(SAVER_OFF),
        HOTSPOT_ON to setOf(WIFI_OFF, WIFI_DISCONNECTED),
        HOTSPOT_OFF to setOf(WIFI_ON, WIFI_CONNECTED),
        // Turning Wi-Fi off drops its network; "Off" says it all. Turning it on is no cause: the
        // network joining a moment later replaces "On" with its name.
        WIFI_OFF to setOf(WIFI_DISCONNECTED),
        BLUETOOTH_OFF to setOf(HEADPHONES_DISCONNECTED),
        MONSTER_ON to setOf(SAVER_OFF),
    )

    /** Whether [effect] is something [cause] brings with it. */
    fun causes(cause: SystemEvent, effect: SystemEvent): Boolean = consequences[cause]?.contains(effect) == true

    /**
     * Whether [effect] is a consequence of anything in [recent], directly or through a chain
     * (bedtime -> Do Not Disturb -> silent).
     */
    fun explainedBy(effect: SystemEvent, recent: Collection<SystemEvent>): Boolean =
        recent.any { cause -> causes(cause, effect) || consequences[cause].orEmpty().any { causes(it, effect) } }
}
