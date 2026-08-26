package app.picnic.player.ui.collection

import androidx.compose.runtime.Immutable
import app.picnic.player.data.media.CollectionSection
import app.picnic.player.data.media.sectionFor
import org.jellyfin.sdk.model.api.BaseItemDto

@Immutable
data class CollectionRow(
    val section: CollectionSection,
    val items: List<BaseItemDto>
)

fun collectionRows(children: List<BaseItemDto>): List<CollectionRow> {
    val bySection = children.groupBy { sectionFor(it.type) }
    return CollectionSection.entries.mapNotNull { section ->
        bySection[section]?.let { CollectionRow(section, it) }
    }
}
