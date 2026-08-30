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

    fun combineContinueWatching(
        resume: List<BaseItemDto>,
        nextUp: List<BaseItemDto>
    ): List<BaseItemDto> {
        val out = ArrayList<BaseItemDto>(resume.size + nextUp.size)
        val seenItems = HashSet<UUID>()
        val seenSeries = HashSet<UUID>()
        for (item in resume) {
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
        pinnedLibraryIds: List<UUID>? = null
    ): List<HomeRow> {
        val rows = ArrayList<HomeRow>()
        val continueWatching = combineContinueWatching(resume, nextUp)
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
