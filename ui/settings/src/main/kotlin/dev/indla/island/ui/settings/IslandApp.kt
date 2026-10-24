package dev.indla.island.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.indla.island.ui.island.Transparency
import dev.indla.island.ui.settings.components.AppBackground
import dev.indla.island.ui.settings.components.GlassTabBar
import dev.indla.island.ui.settings.components.TabItem
import dev.indla.island.ui.settings.screens.AppsScreen
import dev.indla.island.ui.settings.screens.FixturesScreen
import dev.indla.island.ui.settings.screens.HomeScreen
import dev.indla.island.ui.settings.screens.LookScreen
import dev.indla.island.ui.settings.screens.OnboardingScreen
import dev.indla.island.ui.settings.theme.IslandTheme

private val Tabs = listOf(
    TabItem("Home", Icons.Rounded.Home),
    TabItem("Apps", Icons.Rounded.Apps),
    TabItem("Look & feel", Icons.Rounded.Palette),
)

/** Three tabs replace a five-entry menu; every tab opens with a live island preview. */
@Composable
fun IslandApp(showDebug: Boolean) {
    val vm: SettingsViewModel = hiltViewModel()
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val fixtures by vm.fixtures.collectAsStateWithLifecycle()
    var reduceTransparency by remember { mutableStateOf(Transparency.reduced(context)) }

    LifecycleResumeEffect(Unit) {
        // Live-updates the checklist and health card when coming back from system settings.
        vm.refreshPermissions()
        reduceTransparency = Transparency.reduced(context)
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refreshPermissions()
        if (granted) vm.sendTestNotification()
    }
    val optionalPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
    }
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val sendTest = {
        when {
            vm.sendTestNotification() -> Unit
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> Toast.makeText(context, "Notifications are blocked for Island", Toast.LENGTH_SHORT).show()
        }
    }

    val s = settings
    IslandTheme(s?.appearance?.glassIntensity ?: 0.6f, reduceTransparency) {
        AppBackground {
            if (s == null) return@AppBackground
            if (!s.onboardingDone) {
                OnboardingScreen(
                    settings = s,
                    permissions = permissions,
                    reduceTransparency = reduceTransparency,
                    apps = apps,
                    onAcceptDisclosure = vm::acceptDisclosure,
                    onFinish = { vm.finishOnboarding(it) },
                )
                return@AppBackground
            }

            var tab by rememberSaveable { mutableIntStateOf(0) }
            var showFixtures by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showFixtures) { showFixtures = false }
            BackHandler(enabled = !showFixtures && tab != 0) { tab = 0 }
            val haze = rememberHazeState()

            if (showFixtures) {
                FixturesScreen(fixtures, vm::clearFixtures) { showFixtures = false }
                return@AppBackground
            }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    Crossfade(tab, label = "tab") { current ->
                        when (current) {
                            0 -> HomeScreen(
                                settings = s,
                                permissions = permissions,
                                health = health,
                                reduceTransparency = reduceTransparency,
                                showDebug = showDebug,
                                onEnabled = vm::setEnabled,
                                onSendTest = sendTest,
                                onTryCall = { if (!vm.tryCall()) askNotifications() },
                                onTryDownload = { if (!vm.tryDownload()) askNotifications() },
                                onTryMusic = vm::tryMusic,
                                onTryEarbuds = vm::tryEarbuds,
                                onTryModes = vm::tryModes,
                                onAllowContacts = { optionalPermission.launch(Manifest.permission.READ_CONTACTS) },
                                onAllowBluetooth = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) optionalPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                },
                                onOpenFixtures = { showFixtures = true },
                            )
                            1 -> AppsScreen(
                                appearance = s.appearance,
                                reduceTransparency = reduceTransparency,
                                apps = apps,
                                ruleFor = { pkg -> rules[pkg] ?: vm.ruleFor(pkg) },
                                onSave = { vm.saveRule(it) },
                                live = s.live,
                                onLive = { vm.updateLive(it) },
                                onSetAll = { vm.setAllApps(it) },
                            )
                            else -> LookScreen(
                                settings = s,
                                reduceTransparency = reduceTransparency,
                                onAppearance = { vm.updateAppearance(it) },
                                onWhenToShow = { vm.updateWhenToShow(it) },
                                onGestures = { vm.updateGestures(it) },
                                onAutoHide = vm::setAutoHide,
                                onPositionEditing = vm::setPositionEditing,
                                onPreviewSize = vm::previewSize,
                                onNudge = { dx, dy -> vm.nudge(dx, dy) },
                                onResetPosition = { vm.resetPosition() },
                            )
                        }
                    }
                }
                GlassTabBar(Tabs, tab, { tab = it }, haze, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
