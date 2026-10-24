package dev.indla.island.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.indla.island.model.GestureAction
import dev.indla.island.model.GestureMap
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.LiveSettings
import dev.indla.island.model.StylePreset
import dev.indla.island.model.WhenToShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<IslandSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    suspend fun updateAppearance(transform: (IslandAppearance) -> IslandAppearance) =
        update { it.copy(appearance = transform(it.appearance)) }

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val preset = stringPreferencesKey("preset")
        val accent = longPreferencesKey("accent")
        val sizeScale = floatPreferencesKey("size_scale")
        val offsetX = floatPreferencesKey("offset_x_dp")
        val offsetY = floatPreferencesKey("offset_y_dp")
        val restPill = booleanPreferencesKey("rest_pill")
        val glass = floatPreferencesKey("glass_intensity")
        val hideForeground = booleanPreferencesKey("hide_foreground")
        val hideFullscreen = booleanPreferencesKey("hide_fullscreen")
        val hideLandscape = booleanPreferencesKey("hide_landscape")
        val showLockscreen = booleanPreferencesKey("show_lockscreen")
        val hideShade = booleanPreferencesKey("hide_shade")
        val autoHide = intPreferencesKey("auto_hide_seconds")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val disclosure = booleanPreferencesKey("a11y_disclosure_accepted")
        val autoColour = booleanPreferencesKey("auto_colour")
        val glassCard = booleanPreferencesKey("glass_card")
        fun live(name: String) = booleanPreferencesKey("live_$name")
        fun gesture(slot: String) = stringPreferencesKey("gesture_$slot")
    }

    private fun Preferences.toSettings(): IslandSettings {
        val d = IslandSettings()
        val a = d.appearance
        val w = d.whenToShow
        val g = d.gestures
        fun gesture(slot: String, default: GestureAction) =
            this[Keys.gesture(slot)]?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: default
        return IslandSettings(
            enabled = this[Keys.enabled] ?: d.enabled,
            appearance = IslandAppearance(
                preset = this[Keys.preset]?.let { runCatching { StylePreset.valueOf(it) }.getOrNull() } ?: a.preset,
                accentArgb = this[Keys.accent] ?: a.accentArgb,
                sizeScale = this[Keys.sizeScale] ?: a.sizeScale,
                offsetXDp = this[Keys.offsetX] ?: a.offsetXDp,
                offsetYDp = this[Keys.offsetY] ?: a.offsetYDp,
                showRestPill = this[Keys.restPill] ?: a.showRestPill,
                glassIntensity = this[Keys.glass] ?: a.glassIntensity,
                autoColour = this[Keys.autoColour] ?: a.autoColour,
                glassCard = this[Keys.glassCard] ?: a.glassCard,
            ),
            whenToShow = WhenToShow(
                hideInForegroundApp = this[Keys.hideForeground] ?: w.hideInForegroundApp,
                hideInFullscreen = this[Keys.hideFullscreen] ?: w.hideInFullscreen,
                hideInLandscape = this[Keys.hideLandscape] ?: w.hideInLandscape,
                showOnLockscreen = this[Keys.showLockscreen] ?: w.showOnLockscreen,
                hideWhenShadeOpen = this[Keys.hideShade] ?: w.hideWhenShadeOpen,
            ),
            gestures = GestureMap(
                compactTap = gesture("compact_tap", g.compactTap),
                compactSwipeDown = gesture("compact_down", g.compactSwipeDown),
                compactSwipeUp = gesture("compact_up", g.compactSwipeUp),
                compactSwipeSide = gesture("compact_side", g.compactSwipeSide),
                compactLongPress = gesture("compact_long", g.compactLongPress),
                expandedTap = gesture("expanded_tap", g.expandedTap),
                expandedSwipeDown = gesture("expanded_down", g.expandedSwipeDown),
                expandedSwipeUp = gesture("expanded_up", g.expandedSwipeUp),
                expandedSwipeSide = gesture("expanded_side", g.expandedSwipeSide),
                expandedLongPress = gesture("expanded_long", g.expandedLongPress),
            ),
            autoHideSeconds = this[Keys.autoHide] ?: d.autoHideSeconds,
            live = LiveSettings(
                media = this[Keys.live("media")] ?: d.live.media,
                calls = this[Keys.live("calls")] ?: d.live.calls,
                progress = this[Keys.live("progress")] ?: d.live.progress,
                timers = this[Keys.live("timers")] ?: d.live.timers,
                charging = this[Keys.live("charging")] ?: d.live.charging,
                batteryLow = this[Keys.live("battery_low")] ?: d.live.batteryLow,
                ringer = this[Keys.live("ringer")] ?: d.live.ringer,
                headphones = this[Keys.live("headphones")] ?: d.live.headphones,
            ),
            onboardingDone = this[Keys.onboardingDone] ?: d.onboardingDone,
            accessibilityDisclosureAccepted = this[Keys.disclosure] ?: d.accessibilityDisclosureAccepted,
        )
    }

    private fun MutablePreferences.write(s: IslandSettings) {
        this[Keys.enabled] = s.enabled
        this[Keys.preset] = s.appearance.preset.name
        this[Keys.accent] = s.appearance.accentArgb
        this[Keys.sizeScale] = s.appearance.sizeScale
        this[Keys.offsetX] = s.appearance.offsetXDp
        this[Keys.offsetY] = s.appearance.offsetYDp
        this[Keys.restPill] = s.appearance.showRestPill
        this[Keys.glass] = s.appearance.glassIntensity
        this[Keys.autoColour] = s.appearance.autoColour
        this[Keys.glassCard] = s.appearance.glassCard
        this[Keys.live("media")] = s.live.media
        this[Keys.live("calls")] = s.live.calls
        this[Keys.live("progress")] = s.live.progress
        this[Keys.live("timers")] = s.live.timers
        this[Keys.live("charging")] = s.live.charging
        this[Keys.live("battery_low")] = s.live.batteryLow
        this[Keys.live("ringer")] = s.live.ringer
        this[Keys.live("headphones")] = s.live.headphones
        this[Keys.hideForeground] = s.whenToShow.hideInForegroundApp
        this[Keys.hideFullscreen] = s.whenToShow.hideInFullscreen
        this[Keys.hideLandscape] = s.whenToShow.hideInLandscape
        this[Keys.showLockscreen] = s.whenToShow.showOnLockscreen
        this[Keys.hideShade] = s.whenToShow.hideWhenShadeOpen
        this[Keys.autoHide] = s.autoHideSeconds
        this[Keys.onboardingDone] = s.onboardingDone
        this[Keys.disclosure] = s.accessibilityDisclosureAccepted
        val g = s.gestures
        this[Keys.gesture("compact_tap")] = g.compactTap.name
        this[Keys.gesture("compact_down")] = g.compactSwipeDown.name
        this[Keys.gesture("compact_up")] = g.compactSwipeUp.name
        this[Keys.gesture("compact_side")] = g.compactSwipeSide.name
        this[Keys.gesture("compact_long")] = g.compactLongPress.name
        this[Keys.gesture("expanded_tap")] = g.expandedTap.name
        this[Keys.gesture("expanded_down")] = g.expandedSwipeDown.name
        this[Keys.gesture("expanded_up")] = g.expandedSwipeUp.name
        this[Keys.gesture("expanded_side")] = g.expandedSwipeSide.name
        this[Keys.gesture("expanded_long")] = g.expandedLongPress.name
    }
}
