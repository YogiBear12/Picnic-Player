package app.picnic.player.ui.detail

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PersonKind

data class RelatedRow(
    val key: String,
    val title: String,
    val items: List<BaseItemDto>? = null,
    val endReached: Boolean = false
)

internal sealed interface RelatedRowQuery {
    data object Similar : RelatedRowQuery
    data class Person(val id: UUID, val kind: PersonKind, val type: BaseItemKind) : RelatedRowQuery
    data class Genre(val id: UUID, val type: BaseItemKind) : RelatedRowQuery
}

internal data class RelatedRowSpec(
    val key: String,
    val title: String,
    val eager: Boolean,
    val query: RelatedRowQuery
)

internal fun relatedRowSpecs(item: BaseItemDto): List<RelatedRowSpec> {
    val type = item.type
    val people = item.people.orEmpty()
    return buildList {
        add(RelatedRowSpec(ROW_SIMILAR, "More like this", eager = true, query = RelatedRowQuery.Similar))
        if (type != BaseItemKind.MOVIE && type != BaseItemKind.SERIES) return@buildList
        people.firstOrNull { it.type == PersonKind.ACTOR }?.let { actor ->
            add(RelatedRowSpec(ROW_FEATURING, "Featuring ${actor.name}", eager = false, query = RelatedRowQuery.Person(actor.id, PersonKind.ACTOR, type)))
        }
        val credits = when (type) {
            BaseItemKind.MOVIE -> listOf(PersonKind.DIRECTOR to "Directed by")
            else -> listOf(PersonKind.CREATOR to "Created by", PersonKind.WRITER to "Written by")
        }
        credits.firstNotNullOfOrNull { (kind, prefix) ->
            people.firstOrNull { it.type == kind }?.let { person ->
                RelatedRowSpec(ROW_CREDIT, "$prefix ${person.name}", eager = false, query = RelatedRowQuery.Person(person.id, kind, type))
            }
        }?.let { add(it) }
        item.genreItems.orEmpty().firstOrNull()?.let { genre ->
            add(RelatedRowSpec(ROW_GENRE, "More in ${genre.name}", eager = false, query = RelatedRowQuery.Genre(genre.id, type)))
        }
    }
}

internal const val ROW_SIMILAR = "similar"
internal const val ROW_FEATURING = "related-featuring"
internal const val ROW_CREDIT = "related-credit"
internal const val ROW_GENRE = "related-genre"
internal const val RELATED_ID_CAP = 100
internal const val SIMILAR_ID_CAP = 40
internal const val RELATED_PAGE_SIZE = 20
internal const val RELATED_PREFETCH = 5

internal fun needsNextPage(focusedIndex: Int, loaded: Int, endReached: Boolean, inFlight: Boolean): Boolean = !endReached && !inFlight && focusedIndex >= loaded - RELATED_PREFETCH

internal fun needsAutoLoad(hasPeople: Boolean, eagerRowsEmpty: Boolean, hasCollections: Boolean): Boolean = !hasPeople && eagerRowsEmpty && !hasCollections

internal fun rowsInDisplayOrder(rows: List<RelatedRow>): List<RelatedRow> {
    var waiting = false
    return rows.mapNotNull { row ->
        val items = row.items
        if (items == null) waiting = true
        when {
            items?.isEmpty() == true -> null
            waiting -> row.copy(items = null)
            else -> row
        }
    }
}

internal data class RelatedPage(val items: List<BaseItemDto>, val endReached: Boolean)

internal class RowCursor(var next: Int = 0, var ids: List<UUID>? = null, val pageSize: Int = RELATED_PAGE_SIZE)

internal suspend fun loadOrderedPage(
    cursor: RowCursor,
    fetchIds: suspend () -> List<UUID>,
    fetchItems: suspend (List<UUID>) -> List<BaseItemDto>
): RelatedPage {
    val ids = cursor.ids ?: fetchIds().distinct().also { cursor.ids = it }
    val startIndex = cursor.next
    val slice = ids.drop(startIndex).take(cursor.pageSize)
    val byId = fetchItems(slice).associateBy { it.id }
    val items = slice.mapNotNull { byId[it] }
    check(slice.isEmpty() || items.isNotEmpty()) { "Ordered page hydration returned no items" }
    cursor.next = startIndex + slice.size
    return RelatedPage(items, cursor.next >= ids.size)
}
