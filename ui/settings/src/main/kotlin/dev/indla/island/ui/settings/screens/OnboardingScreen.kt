package dev.indla.island.ui.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.IslandSettings
import dev.indla.island.ui.island.AppIconImage
import dev.indla.island.ui.settings.AppEntry
import dev.indla.island.ui.settings.OemBatterySteps
import dev.indla.island.ui.settings.PermissionStatus
import dev.indla.island.ui.settings.Permissions
import dev.indla.island.ui.settings.components.GlassCard
import dev.indla.island.ui.settings.components.GlassPill
import dev.indla.island.ui.settings.components.IslandPreview
import dev.indla.island.ui.settings.theme.LocalGlass

/** The Play-required prominent disclosure, shown before the system accessibility screen. */
const val ACCESSIBILITY_DISCLOSURE =
    "Island uses the Accessibility Service API for one thing: to draw the island over your status bar, " +
        "at the camera cutout, which normal overlays can't do. It also notes which app is in front so the " +
        "island can stay out of your way.\n\n" +
        "Island does not use it to read, collect or share what's on your screen, what you type, or any " +
        "personal data, and Island has no internet access. You can turn it off any time in Settings → Accessibility."

/** Four-step checklist that live-updates as each permission is granted. */
@Composable
fun OnboardingScreen(
    settings: IslandSettings,
    permissions: PermissionStatus,
    reduceTransparency: Boolean,
    apps: List<AppEntry>?,
    onAcceptDisclosure: () -> Unit,
    onFinish: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    val messaging = apps.orEmpty().filter { it.isMessaging }
    var selected by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(apps) { if (selected == null && apps != null) selected = messaging.map { it.packageName }.toSet() }
    val picked = selected.orEmpty()
    val (oemName, oemSteps) = OemBatterySteps.forManufacturer()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 24.dp, 16.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                Text("Set up Island", color = glass.content, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("Four steps, about two minutes.", color = glass.contentSecondary, fontSize = 16.sp)
            }
        }
        item { IslandPreview(settings.appearance, reduceTransparency) }

        item {
            Step(1, "Notification access", "So Island can read what to show.", permissions.notificationAccess) {
                GlassPill("Allow", { Permissions.openNotificationAccess(context) }, primary = true)
            }
        }
        item {
            Step(2, "Accessibility", "So Island can draw over the status bar.", permissions.accessibility) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill("Allow", {
                        if (settings.accessibilityDisclosureAccepted) Permissions.openAccessibility(context) else showDisclosure = true
                    }, primary = true)
                    if (Permissions.restrictedSettingsLikely) {
                        Text(
                            "Switch greyed out? Installed outside the Play Store, Android 13+ needs: App info → ⋮ → Allow restricted settings.",
                            color = glass.contentSecondary,
                            fontSize = 13.sp,
                        )
                        GlassPill("Open App info", { Permissions.openAppInfo(context) })
                    }
                }
            }
        }
        item {
            Step(3, "Battery", "Unrestricted, so Android doesn't stop Island.", permissions.batteryUnrestricted) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassPill("Set to Unrestricted", { Permissions.requestBatteryExemption(context) }, primary = true)
                    Text("$oemName also needs:", color = glass.contentSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    oemSteps.forEach { Text("• $it", color = glass.contentSecondary, fontSize = 13.sp) }
                }
            }
        }
        item {
            Step(4, "Pick apps", "Messaging apps are pre-selected. Add more later in Apps.", done = picked.isNotEmpty()) {
                Column {
                    if (messaging.isEmpty()) {
                        Text("No chat apps found yet, pick any in the Apps tab later.", color = glass.contentSecondary, fontSize = 13.sp)
                    }
                    messaging.forEach { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .clickable {
                                    selected = if (app.packageName in picked) picked - app.packageName else picked + app.packageName
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconImage(app.packageName, 30.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = glass.content, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Checkbox(checked = app.packageName in picked, onCheckedChange = { on ->
                                selected = if (on) picked + app.packageName else picked - app.packageName
                            })
                        }
                    }
                }
            }
        }
        item {
            GlassPill(
                if (permissions.essentials) "Finish" else "Finish (steps 1 and 2 needed)",
                onClick = { onFinish(picked) },
                primary = true,
                enabled = permissions.essentials,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            )
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("How Island uses Accessibility") },
            text = { Text(ACCESSIBILITY_DISCLOSURE) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    onAcceptDisclosure()
                    Permissions.openAccessibility(context)
                }) { Text("Accept") }
            },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Decline") } },
        )
    }
}

@Composable
private fun Step(number: Int, title: String, why: String, done: Boolean, action: @Composable () -> Unit) {
    val glass = LocalGlass.current
    val scheme = MaterialTheme.colorScheme
    GlassCard {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) scheme.primary else scheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                else Text("$number", color = scheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = glass.content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(why, color = glass.contentSecondary, fontSize = 14.sp)
            }
        }
        if (!done) {
            Box(Modifier.padding(start = 66.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)) { action() }
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}
