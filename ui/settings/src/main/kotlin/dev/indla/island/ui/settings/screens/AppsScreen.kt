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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.indla.island.model.Priority
import dev.indla.island.ui.island.AppIconImage
import dev.indla.island.ui.settings.AppEntry
import dev.indla.island.ui.settings.components.GlassToggleRow
import dev.indla.island.ui.settings.components.RowDivider
import dev.indla.island.ui.settings.components.SectionLabel
import dev.indla.island.ui.settings.components.Segmented
import dev.indla.island.ui.settings.components.Space
import dev.indla.island.ui.settings.components.TabScaffold
import dev.indla.island.ui.settings.components.glassSurface
import dev.indla.island.ui.settings.theme.LocalGlass

/** Accent swatches shared by the Apps and Look tabs. */
val AccentSwatches = listOf(0xFF8AB4F8, 0xFF7EE0B5, 0xFFFFC66D, 0xFFFF8A80, 0xFFD7AEFB, 0xFF80DEEA, 0xFFFFFFFF)

private val IconSize = 36.dp

/** The icon sits concentric in its pill: the same gap above, below and before it. */
private val IconGap = Space.s

/** An open app grows from a pill into a card with these corners. */
private val OpenRadius = 28.dp

@Composable
fun AppsScreen(
    apps: List<AppEntry>?,
    ruleFor: (String) -> AppRule,
    onSave: (AppRule) -> Unit,
    onSetAll: (Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    val (shown, hidden) = filtered.partition { ruleFor(it.packageName).show }

    // No preview here: the list is the page. Every row is its own glass pill, a small gap apart.
    TabScaffold(title = "Apps", itemSpacing = Space.s) {
        item(key = "about") {
            // One plain sentence instead of a wall of switches: what this list is for.
            Text(
                "Choose which apps pop up on the island. Music, calls, timers and phone modes always show.",
                color = LocalGlass.current.contentSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = Space.rowPadding),
            )
        }
        item(key = "search") {
            SearchField(query, { query = it })
        }
        if (!apps.isNullOrEmpty()) {
            item(key = "all") {
                val allOn = apps.all { ruleFor(it.packageName).show }
                Box(Modifier.fillMaxWidth().glassSurface(CircleShape)) {
                    GlassToggleRow(
                        "All apps",
                        allOn,
                        onSetAll,
                        subtitle = if (allOn) "Every app can use the island" else "${apps.count { ruleFor(it.packageName).show }} of ${apps.size} apps on",
                        icon = Icons.Rounded.SelectAll,
                    )
                }
            }
        }
        if (apps == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        section("On the island", shown, expanded, { expanded = it }, ruleFor, onSave)
        section("Other apps", hidden, expanded, { expanded = it }, ruleFor, onSave)
        if (apps != null && filtered.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No apps match “$query”",
                    color = LocalGlass.current.contentSecondary,
                    modifier = Modifier.padding(Space.l),
                )
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    apps: List<AppEntry>,
    expanded: String?,
    onExpand: (String?) -> Unit,
    ruleFor: (String) -> AppRule,
    onSave: (AppRule) -> Unit,
) {
    if (apps.isEmpty()) return
    item(key = "label:$title") {
        SectionLabel("$title · ${apps.size}", Modifier.padding(top = Space.s))
    }
    items(apps, key = { app -> app.packageName }) { app ->
        val rule = ruleFor(app.packageName)
        val open = expanded == app.packageName
        // A pill while closed; opening it makes room for the editor under a card's corners.
        val shape = if (open) RoundedCornerShape(OpenRadius) else CircleShape
        Column(Modifier.animateItem().fillMaxWidth().glassSurface(shape)) {
            // Switching an app back on also lifts a long-press Mute.
            AppRow(app, rule, onToggle = { onSave(rule.copy(show = it, muted = rule.muted && !it)) }, onClick = { onExpand(if (open) null else app.packageName) })
            AnimatedVisibility(open) { RuleEditor(rule, onSave) }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .glassSurface(CircleShape)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = glass.contentSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.s))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search apps", color = glass.contentSecondary, fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = glass.content, fontSize = 15.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                "Clear search",
                tint = glass.contentSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onQuery("") },
            )
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, rule: AppRule, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = IconGap, end = Space.m, top = IconGap, bottom = IconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(app.packageName, IconSize)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = glass.content, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            val details = buildList {
                if (rule.muted) add("Muted")
                if (rule.show) add(priorityLabel(rule.priority))
                if (rule.autoExpand) add("Expands")
                if (rule.hideContentOnLock) add("Private on lock")
                if (app.isMessaging) add("Chat")
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), color = glass.contentSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
        Switch(checked = rule.show, onCheckedChange = onToggle)
    }
}

@Composable
private fun RuleEditor(rule: AppRule, onSave: (AppRule) -> Unit) {
    val inset = IconGap + IconSize + Space.m
    Column(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
        RowDivider(start = inset)
        Segmented(
            options = Priority.entries,
            selected = rule.priority,
            label = ::priorityLabel,
            onSelect = { onSave(rule.copy(priority = it)) },
            modifier = Modifier.padding(start = inset, end = Space.rowPadding, top = Space.m, bottom = Space.xs),
        )
        GlassToggleRow("Expand automatically", rule.autoExpand, { onSave(rule.copy(autoExpand = it)) })
        GlassToggleRow(
            "Private on lockscreen",
            rule.hideContentOnLock,
            { onSave(rule.copy(hideContentOnLock = it)) },
            subtitle = "Only the app icon until you unlock",
        )
        Swatches(
            selected = rule.accentArgb,
            includeAuto = true,
            onPick = { onSave(rule.copy(accentArgb = it)) },
            modifier = Modifier.padding(horizontal = Space.rowPadding, vertical = Space.s),
        )
    }
}

@Composable
fun Swatches(selected: Long?, includeAuto: Boolean, onPick: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (includeAuto) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .glassSurface(CircleShape, strong = true)
                    .then(if (selected == null) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                    .clickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { Text("A", color = glass.content, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        AccentSwatches.forEach { argb ->
            Box(
                Modifier
                    .size(32.dp)
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
