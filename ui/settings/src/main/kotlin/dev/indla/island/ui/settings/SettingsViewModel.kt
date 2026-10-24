package dev.indla.island.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.indla.island.data.ApplicationScope
import dev.indla.island.data.OverlayKeeper
import dev.indla.island.data.SettingsRepository
import dev.indla.island.data.rules.AppRuleRepository
import dev.indla.island.engine.IslandEngine
import dev.indla.island.model.AppRule
import dev.indla.island.model.AudioKind
import dev.indla.island.model.DefaultApps
import dev.indla.island.model.GestureMap
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.LiveContent
import dev.indla.island.model.LiveSettings
import dev.indla.island.model.ServiceHealth
import dev.indla.island.model.SystemEvent
import dev.indla.island.model.WhenToShow
import dev.indla.island.service.listener.FixtureEntry
import dev.indla.island.service.listener.FixtureRecorder
import dev.indla.island.service.listener.selftest.TestLiveActivities
import dev.indla.island.service.listener.selftest.TestNotificationPoster
import dev.indla.island.service.overlay.FrameStats
import dev.indla.island.service.overlay.OverlayFrameStats
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val rulesRepo: AppRuleRepository,
    private val engine: IslandEngine,
    private val poster: TestNotificationPoster,
    private val installedApps: InstalledApps,
    private val recorder: FixtureRecorder,
    private val live: TestLiveActivities,
    /** Demos outlive this screen: leaving the app to watch them must not cancel them. */
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    /** Null until DataStore has been read, so onboarding never flashes for returning users. */
    val settings: StateFlow<IslandSettings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rules: StateFlow<Map<String, AppRule>> = rulesRepo.rules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val health: StateFlow<ServiceHealth> = engine.health
    val fixtures: StateFlow<List<FixtureEntry>> = recorder.entries
    val frameStats: StateFlow<FrameStats> = OverlayFrameStats.stats

    private val _permissions = MutableStateFlow(Permissions.status(context))
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private var testRound = 0

    init {
        viewModelScope.launch { _apps.value = installedApps.load() }
    }

    fun refreshPermissions() {
        OverlayKeeper.restoreIfDropped(context)
        _permissions.value = Permissions.status(context)
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun updateAppearance(transform: (IslandAppearance) -> IslandAppearance) =
        viewModelScope.launch { settingsRepo.updateAppearance(transform) }

    fun updateWhenToShow(transform: (WhenToShow) -> WhenToShow) = update { it.copy(whenToShow = transform(it.whenToShow)) }

    fun updateGestures(transform: (GestureMap) -> GestureMap) = update { it.copy(gestures = transform(it.gestures)) }

    fun setAutoHide(seconds: Int) = update { it.copy(autoHideSeconds = seconds) }

    fun updateLive(transform: (LiveSettings) -> LiveSettings) = update { it.copy(live = transform(it.live)) }

    /** "Try it" live activities; false when notifications are blocked and need the permission. */
    fun tryCall(): Boolean = poster.canPost().also { if (it) live.incomingCall() }

    fun tryDownload(): Boolean = poster.canPost().also { if (it) live.startDownload() }

    fun tryMusic() = live.playMusic()

    /** Every mode glance in turn, as the phone would raise them. */
    fun tryModes() {
        appScope.launch(Dispatchers.Main) {
            listOf(
                LiveContent.System(SystemEvent.CHARGING, 62, "62%"),
                LiveContent.System(SystemEvent.UNPLUGGED, 64, "64%"),
                LiveContent.System(SystemEvent.BATTERY_LOW, 12, "12%"),
                LiveContent.System(SystemEvent.RINGER_SILENT, null, "Sounds off"),
                LiveContent.System(SystemEvent.RINGER_VIBRATE, null, "Vibrate only"),
                LiveContent.System(SystemEvent.RINGER_NORMAL, null, "Sound on"),
                LiveContent.System(SystemEvent.AIRPLANE_ON, null, "On"),
                LiveContent.System(SystemEvent.FLASHLIGHT_ON, null, "On"),
                LiveContent.System(SystemEvent.SAVER_ON, 62, "On · 62%"),
                LiveContent.System(SystemEvent.MONSTER_ON, null, "On"),
                LiveContent.System(SystemEvent.DARK_MODE, null, "On"),
                LiveContent.System(SystemEvent.LIGHT_MODE, null, "On"),
                LiveContent.System(SystemEvent.WIFI_CONNECTED, null, "Connected"),
                LiveContent.System(SystemEvent.CLIPBOARD, null, "Meet at 7, Café Coffee Day, 100 Ft Rd"),
                LiveContent.System(SystemEvent.LOCATION_ON, null, "On"),
                LiveContent.System(SystemEvent.DND_ON, null, "Priority only"),
                LiveContent.System(SystemEvent.BEDTIME_ON, null, "Until 7:00 AM"),
                LiveContent.System(SystemEvent.DATA_ON, null, "On"),
                LiveContent.System(SystemEvent.AUTH_FAILED, 0, "Fingerprint not recognised"),
                LiveContent.System(SystemEvent.UNLOCKED, 0, "Fingerprint"),
                // The demo torch was never lit, so it says off itself.
                LiveContent.System(SystemEvent.FLASHLIGHT_OFF, null, "Off"),
            ).forEach {
                engine.onSystemEvent(it)
                delay(2_600)
            }
        }
    }

    /** The headphones card as a pair would raise it: connected first, battery a moment later. */
    fun tryEarbuds() {
        val (name, kind) = DEMO_AUDIO[demoAudio++ % DEMO_AUDIO.size]
        val card = LiveContent.System(SystemEvent.HEADPHONES_CONNECTED, null, name, device = kind)
        engine.onSystemEvent(card)
        appScope.launch(Dispatchers.Main) {
            delay(900)
            engine.onSystemEvent(card.copy(level = 75))
        }
    }

    fun acceptDisclosure() = update { it.copy(accessibilityDisclosureAccepted = true) }

    fun ruleFor(packageName: String): AppRule = rules.value[packageName] ?: DefaultApps.defaultRule(packageName)

    fun saveRule(rule: AppRule) = viewModelScope.launch { rulesRepo.save(rule) }

    /** "All apps" switch: every listed app's notifications on (lifting any mute) or off. */
    fun setAllApps(show: Boolean) = viewModelScope.launch {
        val apps = _apps.value.orEmpty()
        rulesRepo.saveAll(apps.map { app -> ruleFor(app.packageName).let { it.copy(show = show, muted = it.muted && !show) } })
    }

    /**
     * Last onboarding step: only the chat apps listed there get a rule. Writing "off" rules for
     * every other app would hide nothing new (they're off by default) but would pin choices the
     * user never made.
     */
    fun finishOnboarding(selected: Set<String>) = viewModelScope.launch {
        val chats = _apps.value.orEmpty().filter { it.isMessaging }
        rulesRepo.saveAll(chats.map { ruleFor(it.packageName).copy(show = it.packageName in selected) })
        settingsRepo.update { it.copy(onboardingDone = true) }
    }

    fun sendTestNotification(): Boolean {
        if (!poster.canPost()) return false
        poster.post(testRound++)
        return true
    }

    fun setPositionEditing(editing: Boolean) = engine.setPositionEditing(editing)

    /** Live size while the slider is dragged; null once it is released and saved. */
    fun previewSize(scale: Float?) = engine.previewSize(scale)

    fun nudge(dxDp: Float, dyDp: Float) = updateAppearance {
        it.copy(offsetXDp = it.offsetXDp + dxDp, offsetYDp = (it.offsetYDp + dyDp).coerceIn(-40f, 160f))
    }

    fun resetPosition() = updateAppearance { it.copy(offsetXDp = 0f, offsetYDp = 0f) }

    fun clearFixtures() = recorder.clear()

    private fun update(transform: (IslandSettings) -> IslandSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    override fun onCleared() {
        engine.setPositionEditing(false)
        engine.previewSize(null)
    }

    private var demoAudio = 0

    private companion object {
        /** Each tap on the Earbuds chip shows the next kind of audio device. */
        val DEMO_AUDIO = listOf(
            "Island Buds Pro" to AudioKind.EARBUDS,
            "Island Studio Max" to AudioKind.HEADPHONES,
            "Island Boom Speaker" to AudioKind.SPEAKER,
        )
    }
}
