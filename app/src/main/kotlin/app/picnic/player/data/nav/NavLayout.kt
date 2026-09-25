package app.picnic.player.data.nav

import kotlinx.serialization.Serializable

/** Stable id for the Discover destination in persisted nav layouts. */
const val NAV_ID_DISCOVER = "discover"

/** Stable id for the Playlists destination in persisted nav layouts. */
const val NAV_ID_PLAYLISTS = "playlists"

/**
 * Per-user sidebar layout: customizable destinations split into pinned (page 1)
 * and unpinned (More / page 2), each in user order.
 */
@Serializable
data class NavLayout(
    val pinnedIds: List<String> = emptyList(),
    val unpinnedIds: List<String> = emptyList()
) {
    fun isPinned(id: String): Boolean = id in pinnedIds
}

/**
 * Reconciles saved layout with the currently available customizable ids.
 *
 * - Keeps saved ids that are unavailable right now, in place, so an id that returns (Discover
 *   linking late, a library coming back) keeps its position and pin state. Display filters them.
 * - New ids are pinned at the end of the pinned list (so users notice new libraries).
 * - On first layout ([saved] null): all libraries pinned in [availableIds] order;
 *   Discover respects [legacyDiscoverPinned] (migrated Show Discover pref). An unpinned Discover
 *   is saved even before Seerr links, because the legacy pref is deleted after this first run.
 */
object NavLayoutResolver {

    fun reconcile(
        availableIds: List<String>,
        saved: NavLayout?,
        legacyDiscoverPinned: Boolean = true
    ): NavLayout {
        val available = availableIds.distinct()

        if (saved == null) {
            val pinned = available.filter { id ->
                if (id == NAV_ID_DISCOVER) legacyDiscoverPinned else true
            }
            val unpinned = if (legacyDiscoverPinned) emptyList() else listOf(NAV_ID_DISCOVER)
            return NavLayout(pinnedIds = pinned, unpinnedIds = unpinned)
        }

        val pinned = saved.pinnedIds.distinct()
        val unpinned = saved.unpinnedIds.filter { it !in pinned }.distinct()
        val known = (pinned + unpinned).toSet()
        val newcomers = available.filterNot { it in known }
        return NavLayout(
            pinnedIds = pinned + newcomers,
            unpinnedIds = unpinned
        )
    }

    fun pin(layout: NavLayout, id: String): NavLayout {
        if (id in layout.pinnedIds) return layout
        return NavLayout(
            pinnedIds = layout.pinnedIds + id,
            unpinnedIds = layout.unpinnedIds.filterNot { it == id }
        )
    }

    fun unpin(layout: NavLayout, id: String): NavLayout {
        if (id in layout.unpinnedIds) return layout
        if (id !in layout.pinnedIds) {
            return NavLayout(
                pinnedIds = layout.pinnedIds,
                unpinnedIds = layout.unpinnedIds + id
            )
        }
        return NavLayout(
            pinnedIds = layout.pinnedIds.filterNot { it == id },
            unpinnedIds = layout.unpinnedIds + id
        )
    }

    /** Move [id] up or down within its current list, past ids that are not [shown]. */
    fun move(layout: NavLayout, id: String, delta: Int, shown: (String) -> Boolean): NavLayout {
        fun swap(list: List<String>): List<String> {
            val i = list.indexOf(id)
            if (i < 0) return list
            val visible = list.indices.filter { it == i || shown(list[it]) }
            val at = visible.indexOf(i)
            val j = visible[(at + delta).coerceIn(visible.indices)]
            if (i == j) return list
            return list.toMutableList().also {
                val tmp = it[i]
                it[i] = it[j]
                it[j] = tmp
            }
        }
        return when {
            id in layout.pinnedIds -> layout.copy(pinnedIds = swap(layout.pinnedIds))
            id in layout.unpinnedIds -> layout.copy(unpinnedIds = swap(layout.unpinnedIds))
            else -> layout
        }
    }
}
