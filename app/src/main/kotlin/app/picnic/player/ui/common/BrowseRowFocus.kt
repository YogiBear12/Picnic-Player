package app.picnic.player.ui.common

import app.picnic.player.data.media.HomeRow
import java.util.UUID

internal data class BrowseRowFocus(val rowIndex: Int, val itemId: UUID?, val rowItemIds: Map<Int, UUID>)

internal fun BrowseRowFocus.retarget(previous: List<HomeRow>, combined: List<HomeRow>): BrowseRowFocus {
    val indices = combined.withIndex().associate { it.value.key to it.index }
    val focusedIndex = previous.getOrNull(rowIndex)?.key?.let(indices::get)
        ?: rowIndex.coerceIn(0, maxOf(combined.lastIndex, 0))
    val focusedItems = combined.getOrNull(focusedIndex)?.items.orEmpty()
    val focusedRowKept = previous.getOrNull(rowIndex)?.key in indices
    // A card removed from a surviving row keeps its id so the scaffold recovers to the neighboring slot.
    val focusedId = itemId?.takeIf { id -> focusedRowKept || focusedItems.any { it.id == id } }
        ?: focusedItems.firstOrNull()?.id
    val remembered = rowItemIds.mapNotNull { (index, id) ->
        val newIndex = previous.getOrNull(index)?.key?.let(indices::get) ?: return@mapNotNull null
        (newIndex to id).takeIf { combined[newIndex].items.any { it.id == id } }
    }.toMap()
    return BrowseRowFocus(
        rowIndex = focusedIndex,
        itemId = focusedId,
        rowItemIds = remembered.ifEmpty {
            focusedId?.takeIf { id -> focusedItems.any { it.id == id } }?.let { mapOf(focusedIndex to it) } ?: emptyMap()
        }
    )
}
