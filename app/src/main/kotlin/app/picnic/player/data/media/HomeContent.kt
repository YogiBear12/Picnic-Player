package app.picnic.player.data.media

import androidx.compose.runtime.Immutable
import java.util.UUID
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
@Immutable
data class HomeRow(
    val title: String,
    val items: List<BaseItemDto>,
    val continueWatching: Boolean
)

object HomeContent {
    fun withoutHidden(items: List<BaseItemDto>, hidden: Map<String, Long>): List<BaseItemDto> {
        if (hidden.isEmpty()) return items
        return items.filter { item ->
            hidden[item.id.toString()] != (item.userData?.playbackPositionTicks ?: 0L)
        }
    }

    fun earliestPerSeries(resume: List<BaseItemDto>): List<BaseItemDto> {
        val slotOfSeries = LinkedHashMap<UUID, Int>()
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
            } else if (episodeOrder.compare(item, out[slot]) < 0) {
                out[slot] = item
            }
        }
        return out
    }

    private val episodeOrder = compareBy<BaseItemDto>(
        { it.parentIndexNumber ?: Int.MAX_VALUE },
        { it.indexNumber ?: Int.MAX_VALUE }
    )

    fun combineContinueWatching(
        resume: List<BaseItemDto>,
        nextUp: List<BaseItemDto>
    ): List<BaseItemDto> {
        val out = ArrayList<BaseItemDto>(resume.size + nextUp.size)
        val seenItems = HashSet<UUID>()
        val seenSeries = HashSet<UUID>()
        for (item in earliestPerSeries(resume)) {
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
        return out
    }

    fun buildHomeRows(
        resume: List<BaseItemDto>,
        nextUp: List<BaseItemDto>,
        latestByLibrary: List<Pair<BaseItemDto, List<BaseItemDto>>>,
        pinnedLibraryIds: List<UUID>? = null,
        hidden: Map<String, Long> = emptyMap()
    ): List<HomeRow> {
        val rows = ArrayList<HomeRow>()
        val continueWatching = withoutHidden(combineContinueWatching(resume, nextUp), hidden)
        if (continueWatching.isNotEmpty()) {
            rows += HomeRow("Continue watching", continueWatching, continueWatching = true)
        }
        val byId = latestByLibrary.associateBy { it.first.id }
        val ordered = if (pinnedLibraryIds == null) {
            latestByLibrary
        } else {
            pinnedLibraryIds.mapNotNull { byId[it] }
        }
        for ((view, items) in ordered) {
            if (items.isNotEmpty()) {
                rows += HomeRow(
                    title = "Recently added in ${view.name.orEmpty()}",
                    items = items,
                    continueWatching = false
                )
            }
        }
        return rows
    }
}
