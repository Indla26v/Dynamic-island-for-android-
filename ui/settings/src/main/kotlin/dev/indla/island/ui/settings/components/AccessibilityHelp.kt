package dev.indla.island.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.ui.settings.Permissions
import dev.indla.island.ui.settings.theme.LocalGlass

/**
 * How to turn the accessibility service on, written for this phone.
 *
 * When Android has restricted Island (installed from a file on Android 13+), the order matters:
 * the "Allow restricted settings" item in App info's ⋮ menu only appears after the user has
 * tried the switch once and seen the "Restricted setting" dialog. Many guides skip that step,
 * which is why the menu item seems to be missing.
 */
@Composable
fun AccessibilityHelp(restricted: Boolean, onTurnOn: () -> Unit, onAppInfo: () -> Unit) {
    val glass = LocalGlass.current
    if (!restricted) {
        Text(
            "Allow opens Island's switch. If it doesn't: ${Permissions.accessibilityPath()}",
            color = glass.contentSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(
            "Android blocks this switch for apps installed from a file. Three steps unlock it:",
            color = glass.content,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp,
        )
        HelpStep(1, "Tap Turn on. Android shows \"Restricted setting\". Tap OK.") {
            GlassPill("Turn on", onTurnOn, compact = true)
        }
        HelpStep(2, "Open App info, tap ⋮ at the top right, choose Allow restricted settings and confirm with your PIN.") {
            GlassPill("Open App info", onAppInfo, compact = true)
        }
        HelpStep(3, "Come back, tap Turn on again and switch Island on.")
        Text(
            "No ⋮ or no option? It only appears after step 1. Installing Island over USB or from the Play Store skips all of this.",
            color = glass.contentSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun HelpStep(number: Int, text: String, action: (@Composable () -> Unit)? = null) {
    val glass = LocalGlass.current
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Text("$number", color = scheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(Space.m))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(text, color = glass.content, fontSize = 14.sp, lineHeight = 19.sp)
            action?.invoke()
        }
    }
}
