package dev.indla.island.ui.settings.screens

import android.service.notification.NotificationListenerService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.ServiceHealth
import dev.indla.island.service.listener.IslandNotificationListener
import dev.indla.island.ui.settings.PermissionStatus
import dev.indla.island.ui.settings.Permissions
import dev.indla.island.ui.settings.components.AccessibilityHelp
import dev.indla.island.ui.settings.components.GlassCard
import dev.indla.island.ui.settings.components.GlassChip
import dev.indla.island.ui.settings.components.GlassPill
import dev.indla.island.ui.settings.components.GlassRow
import dev.indla.island.ui.settings.components.IslandPreview
import dev.indla.island.ui.settings.components.RowDivider
import dev.indla.island.ui.settings.components.Space
import dev.indla.island.ui.settings.components.StatusDot
import dev.indla.island.ui.settings.components.TabScaffold
import dev.indla.island.ui.settings.theme.LocalGlass

@Composable
fun HomeScreen(
    settings: IslandSettings,
    permissions: PermissionStatus,
    health: ServiceHealth,
    reduceTransparency: Boolean,
    showDebug: Boolean,
    onEnabled: (Boolean) -> Unit,
    onSendTest: () -> Unit,
    onTryCall: () -> Unit,
    onTryDownload: () -> Unit,
    onTryMusic: () -> Unit,
    onOpenFixtures: () -> Unit,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val checks = listOf(
        Check(
            title = "Notification access",
            icon = Icons.Rounded.NotificationsActive,
            ok = permissions.notificationAccess && health.listenerConnected,
            problem = if (permissions.notificationAccess) "Android stopped the listener" else "Needed to read what to show",
            fixLabel = if (permissions.notificationAccess) "Reconnect" else "Allow",
            fix = {
                if (permissions.notificationAccess) {
                    NotificationListenerService.requestRebind(IslandNotificationListener.component(context))
                } else {
                    Permissions.openNotificationAccess(context)
                }
            },
        ),
        Check(
            title = "Draw over the status bar",
            icon = Icons.Rounded.Layers,
            ok = permissions.accessibility && health.overlayConnected,
            problem = "Accessibility service is off",
            fixLabel = "Turn on",
            fix = { Permissions.openAccessibility(context) },
        ),
        Check(
            title = "Battery",
            icon = Icons.Rounded.BatteryChargingFull,
            ok = permissions.batteryUnrestricted,
            problem = "Android may stop Island in the background",
            fixLabel = "Fix",
            fix = { Permissions.requestBatteryExemption(context) },
        ),
    )
    val problems = checks.count { !it.ok }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    TabScaffold(
        title = "Island",
        pinned = { IslandPreview(settings.appearance, reduceTransparency) },
        headerAction = { Switch(checked = settings.enabled, onCheckedChange = onEnabled) },
    ) {
        // Status: one line when everything runs; the failing checks, each a one-tap fix, otherwise.
        item {
            GlassCard {
                val chevron by animateFloatAsState(if (showDetails) 180f else 0f, label = "chevron")
                GlassRow(
                    title = when {
                        !settings.enabled -> "Island is off"
                        problems == 0 -> "All set"
                        problems == 1 -> "1 thing needs a fix"
                        else -> "$problems things need a fix"
                    },
                    subtitle = when {
                        !settings.enabled -> "Turn it on with the switch above"
                        problems == 0 -> "Reading notifications and drawing on screen"
                        else -> "Tap a row to fix it"
                    },
                    leading = { StatusDot(settings.enabled && problems == 0, 12.dp) },
                    onClick = if (problems == 0) ({ showDetails = !showDetails }) else null,
                ) {
                    if (problems == 0) {
                        Icon(Icons.Rounded.ExpandMore, "Details", tint = glass.contentSecondary, modifier = Modifier.rotate(chevron))
                    }
                }
                AnimatedVisibility(problems > 0 || showDetails) {
                    Column {
                        checks.forEach { check ->
                            RowDivider()
                            CheckRow(check)
                        }
                    }
                }
            }
        }

        if (permissions.restricted && !permissions.accessibility) {
            item {
                GlassCard(title = "Turn on accessibility") {
                    Box(Modifier.padding(horizontal = Space.rowPadding, vertical = Space.xs)) {
                        AccessibilityHelp(
                            restricted = true,
                            onTurnOn = { Permissions.openAccessibility(context) },
                            onAppInfo = { Permissions.openAppInfo(context) },
                        )
                    }
                }
            }
        }

        item {
            GlassCard(title = "Try it") {
                // Real notifications and a real media session, through the same path as any app.
                FlowRow(
                    Modifier.padding(horizontal = Space.rowPadding, vertical = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    GlassChip("Message", onSendTest, Icons.AutoMirrored.Rounded.Send)
                    GlassChip("Call", onTryCall, Icons.Rounded.Call)
                    GlassChip("Download", onTryDownload, Icons.Rounded.Download)
                    GlassChip("Music", onTryMusic, Icons.Rounded.MusicNote)
                }
                Text(
                    "Leave Island after starting music or a call to see it on the island.",
                    color = glass.contentSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = Space.rowPadding, vertical = Space.xs),
                )
                if (showDebug) {
                    RowDivider()
                    GlassRow(
                        "Notification fixtures",
                        subtitle = "Export notifications as JSON for parser tests",
                        icon = Icons.Rounded.BugReport,
                        onClick = onOpenFixtures,
                    ) { Icon(Icons.Rounded.ChevronRight, null, tint = glass.contentSecondary) }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.rowPadding, vertical = Space.xs)) {
                Icon(Icons.Rounded.Shield, null, tint = glass.contentSecondary, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Spacer(Modifier.width(Space.s))
                Text(
                    "No internet permission. Notifications are read on this phone and never leave it.",
                    color = glass.contentSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

private class Check(
    val title: String,
    val icon: ImageVector,
    val ok: Boolean,
    val problem: String,
    val fixLabel: String,
    val fix: () -> Unit,
)

@Composable
private fun CheckRow(check: Check) {
    val glass = LocalGlass.current
    GlassRow(
        check.title,
        subtitle = if (check.ok) null else check.problem,
        icon = check.icon,
        iconTint = if (check.ok) glass.contentSecondary else MaterialTheme.colorScheme.primary,
        onClick = if (check.ok) null else check.fix,
    ) {
        if (check.ok) {
            Text("On", color = glass.contentSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = Space.s))
        } else {
            GlassPill(check.fixLabel, check.fix, primary = true, compact = true, modifier = Modifier.padding(start = Space.s))
        }
    }
}
