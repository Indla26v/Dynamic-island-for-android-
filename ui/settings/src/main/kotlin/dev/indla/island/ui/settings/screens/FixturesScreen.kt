package dev.indla.island.ui.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.service.listener.FixtureEntry
import dev.indla.island.ui.settings.components.GlassCard
import dev.indla.island.ui.settings.components.GlassPill
import dev.indla.island.ui.settings.components.GlassRow
import dev.indla.island.ui.settings.theme.LocalGlass
import java.text.DateFormat
import java.util.Date

/** Debug builds only: export any notification's extras as a JSON fixture for parser tests. */
@Composable
fun FixturesScreen(entries: List<FixtureEntry>, onClear: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val time = DateFormat.getTimeInstance(DateFormat.MEDIUM)
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = glass.content) }
                Text("Notification fixtures", color = glass.content, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                GlassPill("Clear", onClear)
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "Nothing recorded yet. Notifications posted while Island's listener is connected show up here.",
                    color = glass.contentSecondary,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(entries, key = { it.postTime.toString() + it.packageName + it.json.hashCode() }) { entry ->
            GlassCard {
                GlassRow(
                    entry.title.ifBlank { "(no title)" },
                    subtitle = "${entry.packageName} · ${time.format(Date(entry.postTime))}",
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("application/json")
                            .putExtra(Intent.EXTRA_SUBJECT, "Island fixture ${entry.packageName}")
                            .putExtra(Intent.EXTRA_TEXT, entry.json)
                        context.startActivity(Intent.createChooser(send, "Export fixture"))
                    },
                ) {
                    GlassPill("Copy", {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("fixture", entry.json))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}
