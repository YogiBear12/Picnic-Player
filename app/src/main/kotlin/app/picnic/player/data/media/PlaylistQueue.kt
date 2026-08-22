package app.picnic.player.data.media

import java.util.UUID
import kotlin.random.Random
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
data class PlaylistQueue(
    val playlistId: String,
    val shuffleSeed: Long? = null,
    val position: Int = 0
) {
    val id: UUID? get() = runCatching { UUID.fromString(playlistId) }.getOrNull()

    fun order(entries: List<BaseItemDto>): List<BaseItemDto> = if (shuffleSeed == null) entries else entries.shuffled(Random(shuffleSeed))

    fun itemAfter(entries: List<BaseItemDto>): BaseItemDto? = order(entries).getOrNull(position + 1)

    fun advanced(): PlaylistQueue = copy(position = position + 1)
}
