package dev.indla.island.ui.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.indla.island.model.AppRule
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.Priority
import dev.indla.island.ui.island.AppIconImage
import dev.indla.island.ui.settings.AppEntry
import dev.indla.island.ui.settings.components.GlassToggleRow
import dev.indla.island.ui.settings.components.IslandPreview
import dev.indla.island.ui.settings.components.TabScaffold
import dev.indla.island.ui.settings.components.glassSurface
import dev.indla.island.ui.settings.theme.LocalGlass

/** Accent swatches shared by the Apps and Look & feel tabs. */
val AccentSwatches = listOf(0xFF8AB4F8, 0xFF7EE0B5, 0xFFFFC66D, 0xFFFF8A80, 0xFFD7AEFB, 0xFF80DEEA, 0xFFFFFFFF)

@Composable
fun AppsScreen(
    appearance: IslandAppearance,
    reduceTransparency: Boolean,
    apps: List<AppEntry>?,
    ruleFor: (String) -> AppRule,
    onSave: (AppRule) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val glass = LocalGlass.current
    val filtered = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    // Rows sit 2 dp apart so they read as one inset group.
    TabScaffold(title = "Apps", pinned = { IslandPreview(appearance, reduceTransparency) }, itemSpacing = 2.dp) {
        item {
            Row(
                Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .glassSurface(CircleShape)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = glass.contentSecondary)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", color = glass.contentSecondary, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = glass.content, fontSize = 16.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (apps == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }

        itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
            val rule = ruleFor(app.packageName)
            val shape = groupShape(index, filtered.size)
            Column(Modifier.fillMaxWidth().glassSurface(shape)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (expanded == app.packageName) null else app.packageName }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(app.packageName, 38.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = glass.content, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            buildString {
                                append(if (rule.show) priorityLabel(rule.priority) else "Hidden")
                                if (app.isMessaging) append(" · Messaging")
                            },
                            color = glass.contentSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(checked = rule.show, onCheckedChange = { onSave(rule.copy(show = it)) })
                }
                AnimatedVisibility(expanded == app.packageName) {
                    RuleEditor(rule, onSave)
                }
            }
        }
    }
}

@Composable
private fun RuleEditor(rule: AppRule, onSave: (AppRule) -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Priority", color = glass.contentSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { p ->
                val selected = rule.priority == p
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier.glassSurface(CircleShape, strong = true),
                        )
                        .clickable { onSave(rule.copy(priority = p)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        priorityLabel(p),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else glass.content,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        GlassToggleRow("Expand automatically", rule.autoExpand, { onSave(rule.copy(autoExpand = it)) })
        GlassToggleRow(
            "Hide content on lockscreen",
            rule.hideContentOnLock,
            { onSave(rule.copy(hideContentOnLock = it)) },
            subtitle = "Shows the app icon only until you unlock",
        )
        Text("Colour", color = glass.contentSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        Swatches(
            selected = rule.accentArgb,
            includeAuto = true,
            onPick = { onSave(rule.copy(accentArgb = it)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun Swatches(selected: Long?, includeAuto: Boolean, onPick: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (includeAuto) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .glassSurface(CircleShape, strong = true)
                    .then(if (selected == null) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                    .clickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { Text("A", color = glass.content, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        AccentSwatches.forEach { argb ->
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(if (selected == argb) Modifier.border(3.dp, glass.content, CircleShape) else Modifier)
                    .clickable { onPick(argb) },
            )
        }
    }
}

private fun priorityLabel(p: Priority) = when (p) {
    Priority.HIGH -> "High"
    Priority.NORMAL -> "Normal"
    Priority.SILENT -> "Silent"
}

/** iOS-Settings-style inset group: only the first and last rows are rounded. */
private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val r = 28.dp
    val small = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) r else small,
        topEnd = if (index == 0) r else small,
        bottomStart = if (index == count - 1) r else small,
        bottomEnd = if (index == count - 1) r else small,
    )
}
