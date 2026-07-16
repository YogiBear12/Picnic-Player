package app.picnic.player.data.media

import androidx.compose.runtime.Immutable
import java.util.UUID
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * One home row — Continue watching, or "Recently added in {library}".
 *
 * [Serializable] so [HomeCache] can persist rows for stale-while-revalidate render;
 * [BaseItemDto] carries its own SDK serializer.
 */
@Serializable
@Immutable
data class HomeRow(
    val title: String,
    val items: List<BaseItemDto>,
    val continueWatching: Boolean
)

object HomeContent {

    /**
     * Merges Resume + Next Up into a single Continue Watching list:
     * Resume first (in-progress), then Next Up entries whose item/series aren't
     * already represented. Server order is preserved within each source.
     */
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

    /**
     * Assembles the home rows: Continue Watching (when non-empty) followed by a
     * "Recently added in {library}" row per library that has items.
     */
    fun buildHomeRows(
        resume: List<BaseItemDto>,
        nextUp: List<BaseItemDto>,
        latestByLibrary: List<Pair<BaseItemDto, List<BaseItemDto>>>
    ): List<HomeRow> {
        val rows = ArrayList<HomeRow>()
        val continueWatching = combineContinueWatching(resume, nextUp)
        if (continueWatching.isNotEmpty()) {
            rows += HomeRow("Continue watching", continueWatching, continueWatching = true)
        }
        for ((view, items) in latestByLibrary) {
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
