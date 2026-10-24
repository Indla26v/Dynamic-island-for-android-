package dev.indla.island.ui.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
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
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.LiveSettings
import dev.indla.island.model.Priority
import dev.indla.island.ui.island.AppIconImage
import dev.indla.island.ui.settings.AppEntry
import dev.indla.island.ui.settings.components.GlassChip
import dev.indla.island.ui.settings.components.GlassToggleRow
import dev.indla.island.ui.settings.components.IslandPreview
import dev.indla.island.ui.settings.components.RowDivider
import dev.indla.island.ui.settings.components.SectionLabel
import dev.indla.island.ui.settings.components.Segmented
import dev.indla.island.ui.settings.components.Space
import dev.indla.island.ui.settings.components.TabScaffold
import dev.indla.island.ui.settings.components.glassSurface
import dev.indla.island.ui.settings.theme.LocalGlass

/** Accent swatches shared by the Apps and Look & feel tabs. */
val AccentSwatches = listOf(0xFF8AB4F8, 0xFF7EE0B5, 0xFFFFC66D, 0xFFFF8A80, 0xFFD7AEFB, 0xFF80DEEA, 0xFFFFFFFF)

private val IconSize = 36.dp

@Composable
fun AppsScreen(
    appearance: IslandAppearance,
    reduceTransparency: Boolean,
    apps: List<AppEntry>?,
    ruleFor: (String) -> AppRule,
    onSave: (AppRule) -> Unit,
    live: LiveSettings,
    onLive: ((LiveSettings) -> LiveSettings) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    val (shown, hidden) = filtered.partition { ruleFor(it.packageName).show }

    // Rows sit 1 dp apart so each section reads as one inset group; sections add their own space.
    TabScaffold(title = "Apps", pinned = { IslandPreview(appearance, reduceTransparency) }, itemSpacing = 1.dp) {
        item(key = "live") {
            LiveCard(live, onLive, Modifier.padding(bottom = Space.cardGap))
        }
        item(key = "search") {
            SearchField(query, { query = it }, Modifier.padding(bottom = Space.s))
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
        SectionLabel("$title · ${apps.size}", Modifier.padding(top = Space.m, bottom = Space.xs))
    }
    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
        val rule = ruleFor(app.packageName)
        val open = expanded == app.packageName
        Column(Modifier.animateItem().fillMaxWidth().glassSurface(groupShape(index, apps.size))) {
            AppRow(app, rule, onToggle = { onSave(rule.copy(show = it)) }, onClick = { onExpand(if (open) null else app.packageName) })
            AnimatedVisibility(open) { RuleEditor(rule, onSave) }
        }
    }
}

/** Live activities and system events: what may use the island besides app notifications. */
@Composable
private fun LiveCard(live: LiveSettings, onLive: ((LiveSettings) -> LiveSettings) -> Unit, modifier: Modifier = Modifier) {
    dev.indla.island.ui.settings.components.GlassCard(modifier, title = "Live activities") {
        FlowRow(
            Modifier.padding(horizontal = Space.rowPadding, vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            GlassChip("Music", { onLive { it.copy(media = !it.media) } }, Icons.Rounded.MusicNote, live.media)
            GlassChip("Calls", { onLive { it.copy(calls = !it.calls) } }, Icons.Rounded.Call, live.calls)
            GlassChip("Progress", { onLive { it.copy(progress = !it.progress) } }, Icons.Rounded.Download, live.progress)
            GlassChip("Charging", { onLive { it.copy(charging = !it.charging) } }, Icons.Rounded.Bolt, live.charging)
            GlassChip("Battery low", { onLive { it.copy(batteryLow = !it.batteryLow) } }, Icons.Rounded.BatteryAlert, live.batteryLow)
            GlassChip("Ringer", { onLive { it.copy(ringer = !it.ringer) } }, Icons.Rounded.NotificationsActive, live.ringer)
            GlassChip("Headphones", { onLive { it.copy(headphones = !it.headphones) } }, Icons.Rounded.Headphones, live.headphones)
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
            .padding(horizontal = Space.rowPadding, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(app.packageName, IconSize)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = glass.content, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            val details = buildList {
                if (rule.show) add(priorityLabel(rule.priority))
                if (rule.autoExpand) add("Expands")
                if (rule.hideContentOnLock) add("Private on lock")
                if (app.isMessaging) add("Chat")
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), color = glass.contentSecondary, fontSize = 13.sp, maxLines = 1)
            }
        }
        Switch(checked = rule.show, onCheckedChange = onToggle)
    }
}

@Composable
private fun RuleEditor(rule: AppRule, onSave: (AppRule) -> Unit) {
    val inset = Space.rowPadding + IconSize + Space.m
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

/** iOS-Settings-style inset group: only the first and last rows are fully rounded. */
private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val r = 24.dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) r else small,
        topEnd = if (index == 0) r else small,
        bottomStart = if (index == count - 1) r else small,
        bottomEnd = if (index == count - 1) r else small,
    )
}
