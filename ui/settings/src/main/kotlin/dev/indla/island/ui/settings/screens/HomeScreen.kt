package dev.indla.island.ui.settings.screens

import android.service.notification.NotificationListenerService
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.ServiceHealth
import dev.indla.island.service.listener.IslandNotificationListener
import dev.indla.island.ui.settings.PermissionStatus
import dev.indla.island.ui.settings.Permissions
import dev.indla.island.ui.settings.components.GlassCard
import dev.indla.island.ui.settings.components.GlassPill
import dev.indla.island.ui.settings.components.GlassRow
import dev.indla.island.ui.settings.components.IslandPreview
import dev.indla.island.ui.settings.components.RowDivider
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
    onOpenFixtures: () -> Unit,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    TabScaffold(
        title = "Island",
        pinned = { IslandPreview(settings.appearance, reduceTransparency) },
    ) {
        item {
            GlassCard {
                GlassRow(
                    title = if (settings.enabled) "Island is on" else "Island is off",
                    subtitle = "Show notifications at the camera cutout",
                    onClick = { onEnabled(!settings.enabled) },
                ) {
                    Switch(checked = settings.enabled, onCheckedChange = onEnabled)
                }
            }
        }

        item {
            val listenerOk = permissions.notificationAccess && health.listenerConnected
            val overlayOk = permissions.accessibility && health.overlayConnected
            val batteryOk = permissions.batteryUnrestricted
            val problems = listOf(listenerOk, overlayOk, batteryOk).count { !it }
            GlassCard {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(problems == 0, 12.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when (problems) {
                            0 -> "All services running"
                            1 -> "1 thing needs a fix"
                            else -> "$problems things need a fix"
                        },
                        color = glass.content,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HealthRow(
                    title = "Notification access",
                    ok = listenerOk,
                    detail = when {
                        listenerOk -> "Reading notifications"
                        permissions.notificationAccess -> "Granted, but Android stopped the listener"
                        else -> "Needed to read what to show"
                    },
                    icon = Icons.Rounded.NotificationsActive,
                    fixLabel = if (permissions.notificationAccess) "Reconnect" else "Allow",
                    onFix = {
                        if (permissions.notificationAccess) {
                            NotificationListenerService.requestRebind(IslandNotificationListener.component(context))
                        } else {
                            Permissions.openNotificationAccess(context)
                        }
                    },
                )
                RowDivider()
                HealthRow(
                    title = "Draw over the status bar",
                    ok = overlayOk,
                    detail = if (overlayOk) "Island is on screen" else "Accessibility service is off",
                    icon = Icons.Rounded.Layers,
                    fixLabel = "Turn on",
                    onFix = { Permissions.openAccessibility(context) },
                )
                RowDivider()
                HealthRow(
                    title = "Battery",
                    ok = batteryOk,
                    detail = if (batteryOk) "Unrestricted" else "Android may stop Island in the background",
                    icon = Icons.Rounded.BatteryChargingFull,
                    fixLabel = "Fix",
                    onFix = { Permissions.requestBatteryExemption(context) },
                )
            }
        }

        if (Permissions.restrictedSettingsLikely && !permissions.accessibility) {
            item {
                GlassCard(title = "Switch greyed out?") {
                    Text(
                        "Android 13+ restricts accessibility for apps installed outside the Play Store. " +
                            "Open App info, tap ⋮ in the corner, choose Allow restricted settings, then try again.",
                        color = glass.contentSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    GlassPill("Open App info", { Permissions.openAppInfo(context) }, Modifier.padding(20.dp, 10.dp))
                }
            }
        }

        item {
            GlassPill(
                "Send test notification",
                onClick = onSendTest,
                icon = Icons.Rounded.Send,
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showDebug) {
            item {
                GlassCard {
                    GlassRow(
                        "Notification fixtures",
                        subtitle = "Export real notifications as JSON for parser tests",
                        icon = Icons.Rounded.BugReport,
                        onClick = onOpenFixtures,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = glass.contentSecondary) }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Shield, null, tint = glass.contentSecondary, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Island has no internet permission. Notifications are read on this phone and never leave it.",
                    color = glass.contentSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun HealthRow(
    title: String,
    ok: Boolean,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fixLabel: String,
    onFix: () -> Unit,
) {
    // When something is off, the whole row is the one-tap fix.
    GlassRow(title, subtitle = detail, icon = icon, onClick = if (ok) null else onFix) {
        if (ok) {
            StatusDot(true)
        } else {
            Text(
                fixLabel,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
