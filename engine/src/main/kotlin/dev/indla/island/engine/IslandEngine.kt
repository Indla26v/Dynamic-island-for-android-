package dev.indla.island.engine

import dev.indla.island.model.AppRule
import dev.indla.island.model.DefaultApps
import dev.indla.island.model.Environment
import dev.indla.island.model.GestureAction
import dev.indla.island.model.Importance
import dev.indla.island.model.IslandGesture
import dev.indla.island.model.IslandItem
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandSettings
import dev.indla.island.model.IslandUiState
import dev.indla.island.model.ItemKind
import dev.indla.island.model.MessageLine
import dev.indla.island.model.Priority
import dev.indla.island.model.RawNotification
import dev.indla.island.model.ServiceHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the only copy of island state. Notification sources, gestures and the environment feed
 * in; [state] comes out, and the overlay only renders it, so two racing notifications can never
 * draw two islands.
 *
 * Not thread-safe by design: call it from [scope]'s dispatcher (the main thread in the app).
 */
class IslandEngine(
    private val scope: CoroutineScope,
    settings: Flow<IslandSettings>,
    rules: Flow<Map<String, AppRule>>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var effects: IslandEffects = IslandEffects.None

    private val _state = MutableStateFlow(IslandUiState())
    val state: StateFlow<IslandUiState> = _state.asStateFlow()

    private val _health = MutableStateFlow(ServiceHealth())
    val health: StateFlow<ServiceHealth> = _health.asStateFlow()

    private var parser = NotificationParser()
    private var settings = IslandSettings()
    private var rules: Map<String, AppRule> = emptyMap()
    private var env = Environment()

    private class Active(var item: IslandItem, var expiresAt: Long, val selfTest: Boolean)

    /** Newest first. */
    private val active = mutableListOf<Active>()
    private var currentIndex = 0
    private var expanded = false
    private var replyOpen = false
    private var quickActionsOpen = false
    private var positionEditing = false
    private var expandedDeadline = 0L
    private var timer: Job? = null

    /** key -> content hash of everything posted and not yet removed; bounded. */
    private val seen = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > MAX_SEEN
    }

    init {
        scope.launch {
            settings.collect {
                this@IslandEngine.settings = it
                if (!it.enabled) clearIsland()
                publish()
            }
        }
        scope.launch { rules.collect { this@IslandEngine.rules = it } }
    }

    val environment: Environment get() = env

    fun addRedactedMarkers(markers: Set<String>) {
        parser = NotificationParser(NotificationParser.DEFAULT_REDACTED_MARKERS + markers)
    }

    // ---- Notification source -----------------------------------------------------------------

    fun onPosted(raw: RawNotification) {
        val rule = rules[raw.packageName]
            ?: if (raw.isSelfTest) AppRule(raw.packageName, show = true) else DefaultApps.defaultRule(raw.packageName)
        if (!rule.show && !raw.isSelfTest) {
            removeKey(raw.key)
            return
        }
        val item = when (val result = parser.parse(raw, rule)) {
            is ParseResult.Show -> result.item
            is ParseResult.Skip -> {
                removeKey(raw.key)
                return
            }
        }

        // Same key + same content hash is the same notification: ignore it. This also drops
        // FLAG_ONLY_ALERT_ONCE re-posts whose content didn't change.
        val previousHash = seen.put(raw.key, item.contentHash)
        if (previousHash == item.contentHash) return

        // Summaries never pop while their children are there; a child replaces its summary.
        if (item.isGroupSummary && item.groupKey != null) {
            val childShown = active.any { it.item.groupKey == item.groupKey && !it.item.isGroupSummary }
            if (raw.groupHasChildren || childShown) return
        } else if (item.groupKey != null) {
            removeWhere { it.item.isGroupSummary && it.item.groupKey == item.groupKey }
        }

        val existing = active.indexOfFirst { it.item.key == raw.key }
        // A chat update whose newest message is the user's own (an inline reply echoed back by
        // the app) is never news, even though the content changed.
        val ownEcho = raw.messages.maxByOrNull { it.timestamp }?.let { it.sender == null } == true
        val silent = isSilent(raw, rule) || ownEcho
        if (existing >= 0 && (raw.onlyAlertOnce || silent)) {
            // Already on screen: refresh the content in place without popping again.
            active[existing].item = item
            publish()
            return
        }
        if (silent || !canPop(raw, rule)) {
            if (existing >= 0) active[existing].item = item
            publish()
            return
        }

        if (existing >= 0) removeAt(existing)
        val now = clock()
        val entry = Active(item, now + autoHideMs(item), raw.isSelfTest)
        if (replyOpen || quickActionsOpen) {
            // Don't yank the card away from someone who is typing; queue behind it.
            active.add((currentIndex + 1).coerceAtMost(active.size), entry)
        } else {
            active.add(0, entry)
            currentIndex = 0
            expanded = rule.autoExpand
            if (expanded) expandedDeadline = now + EXPANDED_IDLE_MS
        }
        while (active.size > MAX_ITEMS) active.removeAt(active.lastIndex)
        publish()
    }

    /**
     * Notifications already in the shade when the listener connects: remember their content so
     * a later identical re-post doesn't pop, but show nothing now.
     */
    fun onSnapshot(raws: List<RawNotification>) {
        for (raw in raws) {
            val rule = rules[raw.packageName] ?: DefaultApps.defaultRule(raw.packageName)
            val result = parser.parse(raw, rule) as? ParseResult.Show ?: continue
            seen[raw.key] = result.item.contentHash
        }
    }

    fun onRemoved(key: String) {
        seen.remove(key)
        removeKey(key)
    }

    // ---- User input ------------------------------------------------------------------------

    fun onGesture(gesture: IslandGesture) {
        if (active.isEmpty()) return
        touch()
        perform(settings.gestures.resolve(gesture, expanded), gesture)
    }

    fun onActionTapped(actionIndex: Int) {
        val item = current() ?: return
        val action = item.actions.firstOrNull { it.index == actionIndex } ?: return
        touch()
        if (action.isReply) {
            expanded = true
            replyOpen = true
            quickActionsOpen = false
            publish()
        } else {
            effects.invokeAction(item.key, action.index)
            removeCurrent()
        }
    }

    fun onReplySent(text: String) {
        val item = current() ?: return
        val action = item.replyAction ?: return
        if (!replyOpen || text.isBlank()) return
        effects.reply(item.key, action.index, text.trim())
        removeCurrent()
    }

    fun onReplyCancelled() {
        if (!replyOpen) return
        replyOpen = false
        touch()
        publish()
    }

    fun onMuteApp() {
        val item = current() ?: return
        effects.muteApp(item.packageName)
        removeWhere { it.item.packageName == item.packageName }
        collapse()
        publish()
    }

    fun onDismissCurrent() {
        val item = current() ?: return
        effects.dismissNotification(item.key)
        removeCurrent()
    }

    /** Tap outside an expanded card collapses it, like system UI. */
    fun onOutsideTouch() {
        if (!expanded) return
        collapse()
        publish()
    }

    /** Tap on the split-state bubble. */
    fun onSelectItem(index: Int) {
        if (index !in active.indices) return
        currentIndex = index
        replyOpen = false
        quickActionsOpen = false
        expanded = true
        touch()
        publish()
    }

    fun setPositionEditing(editing: Boolean) {
        if (positionEditing == editing) return
        positionEditing = editing
        publish()
    }

    // ---- Environment and health --------------------------------------------------------------

    fun updateEnvironment(newEnv: Environment) {
        if (newEnv == env) return
        val foregroundChanged = newEnv.foregroundPackage != env.foregroundPackage
        env = newEnv
        if (foregroundChanged && settings.whenToShow.hideInForegroundApp && newEnv.foregroundPackage != null) {
            // The user is in that app now; the island has nothing new to tell them about it.
            removeWhere {
                it.item.packageName == newEnv.foregroundPackage && !it.selfTest && it.item.priority != Priority.HIGH
            }
        }
        publish()
    }

    fun setListenerConnected(connected: Boolean) = _health.update { it.copy(listenerConnected = connected) }

    fun setOverlayConnected(connected: Boolean) = _health.update { it.copy(overlayConnected = connected) }

    // ---- Internals ---------------------------------------------------------------------------

    private fun perform(action: GestureAction, gesture: IslandGesture) {
        val item = current() ?: return
        when (action) {
            GestureAction.EXPAND -> {
                expanded = true
                quickActionsOpen = false
            }
            GestureAction.OPEN_APP -> {
                effects.openItem(item.key)
                removeCurrent()
                return
            }
            GestureAction.HIDE -> clearIsland()
            GestureAction.COLLAPSE -> collapse()
            GestureAction.SWITCH_ITEM -> if (active.size > 1) {
                val step = if (gesture == IslandGesture.SWIPE_RIGHT) -1 else 1
                currentIndex = (currentIndex + step).mod(active.size)
                replyOpen = false
                quickActionsOpen = false
            }
            GestureAction.QUICK_ACTIONS -> {
                expanded = true
                quickActionsOpen = true
                replyOpen = false
            }
            GestureAction.NONE -> return
        }
        touch()
        publish()
    }

    private fun isSilent(raw: RawNotification, rule: AppRule): Boolean {
        if (raw.isSelfTest) return false
        val r = raw.ranking
        val lowImportance = r.importance != Importance.UNSPECIFIED && r.importance < Importance.DEFAULT
        return rule.priority == Priority.SILENT || lowImportance || r.isAmbient ||
            !r.matchesInterruptionFilter || r.isSuspended
    }

    private fun canPop(raw: RawNotification, rule: AppRule): Boolean {
        if (!isVisible()) return false
        val inForeground = env.foregroundPackage == raw.packageName
        return !(inForeground && settings.whenToShow.hideInForegroundApp && rule.priority != Priority.HIGH && !raw.isSelfTest)
    }

    private fun isVisible(): Boolean {
        if (!settings.enabled) return false
        if (positionEditing) return true
        val w = settings.whenToShow
        return !(env.landscape && w.hideInLandscape) &&
            !(env.fullscreen && w.hideInFullscreen) &&
            !(env.shadeOpen && w.hideWhenShadeOpen) &&
            !(env.locked && !w.showOnLockscreen)
    }

    private fun current(): IslandItem? = active.getOrNull(currentIndex)?.item

    private fun touch() {
        val now = clock()
        expandedDeadline = now + EXPANDED_IDLE_MS
        active.getOrNull(currentIndex)?.let { it.expiresAt = now + autoHideMs(it.item) }
    }

    private fun collapse() {
        expanded = false
        replyOpen = false
        quickActionsOpen = false
        val now = clock()
        active.forEach { it.expiresAt = maxOf(it.expiresAt, now + autoHideMs(it.item)) }
    }

    private fun clearIsland() {
        active.clear()
        currentIndex = 0
        expanded = false
        replyOpen = false
        quickActionsOpen = false
    }

    private fun removeCurrent() {
        if (currentIndex in active.indices) active.removeAt(currentIndex)
        currentIndex = 0
        // Whatever queued up while the user was busy gets a fresh turn on screen.
        collapse()
        publish()
    }

    private fun removeKey(key: String) {
        val index = active.indexOfFirst { it.item.key == key }
        if (index < 0) return
        removeAt(index)
        publish()
    }

    private fun removeAt(index: Int) {
        active.removeAt(index)
        if (index == currentIndex) {
            replyOpen = false
            quickActionsOpen = false
            expanded = false
        }
        if (index < currentIndex) currentIndex--
    }

    private fun removeWhere(predicate: (Active) -> Boolean) {
        val currentEntry = active.getOrNull(currentIndex)
        active.removeAll(predicate)
        if (currentEntry != null && currentEntry !in active) {
            currentIndex = 0
            expanded = false
            replyOpen = false
            quickActionsOpen = false
        } else if (currentEntry != null) {
            currentIndex = active.indexOf(currentEntry)
        }
    }

    private fun autoHideMs(item: IslandItem): Long {
        val base = settings.autoHideSeconds.coerceAtLeast(1) * 1000L
        return if (item.priority == Priority.HIGH) base * 2 else base
    }

    private fun lockRedacted(item: IslandItem): IslandItem =
        if (env.locked && item.hideContentOnLock && item.kind != ItemKind.HIDDEN_BY_ANDROID) {
            item.copy(
                kind = ItemKind.HIDDEN_ON_LOCKSCREEN,
                title = item.appLabel,
                glance = "New",
                lines = listOf(MessageLine(sender = null, text = "Unlock to see")),
                actions = emptyList(),
                avatarKey = null,
            )
        } else {
            item
        }

    private fun publish() {
        if (active.isEmpty()) {
            currentIndex = 0
            expanded = false
            replyOpen = false
            quickActionsOpen = false
        }
        currentIndex = currentIndex.coerceIn(0, (active.size - 1).coerceAtLeast(0))
        val mode = when {
            active.isEmpty() -> IslandMode.REST
            expanded -> IslandMode.EXPANDED
            active.size > 1 -> IslandMode.SPLIT
            else -> IslandMode.COMPACT
        }
        _state.value = IslandUiState(
            mode = mode,
            visible = isVisible(),
            items = active.map { lockRedacted(it.item) },
            currentIndex = currentIndex,
            replyOpen = replyOpen,
            quickActionsOpen = quickActionsOpen,
            positionEditing = positionEditing,
            appearance = settings.appearance,
        )
        reschedule()
    }

    private fun reschedule() {
        timer?.cancel()
        timer = null
        if (active.isEmpty()) return
        val deadline = when {
            expanded && replyOpen -> return
            expanded -> expandedDeadline
            else -> active.minOf { it.expiresAt }
        }
        timer = scope.launch {
            delay((deadline - clock()).coerceAtLeast(0))
            timer = null
            onTimer()
        }
    }

    private fun onTimer() {
        val now = clock()
        if (expanded) {
            if (!replyOpen && now >= expandedDeadline) collapse()
        } else {
            removeWhere { it.expiresAt <= now }
        }
        publish()
    }

    companion object {
        const val EXPANDED_IDLE_MS = 8_000L
        const val MAX_ITEMS = 5
        private const val MAX_SEEN = 256
    }
}
