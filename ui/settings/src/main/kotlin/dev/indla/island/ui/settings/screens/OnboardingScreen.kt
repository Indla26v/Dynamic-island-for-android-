package dev.indla.island.ui.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.indla.island.ui.settings.components.RowDivider
import dev.indla.island.ui.settings.components.Space
import dev.indla.island.ui.settings.theme.LocalGlass

/** The Play-required prominent disclosure, shown before the system accessibility screen. */
const val ACCESSIBILITY_DISCLOSURE =
    "Island uses the Accessibility Service API for one thing: to draw the island over your status bar, " +
        "at the camera cutout, which normal overlays can't do. It also notes which app is in front so the " +
        "island can stay out of your way.\n\n" +
        "Island does not use it to read, collect or share what's on your screen, what you type, or any " +
        "personal data, and Island has no internet access. You can turn it off any time in Settings → Accessibility."

private val Badge = 30.dp

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
    val done = listOf(permissions.notificationAccess, permissions.accessibility, permissions.batteryUnrestricted, picked.isNotEmpty())
    val progress by animateFloatAsState(done.count { it } / 4f, label = "progress")
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = Space.xl,
                bottom = 48.dp + Space.l * 2 + navBar,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.cardGap),
        ) {
            item {
                Column(Modifier.padding(horizontal = Space.s)) {
                    Text("Set up Island", color = glass.content, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Space.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${done.count { it }} of 4 done · about two minutes",
                            color = glass.contentSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Space.s))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
            }
            item { IslandPreview(settings.appearance, reduceTransparency) }

            item {
                GlassCard {
                    Step(1, "Notification access", "To read what to show", done[0], "Allow", onAction = {
                        Permissions.openNotificationAccess(context)
                    })
                    RowDivider(start = Space.rowPadding + Badge + Space.m)
                    Step(2, "Accessibility", "To draw over the status bar", done[1], "Allow", onAction = {
                        if (settings.accessibilityDisclosureAccepted) Permissions.openAccessibility(context) else showDisclosure = true
                    }) {
                        if (Permissions.restrictedSettingsLikely) {
                            Hint("Greyed out? App info → ⋮ → Allow restricted settings.")
                            GlassPill("Open App info", { Permissions.openAppInfo(context) }, compact = true)
                        }
                    }
                    RowDivider(start = Space.rowPadding + Badge + Space.m)
                    Step(3, "Battery", "So Android doesn't stop Island", done[2], "Allow", onAction = {
                        Permissions.requestBatteryExemption(context)
                    }) {
                        Hint("$oemName: " + oemSteps.joinToString(" · "))
                    }
                }
            }

            item {
                GlassCard(title = "Pick apps") {
                    Text(
                        "Chat apps are pre-selected. Add more any time in Apps.",
                        color = glass.contentSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = Space.rowPadding),
                    )
                    if (messaging.isEmpty()) {
                        Hint("No chat apps found; pick any in the Apps tab later.", Modifier.padding(horizontal = Space.rowPadding))
                    }
                    messaging.forEach { app ->
                        val on = app.packageName in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (on) picked - app.packageName else picked + app.packageName }
                                .padding(start = Space.rowPadding, end = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconImage(app.packageName, 28.dp)
                            Spacer(Modifier.width(Space.m))
                            Text(app.label, color = glass.content, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Checkbox(checked = on, onCheckedChange = { selected = if (it) picked + app.packageName else picked - app.packageName })
                        }
                    }
                }
            }
        }

        // Pinned, so finishing never needs a scroll; the list dissolves beneath it.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(48.dp + Space.l * 3 + navBar)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)))),
        )
        GlassPill(
            if (permissions.essentials) "Finish" else "Finish · steps 1 and 2 needed",
            onClick = { onFinish(picked) },
            primary = true,
            enabled = permissions.essentials,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(Space.l)
                .fillMaxWidth(),
        )
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

/** Badge, title and reason on one line with the action on the right; extra help only while pending. */
@Composable
private fun Step(
    number: Int,
    title: String,
    why: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val glass = LocalGlass.current
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.rowPadding, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(Badge).clip(CircleShape).background(if (done) scheme.primary else scheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, "Done", tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                else Text("$number", color = scheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(title, color = glass.content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(why, color = glass.contentSecondary, fontSize = 13.sp)
            }
            if (!done) GlassPill(actionLabel, onAction, primary = true, compact = true, modifier = Modifier.padding(start = Space.s))
        }
        if (extra != null) {
            AnimatedVisibility(!done) {
                Column(
                    Modifier.padding(start = Space.rowPadding + Badge + Space.m, end = Space.rowPadding, bottom = Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) { extra() }
            }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalGlass.current.contentSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = modifier)
}
