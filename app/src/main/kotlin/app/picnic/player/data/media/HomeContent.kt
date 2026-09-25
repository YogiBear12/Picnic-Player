package app.picnic.player.data.media

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
@Immutable
data class HomeRow(
    val title: String,
    val items: List<BaseItemDto>,
    val continueWatching: Boolean,
    val key: String
)

@Immutable
data class HomeSlot(val title: String, val libraryId: UUID?) {
    val continueWatching: Boolean get() = libraryId == null
    val key: String get() = libraryId?.let { "library-$it" } ?: "continue-watching"

    fun row(items: List<BaseItemDto>): HomeRow? = items.takeIf { it.isNotEmpty() }?.let { HomeRow(title, it, continueWatching, key) }
}

data class RevealedRows(val rows: List<HomeRow>, val pending: List<HomeSlot>)

object HomeContent {
    fun lastPlayedMillis(item: BaseItemDto): Long? = item.userData?.lastPlayedDate
        ?.atZone(ZoneId.systemDefault())
        ?.toInstant()
        ?.toEpochMilli()

    fun withoutHidden(items: List<BaseItemDto>, hidden: Map<String, Long>): List<BaseItemDto> {
        if (hidden.isEmpty()) return items
        return items.filter { item ->
            val hiddenAt = hidden[item.seedId.toString()] ?: return@filter true
            (lastPlayedMillis(item) ?: 0L) > hiddenAt
        }
    }

    fun preferredPerSeries(resume: List<BaseItemDto>): List<BaseItemDto> {
        val slotOfSeries = HashMap<UUID, Int>()
        val out = ArrayList<BaseItemDto>(resume.size)
        for (item in resume) {
            val series = item.seriesId
            if (series == null) {
                out += item
                continue
            }
            val slot = slotOfSeries[series]
            if (slot == null) {
                slotOfSeries[series] = out.size
                out += item
            } else if (resumePriority.compare(item, out[slot]) < 0) {
                out[slot] = item
            }
        }
        return out
    }

    private val resumePriority = compareByDescending<BaseItemDto> { lastPlayedMillis(it) ?: Long.MIN_VALUE }
        .thenBy { it.parentIndexNumber ?: Int.MAX_VALUE }
        .thenBy { it.indexNumber ?: Int.MAX_VALUE }

    fun combineContinueWatching(
        resume: List<BaseItemDto>,
        nextUp: List<BaseItemDto>,
        queuedDates: Map<UUID, LocalDateTime> = emptyMap()
    ): List<BaseItemDto> {
        val out = ArrayList<BaseItemDto>(resume.size + nextUp.size)
        val seenItems = HashSet<UUID>()
        val seenSeries = HashSet<UUID>()
        for (item in preferredPerSeries(resume)) {
            if (seenItems.add(item.id)) {
                item.seriesId?.let(seenSeries::add)
                out += item
            }
        }
        for (item in nextUp) {
            if (item.id in seenItems) continue
            val series = item.seriesId
            if (series != null && series in seenSeries) continue
            seenItems += item.id
            series?.let(seenSeries::add)
            out += item
        }
        return out.sortedByDescending { queuedDates[it.id] ?: it.userData?.lastPlayedDate }
    }

    fun homeSlots(views: List<BaseItemDto>): List<HomeSlot> = listOf(HomeSlot("Continue watching", libraryId = null)) +
        views.map { view -> HomeSlot("Recently added in ${view.name.orEmpty()}", view.id) }

    fun visibleSlots(slots: List<HomeSlot>, pinnedLibraryIds: List<UUID>): List<HomeSlot> {
        val byId = slots.associateBy { it.libraryId }
        return listOfNotNull(byId[null]) + pinnedLibraryIds.mapNotNull(byId::get)
    }

    fun continueWatchingItems(
        resume: List<BaseItemDto>,
        nextUp: List<BaseItemDto>,
        hidden: Map<String, Long>,
        queuedDates: Map<UUID, LocalDateTime>
    ): List<BaseItemDto> = withoutHidden(combineContinueWatching(resume, nextUp, queuedDates), hidden)

    fun buildHomeRows(
        resume: List<BaseItemDto>,
        nextUp: List<BaseItemDto>,
        latestByLibrary: List<Pair<BaseItemDto, List<BaseItemDto>>>,
        pinnedLibraryIds: List<UUID>,
        hidden: Map<String, Long> = emptyMap(),
        queuedDates: Map<UUID, LocalDateTime> = emptyMap()
    ): List<HomeRow> {
        val continueWatching = continueWatchingItems(resume, nextUp, hidden, queuedDates)
        val latest = latestByLibrary.associate { (view, items) -> view.id to items }
        return visibleSlots(homeSlots(latestByLibrary.map { it.first }), pinnedLibraryIds).mapNotNull { slot ->
            slot.row(if (slot.continueWatching) continueWatching else latest[slot.libraryId].orEmpty())
        }
    }

    /**
     * Rows show strictly top-down: a slot is revealed only once every slot above it has
     * resolved, so a row never appears above one the user is already on.
     */
    fun reveal(slots: List<HomeSlot>, resolved: Map<String, HomeRow?>): RevealedRows {
        val rows = ArrayList<HomeRow>(slots.size)
        for ((index, slot) in slots.withIndex()) {
            if (slot.key !in resolved) return RevealedRows(rows, slots.subList(index, slots.size))
            resolved[slot.key]?.let(rows::add)
        }
        return RevealedRows(rows, emptyList())
    }
}
