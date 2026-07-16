package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Hero-badge stream prefetch plan for a browse screen (Home / For you). Pure:
 * given the rows, where focus sits, and which items already have streams, it
 * decides which item ids to batch-fetch and which series ids to fetch on the
 * sliding window. The ViewModel performs the IO with its own concurrency bound.
 *
 * Movies/episodes batch across the whole focused-row ±1 band; series lead
 * streams cost one call each, so they are limited to a window around the row's
 * focused card ([SERIES_WINDOW_BEHIND]..[SERIES_WINDOW_AHEAD]).
 *
 * Extracted from the verbatim-duplicated `prefetchStreamsAhead` in
 * HomeViewModel and ForYouViewModel so the window logic lives (and is tested)
 * in one place.
 */
data class HeroPrefetchPlan(
    val streamIds: List<UUID>,
    val seriesIds: List<UUID>
) {
    val isEmpty: Boolean get() = streamIds.isEmpty() && seriesIds.isEmpty()
}

private const val SERIES_WINDOW_BEHIND = 2
private const val SERIES_WINDOW_AHEAD = 4

fun planHeroStreamPrefetch(
    rows: List<HomeRow>,
    focusedRowIndex: Int,
    rowFocusedItemIds: Map<Int, UUID>,
    alreadyFetched: Set<UUID>
): HeroPrefetchPlan {
    if (rows.isEmpty()) return HeroPrefetchPlan(emptyList(), emptyList())

    val streamIds = mutableListOf<UUID>()
    val seriesIds = mutableListOf<UUID>()
    val lookAheadRows = ((focusedRowIndex - 1)..(focusedRowIndex + 1)).filter { it in rows.indices }

    for (rIdx in lookAheadRows) {
        val row = rows[rIdx]
        val startIdx = row.items.indexOfFirst { it.id == rowFocusedItemIds[rIdx] }.coerceAtLeast(0)
        for (i in row.items.indices) {
            val item = row.items[i]
            if (item.id in alreadyFetched) continue
            val isSeries = item.type == BaseItemKind.SERIES || item.type == BaseItemKind.SEASON
            if (!isSeries) {
                // Movies/episodes: batched fetch for the entire row.
                streamIds.add(item.id)
            } else if (i in (startIdx - SERIES_WINDOW_BEHIND)..(startIdx + SERIES_WINDOW_AHEAD)) {
                // Series: sliding window to avoid API flooding.
                seriesIds.add(item.id)
            }
        }
    }
    return HeroPrefetchPlan(streamIds, seriesIds)
}

/** Series ids on [rows] that still need a season-count fetch (not in [alreadyCounted]). */
fun seriesNeedingSeasonCount(
    rows: List<HomeRow>,
    alreadyCounted: Set<UUID>
): List<UUID> = rows.asSequence()
    .flatMap { it.items.asSequence() }
    .filter { it.type == BaseItemKind.SERIES }
    .map { it.id }
    .distinct()
    .filter { it !in alreadyCounted }
    .toList()
