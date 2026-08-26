package app.picnic.player.data.media

import java.util.UUID
import kotlin.random.Random
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

enum class QueueKind {
    PLAYLIST,
    COLLECTION
}

@Serializable
data class ItemQueue(
    val parentId: String,
    val kind: QueueKind,
    val shuffleSeed: Long? = null,
    val position: Int = 0
) {
    val id: UUID? get() = runCatching { UUID.fromString(parentId) }.getOrNull()

    fun order(entries: List<BaseItemDto>): List<BaseItemDto> = if (shuffleSeed == null) entries else entries.shuffled(Random(shuffleSeed))

    fun itemAfter(entries: List<BaseItemDto>): BaseItemDto? = order(entries).getOrNull(position + 1)

    fun advanced(): ItemQueue = copy(position = position + 1)
}
