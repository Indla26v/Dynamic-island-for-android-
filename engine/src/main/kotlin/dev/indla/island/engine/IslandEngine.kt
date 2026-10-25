package dev.indla.island.engine

import dev.indla.island.model.ActionRole
import dev.indla.island.model.AppRule
import dev.indla.island.model.DefaultApps
import dev.indla.island.model.Environment
import dev.indla.island.model.GestureAction
import dev.indla.island.model.Importance
import dev.indla.island.model.IslandAction
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
import dev.indla.island.model.StackInfo
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
 * - transient items (notifications, system events) pop and expire after the auto-hide time.
 *   One app's notifications stack into a single entry whose pages a swipe moves through, and
 *   the ones that leave unopened gather in a "missed" pile until they are looked at or read;
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

    private class Active(item: IslandItem, var expiresAt: Long, val selfTest: Boolean, val missed: Boolean = false) {
        /** Oldest first. One page, unless notifications stacked. */
        val pages = mutableListOf(item)
        var page = 0
        /** Pages the user looked at in the expanded card: those aren't missed. */
        val viewed = HashSet<String>()
        var item: IslandItem
            get() = pages[page]
            set(value) {
                pages[page] = value
            }
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
    /** System events of the last few seconds, to tell causes from their side effects. */
    private val recentSystem = ArrayDeque<Pair<SystemEvent, Long>>()

    /** Set by a page turn for the one state it produces. */
    private var pageTurn = 0
    /** Digital Wellbeing's "Bedtime mode is on" notification, while it's up. */
    private var bedtimeKey: String? = null
    /** Link to Windows' ongoing "connected" notification, while it's up. */
    private var linkKey: String? = null

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
        if (isBedtime(raw)) {
            // Digital Wellbeing says bedtime started (often on its schedule): a glance, not a card.
            if (bedtimeKey != raw.key) {
                bedtimeKey = raw.key
                onSystemEvent(LiveContent.System(SystemEvent.BEDTIME_ON, null, raw.text?.trim()?.takeIf { it.isNotEmpty() } ?: "On"))
            }
            return
        }
        if (isLinkToWindows(raw)) {
            // Link to Windows says it's connected to a PC: a glance, not a card.
            if (linkKey != raw.key) {
                linkKey = raw.key
                onSystemEvent(LiveContent.System(SystemEvent.LINK_ON, null, raw.text?.trim()?.takeIf { it.isNotEmpty() } ?: "Connected"))
            }
            return
        }
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
            val childShown = active.any { e -> e.pages.any { it.groupKey == item.groupKey && !it.isGroupSummary } }
            if (raw.groupHasChildren || childShown) return
        } else if (item.groupKey != null) {
            removePages { it.isGroupSummary && it.groupKey == item.groupKey }
        }

        // A notification that used to be live (a finished download) is a normal post now.
        parked.removeAll { it.item.key == raw.key }
        val holder = active.firstOrNull { e -> !e.missed && e.pages.any { it.key == raw.key } }
        // A chat update whose newest message is the user's own (an inline reply echoed back by
        // the app) is never news, even though the content changed.
        val ownEcho = raw.messages.maxByOrNull { it.timestamp }?.let { it.sender == null } == true
        val silent = isSilent(raw, item.priority) || ownEcho
        if (holder != null && !holder.persistent && (raw.onlyAlertOnce || silent)) {
            // Already on screen: refresh the content in place without popping again.
            holder.pages[holder.pages.indexOfFirst { it.key == raw.key }] = item
            publish()
            return
        }
        if (silent || !canPop(raw.packageName, item.priority, raw.isSelfTest)) {
            removePages({ !it.missed }) { it.key == raw.key }
            val pile = missedPile()
            val inPile = pile?.pages?.indexOfFirst { it.key == raw.key } ?: -1
            when {
                // Hidden (full screen, locked): it'll be waiting in the pile afterwards.
                !silent && missable(raw.packageName) -> addMissed(listOf(item))
                inPile >= 0 -> pile!!.pages[inPile] = item
            }
            publish()
            return
        }

        // Popping now: no longer missed, and it joins its app's stack as the newest page.
        removePages { it.key == raw.key }
        val autoExpand = explicit?.autoExpand == true
        val stack = if (settings.whenToShow.stackByApp) {
            active.firstOrNull { !it.missed && !it.persistent && !it.item.isLive && it.item.kind != ItemKind.SYSTEM && it.item.packageName == item.packageName }
        } else {
            null
        }
        when {
            stack == null -> insert(Active(item, clock() + autoHideMs(item), raw.isSelfTest), autoExpand)
            // Someone is typing on this stack: add the page without turning it under them.
            busy() && stack === active.getOrNull(currentIndex) -> stack.pages += item
            // Reading this stack: stay open and turn to the new message.
            expanded && stack === active.getOrNull(currentIndex) -> {
                stack.pages += item
                stack.page = stack.pages.lastIndex
                stack.expiresAt = clock() + autoHideMs(item)
                expandedDeadline = clock() + EXPANDED_IDLE_MS
            }
            else -> {
                stack.pages += item
                stack.page = stack.pages.lastIndex
                stack.expiresAt = clock() + autoHideMs(item)
                removeAt(active.indexOf(stack))
                insert(stack, autoExpand)
            }
        }
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
        if (key == linkKey) {
            linkKey = null
            onSystemEvent(LiveContent.System(SystemEvent.LINK_OFF, null, "Disconnected"))
            return
        }
        if (key == bedtimeKey) {
            bedtimeKey = null
            onSystemEvent(LiveContent.System(SystemEvent.BEDTIME_OFF, null, "Off"))
            return
        }
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

    /** A phone state change (charging, ringer, modes, headphones, unlock): a brief glance. */
    fun onSystemEvent(event: LiveContent.System) {
        if (isConsequence(event.event)) return
        // Mode switches mostly happen in quick settings, so these glances show over the shade.
        if (!settings.enabled || !settings.live.allows(event.event) || !isVisible(overShade = true)) return
        val key = SYSTEM_KEY_PREFIX + systemGroup(event.event)
        val item = IslandItem(
            key = key,
            packageName = SYSTEM_PACKAGE,
            appLabel = "System",
            kind = ItemKind.SYSTEM,
            title = event.label,
            glance = event.label,
            lines = emptyList(),
            avatarKey = null,
            postTime = clock(),
            groupKey = null,
            isGroupSummary = false,
            live = event,
            actions = if (event.event == SystemEvent.FLASHLIGHT_ON) {
                listOf(IslandAction(ActionRole.TORCH_OFF_INDEX, "Turn off", false, ActionRole.STOP))
            } else {
                emptyList()
            },
        )
        val existing = active.indexOfFirst { it.item.key == key }
        // The same event with news (a pair's battery arriving after it connected): update the
        // card where it is, without popping it again.
        if (existing >= 0 && (active[existing].item.live as? LiveContent.System)?.stableKey == event.stableKey) {
            active[existing].item = item
            publish()
            return
        }
        if (existing >= 0) removeAt(existing)
        // Headphones connecting open up briefly to show the pair and its battery, then leave.
        // Opened by default: the pair and its battery, the plane flying in, the moon swinging in.
        val showcase = true
        // Unlocking happens many times a day: open, but only briefly.
        val shown = if (event.event == SystemEvent.UNLOCKED || event.event == SystemEvent.AUTH_FAILED) BRIEF_SHOWCASE_MS else SHOWCASE_MS
        // The flashlight stays on the island, with a button to turn it off, until it's off.
        // Open for [shown], then it settles into the pill for a moment before leaving, so a glance
        // always collapses instead of vanishing from the open card.
        val expires = if (event.event == SystemEvent.FLASHLIGHT_ON) FOREVER else clock() + shown + COMPACT_TAIL_MS
        insert(Active(item, expires, selfTest = false), autoExpand = showcase && !busy())
        if (showcase && expanded) expandedDeadline = clock() + shown
        publish()
    }

    // ---- User input ------------------------------------------------------------------------

    fun onGesture(gesture: IslandGesture) {
        if (active.isEmpty()) return
        touch()
        if (turnPage(gesture)) return
        perform(settings.gestures.resolve(gesture, expanded), gesture)
    }

    /**
     * A stack is a deck: swiping up takes the next card, swiping down brings the last one back.
     * A fresh stack starts on its newest card and goes back in time; the missed pile starts on
     * its oldest and goes forward. Past either end the swipe does what it normally does
     * (collapse, hide, expand), so nothing is lost.
     */
    private fun turnPage(gesture: IslandGesture): Boolean {
        if (gesture != IslandGesture.SWIPE_UP && gesture != IslandGesture.SWIPE_DOWN) return false
        val entry = active.getOrNull(currentIndex) ?: return false
        if (entry.pages.size < 2 || busy()) return false
        val deck = if (entry.missed) 1 else -1
        val page = entry.page + if (gesture == IslandGesture.SWIPE_UP) deck else -deck
        if (page !in entry.pages.indices) return false
        entry.page = page
        pageTurn = if (gesture == IslandGesture.SWIPE_UP) 1 else -1
        publish()
        return true
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
        } else if (action.role == ActionRole.PAUSE || action.role == ActionRole.RESUME) {
            // Play/pause keeps the card open, so the change can be seen.
            effects.invokeAction(item.key, action.index)
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

    /**
     * One switch flipping others (bedtime -> Do Not Disturb, silent, warm screen) shows only the
     * cause. Returns true when [event] is a side effect of something that just happened; a cause
     * arriving after its side effects clears them away. Causes count even when their own glance
     * is switched off.
     */
    private fun isConsequence(event: SystemEvent): Boolean {
        val now = clock()
        recentSystem.removeAll { now - it.second > GlanceCascade.WINDOW_MS }
        if (GlanceCascade.explainedBy(event, recentSystem.map { it.first })) return true
        removeWhere { entry ->
            val shown = (entry.item.live as? LiveContent.System)?.event
            shown != null && now - entry.item.postTime <= GlanceCascade.WINDOW_MS &&
                GlanceCascade.explainedBy(shown, listOf(event))
        }
        recentSystem.addLast(event to now)
        return false
    }

    /** Opens the shown item's app, whatever the tap gesture is mapped to. */
    fun onOpenCurrent() {
        if (active.isEmpty()) return
        touch()
        perform(GestureAction.OPEN_APP, IslandGesture.TAP)
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
        removePages { it.packageName == item.packageName }
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
        openStack(active[index])
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
            removePages({ !it.selfTest }) {
                !it.isLive && it.packageName == newEnv.foregroundPackage && it.priority != Priority.HIGH
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
            entry.item = holdPausedTime(entry.item, item)
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

    /**
     * Pausing stops an app's chronometer, and some views then read 0:00. The island holds the
     * time it was showing at the moment of the pause instead.
     */
    private fun holdPausedTime(old: IslandItem, new: IslandItem): IslandItem {
        val was = old.live as? LiveContent.Timer ?: return new
        val now = new.live as? LiveContent.Timer ?: return new
        if (now.baseMs != null) return new
        val base = was.baseMs
        val held = when {
            base != null -> clockText(if (was.countDown) base - clock() else clock() - base)
            ZERO_TIME.matches(now.staticTime) && !ZERO_TIME.matches(was.staticTime) -> was.staticTime
            else -> return new
        }
        return new.copy(glance = held, live = now.copy(staticTime = held))
    }

    private fun clockText(ms: Long): String {
        val total = (kotlin.math.abs(ms) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        return if (ms < 0) "-$text" else text
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
        // Over the limit, the oldest transient item makes room (into the missed pile); live
        // activities and the pile stay.
        while (active.size > MAX_ITEMS) {
            val victim = active.indexOfLast { !it.persistent }.takeIf { it > 0 } ?: break
            val gone = active[victim]
            removeAt(victim)
            retire(gone)
        }
    }

    /** The pile of notifications that left the island unopened. */
    private fun missedPile(): Active? = active.firstOrNull { it.missed }

    /** Whether a notification that couldn't pop will still be news later. */
    private fun missable(packageName: String) = settings.enabled && settings.whenToShow.keepMissed &&
        !env.shadeOpen && env.foregroundPackage != packageName

    /** An entry leaving the island: whatever nobody looked at goes to the missed pile. */
    private fun retire(entry: Active) {
        if (entry.missed || entry.item.isLive) return
        addMissed(entry.pages.filter { it.key !in entry.viewed })
    }

    private fun addMissed(items: List<IslandItem>) {
        if (!settings.whenToShow.keepMissed) return
        val fresh = items.filter { !it.isLive && it.kind != ItemKind.SYSTEM }
        if (fresh.isEmpty()) return
        val pile = missedPile() ?: Active(fresh.first(), FOREVER, selfTest = false, missed = true).also {
            it.pages.clear()
            active.add(it)
        }
        for (item in fresh) {
            pile.pages.removeAll { it.key == item.key }
            pile.pages += item
        }
        while (pile.pages.size > MAX_MISSED) pile.pages.removeAt(0)
        // Don't turn the page under someone reading the pile.
        if (!(expanded && pile === active.getOrNull(currentIndex))) pile.page = pile.pages.lastIndex
    }

    /** Opening the pile starts at the oldest missed notification; a swipe goes to newer ones. */
    private fun openStack(entry: Active) {
        if (entry.missed && !expanded) entry.page = 0
    }

    /** Removes matching pages from entries passing [entryFilter]; emptied entries go. */
    private fun removePages(entryFilter: (Active) -> Boolean = { true }, predicate: (IslandItem) -> Boolean) {
        for (e in active) {
            if (!entryFilter(e) || e.pages.none(predicate)) continue
            val shown = e.item
            e.pages.removeAll(predicate)
            e.viewed.retainAll(e.pages.mapTo(HashSet()) { it.key })
            val i = e.pages.indexOf(shown)
            e.page = if (i >= 0) i else e.page.coerceAtMost(e.pages.lastIndex).coerceAtLeast(0)
        }
        removeWhere { it.pages.isEmpty() }
    }

    private fun perform(action: GestureAction, gesture: IslandGesture) {
        val item = current() ?: return
        when (action) {
            GestureAction.EXPAND -> {
                openStack(active[currentIndex])
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
                active.filter { it.persistent && it.item.isLive }.forEach(::hideLive)
                clearIsland()
            }
            GestureAction.COLLAPSE -> collapse()
            GestureAction.SWITCH_ITEM -> {
                // Sideways moves between items (music, a call, a chat); pages are vertical.
                if (active.size < 2) return
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

    /** @param overShade ignore an open shade (system glances show over it). */
    private fun isVisible(overShade: Boolean = false): Boolean {
        if (!settings.enabled) return false
        if (positionEditing) return true
        val w = settings.whenToShow
        return !(env.landscape && w.hideInLandscape) &&
            !(env.fullscreen && w.hideInFullscreen) &&
            !(env.shadeOpen && w.hideWhenShadeOpen && !overShade) &&
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
        // A system glance keeps its own short life; collapsing doesn't buy it more.
        active.forEach { if (!it.persistent && it.item.kind != ItemKind.SYSTEM) it.expiresAt = maxOf(it.expiresAt, now + autoHideMs(it.item)) }
        // What the user paged through in the pile has been seen.
        missedPile()?.let { pile ->
            pile.pages.removeAll { it.key in pile.viewed }
            pile.viewed.clear()
            pile.page = pile.pages.lastIndex.coerceAtLeast(0)
            if (pile.pages.isEmpty()) removeWhere { it === pile }
        }
    }

    private fun clearIsland() {
        active.clear()
        currentIndex = 0
        expanded = false
        replyOpen = false
        quickActionsOpen = false
    }

    /** Removes the page on screen (opened, replied, dismissed); the rest of its stack stays. */
    private fun removeCurrent() {
        active.getOrNull(currentIndex)?.let { e ->
            e.viewed.remove(e.item.key)
            e.pages.removeAt(e.page)
            e.page = e.page.coerceAtMost(e.pages.lastIndex).coerceAtLeast(0)
            if (e.pages.isEmpty()) active.removeAt(currentIndex)
        }
        currentIndex = 0
        // Whatever queued up while the user was busy gets a fresh turn on screen.
        collapse()
        publish()
    }

    private fun removeKey(key: String) {
        if (active.none { e -> e.pages.any { it.key == key } }) return
        removePages { it.key == key }
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
        if (expanded) active.getOrNull(currentIndex)?.let { it.viewed += it.item.key }
        val mode = when {
            active.isEmpty() -> IslandMode.REST
            expanded -> IslandMode.EXPANDED
            active.size > 1 -> IslandMode.SPLIT
            else -> IslandMode.COMPACT
        }
        _state.value = IslandUiState(
            mode = mode,
            // A glance shows over the shade; a lasting one (the torch) only while it's opened up.
            visible = isVisible(overShade = current()?.kind == ItemKind.SYSTEM && (expanded || active.getOrNull(currentIndex)?.persistent == false)),
            items = active.map { lockRedacted(withStack(it)) },
            currentIndex = currentIndex,
            replyOpen = replyOpen,
            quickActionsOpen = quickActionsOpen,
            positionEditing = positionEditing,
            pageTurn = pageTurn,
            appearance = previewScale?.let { settings.appearance.copy(sizeScale = it) } ?: settings.appearance,
        )
        pageTurn = 0
        reschedule()
    }

    private fun withStack(e: Active): IslandItem {
        if (e.pages.size < 2 && !e.missed) return e.item
        val others = if (e.missed) {
            e.pages.asReversed().map { it.packageName }.filter { it != e.item.packageName }.distinct().take(3)
        } else {
            emptyList()
        }
        return e.item.copy(stack = StackInfo(e.pages.size, e.page, e.missed, others))
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
            val expired = active.filter { it.expiresAt <= now }
            removeWhere { it in expired }
            expired.forEach(::retire)
        }
        publish()
    }

    companion object {
        const val EXPANDED_IDLE_MS = 4_000L
        const val SYSTEM_EVENT_MS = 3_000L
        /** How long the headphones card stays open. */
        const val SHOWCASE_MS = 3_500L
        const val BRIEF_SHOWCASE_MS = 2_500L
        /** How long a glance stays as the pill after its card closes. */
        const val COMPACT_TAIL_MS = 2_500L
        /** How long paused media stays before it leaves the island. */
        const val LIVE_LINGER_MS = 10_000L
        const val MAX_ITEMS = 5
        const val MAX_MISSED = 20
        const val SYSTEM_PACKAGE = "android"
        const val SYSTEM_KEY_PREFIX = "system:"
        private const val MAX_SEEN = 256
        private const val MAX_PENDING = 20
        private const val FOREVER = Long.MAX_VALUE
        private val ZERO_TIME = Regex("^[0:]*$")
        private val REDACTABLE = setOf(ItemKind.MESSAGE, ItemKind.GENERIC, ItemKind.PROGRESS)

        fun mediaKey(packageName: String) = "media:$packageName"

        private const val WELLBEING = "com.google.android.apps.wellbeing"
        private val BEDTIME = Regex("(?i)bedtime")

        private const val LINK_TO_WINDOWS = "com.microsoft.appmanager"
        private val CONNECTED = Regex("(?i)connected|linked|syncing")

        private fun isLinkToWindows(raw: RawNotification) = raw.packageName == LINK_TO_WINDOWS && raw.ongoing &&
            listOfNotNull(raw.title, raw.text).any { CONNECTED.containsMatchIn(it) }

        private fun isBedtime(raw: RawNotification) = raw.packageName == WELLBEING && raw.ongoing &&
            listOfNotNull(raw.title, raw.text).any { BEDTIME.containsMatchIn(it) }

        private fun systemGroup(event: SystemEvent) = when (event) {
            SystemEvent.CHARGING, SystemEvent.UNPLUGGED, SystemEvent.BATTERY_LOW -> "battery"
            SystemEvent.RINGER_NORMAL, SystemEvent.RINGER_VIBRATE, SystemEvent.RINGER_SILENT -> "ringer"
            SystemEvent.HEADPHONES_CONNECTED, SystemEvent.HEADPHONES_DISCONNECTED -> "headphones"
            SystemEvent.AIRPLANE_ON, SystemEvent.AIRPLANE_OFF -> "airplane"
            SystemEvent.FLASHLIGHT_ON, SystemEvent.FLASHLIGHT_OFF -> "flashlight"
            SystemEvent.SAVER_ON, SystemEvent.SAVER_OFF -> "saver"
            SystemEvent.MONSTER_ON, SystemEvent.MONSTER_OFF -> "monster"
            SystemEvent.DARK_MODE, SystemEvent.LIGHT_MODE -> "theme"
            SystemEvent.WIFI_CONNECTED, SystemEvent.WIFI_DISCONNECTED -> "wifi"
            SystemEvent.CLIPBOARD -> "clipboard"
            SystemEvent.LOCATION_ON, SystemEvent.LOCATION_OFF -> "location"
            SystemEvent.DND_ON, SystemEvent.DND_OFF -> "dnd"
            SystemEvent.BEDTIME_ON, SystemEvent.BEDTIME_OFF -> "bedtime"
            SystemEvent.DATA_ON, SystemEvent.DATA_OFF -> "data"
            SystemEvent.UNLOCKED, SystemEvent.AUTH_FAILED -> "auth"
            SystemEvent.HOTSPOT_ON, SystemEvent.HOTSPOT_OFF -> "hotspot"
            SystemEvent.EYE_ON, SystemEvent.EYE_OFF -> "eye"
            SystemEvent.LINK_ON, SystemEvent.LINK_OFF -> "link"
        }
    }
}
