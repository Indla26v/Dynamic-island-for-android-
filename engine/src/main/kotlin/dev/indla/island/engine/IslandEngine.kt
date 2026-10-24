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
import dev.indla.island.model.LiveContent
import dev.indla.island.model.MediaCommand
import dev.indla.island.model.MediaSnapshot
import dev.indla.island.model.MessageLine
import dev.indla.island.model.Priority
import dev.indla.island.model.RawNotification
import dev.indla.island.model.ServiceHealth
import dev.indla.island.model.SystemEvent
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
 * Holds the only copy of island state. Notification sources, media sessions, system events,
 * gestures and the environment feed in; [state] comes out, and the overlay only renders it, so
 * two racing events can never draw two islands.
 *
 * Two kinds of items share one list:
 * - transient items (notifications, system events) pop and expire after the auto-hide time;
 * - live activities (calls, playing media, progress) stay while they're happening. They are
 *   parked, not dropped, while their own app is in front, and a swipe-up hides one only until
 *   it changes in a way that matters (a new track, a call answered).
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
    private var rulesLoaded = false
    private val pendingPosts = ArrayDeque<RawNotification>()
    private var env = Environment()

    private class Active(var item: IslandItem, var expiresAt: Long, val selfTest: Boolean) {
        val persistent: Boolean get() = expiresAt == FOREVER
    }

    /** Newest first. */
    private val active = mutableListOf<Active>()

    /** Live activities whose own app is in front: kept up to date, not shown. */
    private val parked = mutableListOf<Active>()

    /** Live keys the user swiped away -> the live hash they had then. */
    private val dismissedLive = HashMap<String, Int>()

    private var currentIndex = 0
    private var expanded = false
    private var replyOpen = false
    private var quickActionsOpen = false
    private var positionEditing = false
    private var expandedDeadline = 0L
    /** A finger is on an interactive part of the card (the music bar); don't collapse under it. */
    private var holding = false
    /** Size from the Look screen's slider while it is dragged, before it is saved. */
    private var previewScale: Float? = null
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
                reconcileParking()
                publish()
            }
        }
        scope.launch {
            rules.collect {
                this@IslandEngine.rules = it
                if (!rulesLoaded) {
                    // Until the user's rules are known, an app they turned off could pop.
                    rulesLoaded = true
                    while (pendingPosts.isNotEmpty()) onPosted(pendingPosts.removeFirst())
                }
            }
        }
    }

    val environment: Environment get() = env

    fun addRedactedMarkers(markers: Set<String>) {
        parser = NotificationParser(NotificationParser.DEFAULT_REDACTED_MARKERS + markers)
    }

    // ---- Notification source -----------------------------------------------------------------

    fun onPosted(raw: RawNotification) {
        if (!rulesLoaded) {
            if (pendingPosts.size >= MAX_PENDING) pendingPosts.removeFirst()
            pendingPosts.addLast(raw)
            return
        }
        val explicit = rules[raw.packageName]
        val parsed = when (val result = parser.parse(raw, explicit ?: AppRule(raw.packageName, show = true))) {
            is ParseResult.Show -> result.item
            is ParseResult.Skip -> {
                removeKey(raw.key)
                return
            }
        }
        if (!shows(parsed, explicit, raw.isSelfTest)) {
            removeKey(raw.key)
            return
        }
        val item = withAccent(parsed, explicit, raw.appAccentArgb)
        if (item.isLive) {
            seen[raw.key] = item.contentHash
            upsertLive(item, raw.isSelfTest, pop = true)
            return
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

        // A notification that used to be live (a finished download) is a normal post now.
        parked.removeAll { it.item.key == raw.key }
        val existing = active.indexOfFirst { it.item.key == raw.key }
        // A chat update whose newest message is the user's own (an inline reply echoed back by
        // the app) is never news, even though the content changed.
        val ownEcho = raw.messages.maxByOrNull { it.timestamp }?.let { it.sender == null } == true
        val silent = isSilent(raw, item.priority) || ownEcho
        if (existing >= 0 && !active[existing].persistent && (raw.onlyAlertOnce || silent)) {
            // Already on screen: refresh the content in place without popping again.
            active[existing].item = item
            publish()
            return
        }
        if (silent || !canPop(raw.packageName, item.priority, raw.isSelfTest)) {
            if (existing >= 0) removeAt(existing)
            publish()
            return
        }

        if (existing >= 0) removeAt(existing)
        insert(Active(item, clock() + autoHideMs(item), raw.isSelfTest), autoExpand = explicit?.autoExpand == true)
        publish()
    }

    /**
     * Notifications already in the shade when the listener connects: remember their content so
     * a later identical re-post doesn't pop, and bring back live activities (a running download,
     * a call in progress) without popping.
     */
    fun onSnapshot(raws: List<RawNotification>) {
        for (raw in raws) {
            val explicit = rules[raw.packageName]
            val item = (parser.parse(raw, explicit ?: AppRule(raw.packageName, show = true)) as? ParseResult.Show)?.item ?: continue
            seen[raw.key] = item.contentHash
            if (item.isLive && shows(item, explicit, raw.isSelfTest)) {
                upsertLive(withAccent(item, explicit, raw.appAccentArgb), raw.isSelfTest, pop = false)
            }
        }
    }

    fun onRemoved(key: String) {
        seen.remove(key)
        dismissedLive.remove(key)
        parked.removeAll { it.item.key == key }
        removeKey(key)
    }

    // ---- Media and system events -----------------------------------------------------------

    /** The active media session, or null when nothing is playing or paused any more. */
    fun onMedia(snapshot: MediaSnapshot?) {
        val key = snapshot?.let { mediaKey(it.packageName) }
        // One media activity at a time: the newest session replaces any other.
        val stale: (Active) -> Boolean = { it.item.kind == ItemKind.MEDIA && it.item.key != key }
        parked.removeAll(stale)
        removeWhere(stale)
        if (snapshot == null || key == null) {
            publish()
            return
        }
        val explicit = rules[snapshot.packageName]
        if (!settings.live.media || (explicit?.muted == true && !snapshot.isSelfTest)) {
            parked.removeAll { it.item.key == key }
            removeKey(key)
            publish()
            return
        }
        val media = snapshot.media
        val item = IslandItem(
            key = key,
            packageName = snapshot.packageName,
            appLabel = snapshot.appLabel,
            kind = ItemKind.MEDIA,
            title = media.track,
            glance = "",
            lines = listOfNotNull(media.artist?.let { MessageLine(null, it) }),
            actions = emptyList(),
            avatarKey = media.artKey,
            postTime = clock(),
            groupKey = null,
            isGroupSummary = false,
            priority = explicit?.priority ?: Priority.NORMAL,
            live = media,
        )
        upsertLive(withAccent(item, explicit, snapshot.appAccentArgb), snapshot.isSelfTest, pop = true)
    }

    /** Charging, battery low, ringer mode or headphones: a brief compact glance. */
    fun onSystemEvent(event: LiveContent.System) {
        if (!settings.enabled || !settings.live.allows(event.event) || !isVisible()) return
        val key = SYSTEM_KEY_PREFIX + systemGroup(event.event)
        val item = IslandItem(
            key = key,
            packageName = SYSTEM_PACKAGE,
            appLabel = "System",
            kind = ItemKind.SYSTEM,
            title = event.label,
            glance = event.label,
            lines = emptyList(),
            actions = emptyList(),
            avatarKey = null,
            postTime = clock(),
            groupKey = null,
            isGroupSummary = false,
            live = event,
        )
        val existing = active.indexOfFirst { it.item.key == key }
        if (existing >= 0) removeAt(existing)
        insert(Active(item, clock() + SYSTEM_EVENT_MS, selfTest = false), autoExpand = false)
        publish()
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
        } else if (item.isLive) {
            // Answer/hang up and friends: the activity updates itself when the app reacts.
            effects.invokeAction(item.key, action.index)
            collapse()
            publish()
        } else {
            effects.invokeAction(item.key, action.index)
            removeCurrent()
        }
    }

    fun onMediaCommand(command: MediaCommand) {
        val item = current()?.takeIf { it.kind == ItemKind.MEDIA } ?: return
        touch()
        effects.mediaCommand(item.packageName, command)
        publish()
    }

    fun onHold(down: Boolean) {
        if (holding == down) return
        holding = down
        touch()
        publish()
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
        parked.removeAll { it.item.packageName == item.packageName }
        collapse()
        publish()
    }

    fun onDismissCurrent() {
        val item = current() ?: return
        if (item.isLive) {
            hideLive(active[currentIndex])
            active.removeAt(currentIndex)
            currentIndex = 0
            collapse()
            publish()
        } else {
            effects.dismissNotification(item.key)
            removeCurrent()
        }
    }

    /** Tap outside an expanded card collapses it, like system UI. */
    fun onOutsideTouch() {
        if (!expanded) return
        collapse()
        publish()
    }

    /** Tap on a split-state bubble. */
    fun onSelectItem(index: Int) {
        if (index !in active.indices) return
        currentIndex = index
        replyOpen = false
        quickActionsOpen = false
        expanded = true
        touch()
        publish()
    }

    fun previewSize(scale: Float?) {
        if (previewScale == scale) return
        previewScale = scale
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
                !it.item.isLive && it.item.packageName == newEnv.foregroundPackage &&
                    !it.selfTest && it.item.priority != Priority.HIGH
            }
        }
        reconcileParking()
        publish()
    }

    fun setListenerConnected(connected: Boolean) = _health.update { it.copy(listenerConnected = connected) }

    fun setOverlayConnected(connected: Boolean) = _health.update { it.copy(overlayConnected = connected) }

    // ---- Internals ---------------------------------------------------------------------------

    private fun shows(item: IslandItem, explicit: AppRule?, selfTest: Boolean): Boolean {
        if (explicit?.muted == true && !selfTest) return false
        return when (item.kind) {
            // Live activities follow their own toggles, not the per-app notification switch.
            ItemKind.CALL -> settings.live.calls
            ItemKind.PROGRESS -> settings.live.progress
            ItemKind.TIMER -> settings.live.timers
            else -> explicit?.show ?: (selfTest || item.packageName in DefaultApps.messaging)
        }
    }

    private fun withAccent(item: IslandItem, explicit: AppRule?, appAccent: Long?): IslandItem =
        item.copy(accentArgb = explicit?.accentArgb ?: appAccent?.takeIf { settings.appearance.autoColour })

    private fun upsertLive(item: IslandItem, selfTest: Boolean, pop: Boolean) {
        val now = clock()
        val hash = liveHash(item)
        dismissedLive[item.key]?.let { hidden ->
            if (hidden == hash) return
            dismissedLive.remove(item.key)
        }
        val persistent = isPersistent(item)

        parked.firstOrNull { it.item.key == item.key }?.let { entry ->
            entry.item = item
            entry.expiresAt = if (persistent) FOREVER else now + LIVE_LINGER_MS
            return
        }

        val index = active.indexOfFirst { it.item.key == item.key }
        if (index >= 0) {
            val entry = active[index]
            val changed = liveHash(entry.item) != hash
            entry.item = item
            entry.expiresAt = when {
                persistent -> FOREVER
                entry.persistent -> now + LIVE_LINGER_MS // just paused: linger, then go
                else -> entry.expiresAt
            }
            if (pop && changed && persistent && !busy() && index != 0) {
                active.removeAt(index)
                active.add(0, entry)
                currentIndex = 0
            }
            reconcileParking()
            publish()
            return
        }

        // A paused session nobody saw start isn't news.
        if (!persistent) return
        val entry = Active(item, FOREVER, selfTest)
        if (pop && isVisible()) insert(entry, autoExpand = false) else active.add(entry)
        reconcileParking()
        publish()
    }

    private fun isPersistent(item: IslandItem) = when (val live = item.live) {
        is LiveContent.Media -> live.playing
        is LiveContent.Call, is LiveContent.Progress, is LiveContent.Timer -> true
        else -> false
    }

    /** What has to change for a live activity to be news again. */
    private fun liveHash(item: IslandItem) = listOf(item.kind, item.title, item.live?.stableKey).hashCode()

    private fun hideLive(entry: Active) {
        dismissedLive[entry.item.key] = liveHash(entry.item)
    }

    /** Park live activities of the app in front; bring back the rest. */
    private fun reconcileParking() {
        val fg = env.foregroundPackage
        fun shouldPark(a: Active) = settings.whenToShow.hideInForegroundApp && fg != null &&
            a.item.isLive && a.item.kind != ItemKind.SYSTEM && !a.selfTest && a.item.packageName == fg
        val toPark = active.filter(::shouldPark)
        if (toPark.isNotEmpty()) {
            removeWhere { it in toPark }
            parked += toPark
        }
        val toRestore = parked.filterNot(::shouldPark)
        if (toRestore.isNotEmpty()) {
            parked.removeAll(toRestore)
            active += toRestore
        }
    }

    private fun busy() = replyOpen || quickActionsOpen

    private fun insert(entry: Active, autoExpand: Boolean) {
        if (busy()) {
            // Don't yank the card away from someone who is typing; queue behind it.
            active.add((currentIndex + 1).coerceAtMost(active.size), entry)
        } else {
            active.add(0, entry)
            currentIndex = 0
            expanded = autoExpand
            if (expanded) expandedDeadline = clock() + EXPANDED_IDLE_MS
        }
        // Over the limit, drop the oldest transient item; live activities stay.
        while (active.size > MAX_ITEMS) {
            val victim = active.indexOfLast { !it.persistent }.takeIf { it > 0 } ?: break
            removeAt(victim)
        }
    }

    private fun perform(action: GestureAction, gesture: IslandGesture) {
        val item = current() ?: return
        when (action) {
            GestureAction.EXPAND -> {
                expanded = true
                quickActionsOpen = false
            }
            GestureAction.OPEN_APP -> {
                effects.openItem(item.key)
                if (item.isLive) collapse() else {
                    removeCurrent()
                    return
                }
            }
            GestureAction.HIDE -> {
                active.filter { it.persistent }.forEach(::hideLive)
                clearIsland()
            }
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

    private fun isSilent(raw: RawNotification, priority: Priority): Boolean {
        if (raw.isSelfTest) return false
        val r = raw.ranking
        val lowImportance = r.importance != Importance.UNSPECIFIED && r.importance < Importance.DEFAULT
        return priority == Priority.SILENT || lowImportance || r.isAmbient ||
            !r.matchesInterruptionFilter || r.isSuspended
    }

    private fun canPop(packageName: String, priority: Priority, selfTest: Boolean): Boolean {
        if (!isVisible()) return false
        val inForeground = env.foregroundPackage == packageName
        return !(inForeground && settings.whenToShow.hideInForegroundApp && priority != Priority.HIGH && !selfTest)
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
        active.getOrNull(currentIndex)?.takeIf { !it.persistent }?.let { it.expiresAt = now + autoHideMs(it.item) }
    }

    private fun collapse() {
        expanded = false
        holding = false
        replyOpen = false
        quickActionsOpen = false
        val now = clock()
        active.forEach { if (!it.persistent) it.expiresAt = maxOf(it.expiresAt, now + autoHideMs(it.item)) }
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
        if (item.kind == ItemKind.SYSTEM) return SYSTEM_EVENT_MS
        val base = settings.autoHideSeconds.coerceAtLeast(1) * 1000L
        return if (item.priority == Priority.HIGH) base * 2 else base
    }

    private fun lockRedacted(item: IslandItem): IslandItem =
        if (env.locked && item.hideContentOnLock && item.kind in REDACTABLE) {
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
            appearance = previewScale?.let { settings.appearance.copy(sizeScale = it) } ?: settings.appearance,
        )
        reschedule()
    }

    private fun reschedule() {
        timer?.cancel()
        timer = null
        if (active.isEmpty()) return
        val deadline = when {
            expanded && (replyOpen || holding) -> return
            expanded -> expandedDeadline
            else -> active.minOf { it.expiresAt }
        }
        if (deadline == FOREVER) return
        timer = scope.launch {
            delay((deadline - clock()).coerceAtLeast(0))
            timer = null
            onTimer()
        }
    }

    private fun onTimer() {
        val now = clock()
        if (expanded) {
            if (!replyOpen && !holding && now >= expandedDeadline) collapse()
        } else {
            removeWhere { it.expiresAt <= now }
        }
        publish()
    }

    companion object {
        const val EXPANDED_IDLE_MS = 8_000L
        const val SYSTEM_EVENT_MS = 3_000L
        /** How long paused media stays before it leaves the island. */
        const val LIVE_LINGER_MS = 10_000L
        const val MAX_ITEMS = 5
        const val SYSTEM_PACKAGE = "android"
        const val SYSTEM_KEY_PREFIX = "system:"
        private const val MAX_SEEN = 256
        private const val MAX_PENDING = 20
        private const val FOREVER = Long.MAX_VALUE
        private val REDACTABLE = setOf(ItemKind.MESSAGE, ItemKind.GENERIC, ItemKind.PROGRESS)

        fun mediaKey(packageName: String) = "media:$packageName"

        private fun systemGroup(event: SystemEvent) = when (event) {
            SystemEvent.CHARGING, SystemEvent.UNPLUGGED, SystemEvent.BATTERY_LOW -> "battery"
            SystemEvent.RINGER_NORMAL, SystemEvent.RINGER_VIBRATE, SystemEvent.RINGER_SILENT -> "ringer"
            SystemEvent.HEADPHONES_CONNECTED, SystemEvent.HEADPHONES_DISCONNECTED -> "headphones"
        }
    }
}
