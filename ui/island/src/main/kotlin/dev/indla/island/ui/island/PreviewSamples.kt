package dev.indla.island.ui.island

import dev.indla.island.model.IslandAction
import dev.indla.island.model.IslandAppearance
import dev.indla.island.model.IslandItem
import dev.indla.island.model.IslandMode
import dev.indla.island.model.IslandUiState
import dev.indla.island.model.ItemKind
import dev.indla.island.model.MessageLine

/** Sample content for the live preview pinned on top of each settings tab. */
object PreviewSamples {
    fun chat(packageName: String) = IslandItem(
        key = "preview-chat",
        packageName = packageName,
        appLabel = "Island",
        kind = ItemKind.MESSAGE,
        title = "Weekend plans",
        glance = "3 new",
        lines = listOf(
            MessageLine("Meera", "Where are we going?"),
            MessageLine("Arpit + 2", "sent 3 reels", count = 3),
        ),
        actions = listOf(IslandAction(0, "Reply", isReply = true), IslandAction(1, "Mark as read", isReply = false)),
        avatarKey = null,
        postTime = 0,
        groupKey = null,
        isGroupSummary = false,
    )

    private fun second(packageName: String) = chat(packageName).copy(key = "preview-2", title = "Priya", glance = "Priya")

    /** Cycles Rest → Compact → Expanded → Split. */
    fun state(step: Int, packageName: String, appearance: IslandAppearance): IslandUiState {
        val chat = chat(packageName)
        return when (step.mod(4)) {
            0 -> IslandUiState(mode = IslandMode.REST, appearance = appearance.copy(showRestPill = true))
            1 -> IslandUiState(mode = IslandMode.COMPACT, items = listOf(chat), appearance = appearance)
            2 -> IslandUiState(mode = IslandMode.EXPANDED, items = listOf(chat), appearance = appearance)
            else -> IslandUiState(mode = IslandMode.SPLIT, items = listOf(chat, second(packageName)), appearance = appearance)
        }
    }

    val stepNames = listOf("Rest", "Compact", "Expanded", "Split")
}
