package dev.indla.island.model

/** The four visible states. Every change between them is one spring of a single shape. */
enum class IslandMode { REST, COMPACT, SPLIT, EXPANDED }

/** Everything the overlay needs to draw one frame. The engine holds the only copy. */
data class IslandUiState(
    val mode: IslandMode = IslandMode.REST,
    /** False when a "When to show" rule (or the master switch) hides the island. */
    val visible: Boolean = true,
    /** Active items, newest first. */
    val items: List<IslandItem> = emptyList(),
    val currentIndex: Int = 0,
    val replyOpen: Boolean = false,
    val quickActionsOpen: Boolean = false,
    val positionEditing: Boolean = false,
    val appearance: IslandAppearance = IslandAppearance(),
) {
    val current: IslandItem? get() = items.getOrNull(currentIndex)

    /** The item shown in the bubble right of the island in the split state. */
    val secondary: IslandItem? get() = if (items.size > 1) items[(currentIndex + 1) % items.size] else null

    /** A third item gets a bubble on the left, so the island can show more at once. */
    val tertiary: IslandItem? get() = if (items.size > 2) items[(currentIndex + 2) % items.size] else null

    val showsRestPill: Boolean get() = appearance.showRestPill || positionEditing
}

data class ServiceHealth(
    val listenerConnected: Boolean = false,
    val overlayConnected: Boolean = false,
)
