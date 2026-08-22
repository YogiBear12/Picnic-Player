package app.picnic.player.data.media

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder

@Singleton
class PlaylistRepository @Inject constructor(
    private val source: SessionApi,
    private val changeBus: LibraryChangeBus
) {
    private suspend fun api() = source.client()

    private suspend fun <T> onIo(block: suspend () -> T): T = source.onIo(block)

    suspend fun playlists(): List<BaseItemDto> = onIo {
        api().itemsApi.getItems(
            userId = source.session().userUuid,
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            recursive = true,
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
            fields = LATEST_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            enableTotalRecordCount = false
        ).content.items.orEmpty()
    }

    suspend fun playlistItems(playlistId: UUID): List<BaseItemDto> = onIo {
        api().playlistsApi.getPlaylistItems(
            playlistId = playlistId,
            userId = source.session().userUuid,
            fields = BROWSE_FIELDS,
            enableImageTypes = IMAGE_TYPES
        ).content.items.orEmpty()
    }

    suspend fun createPlaylist(name: String, itemIds: List<UUID>): UUID? = onIo {
        val result = api().playlistsApi.createPlaylist(
            CreatePlaylistDto(
                name = name,
                ids = itemIds,
                userId = source.session().userUuid,
                mediaType = MediaType.VIDEO,
                users = emptyList(),
                isPublic = false
            )
        ).content
        changeBus.emit(LibraryChange.LibraryContentChanged)
        result.id?.let(UUID::fromString)
    }

    suspend fun addToPlaylist(playlistId: UUID, itemIds: List<UUID>) = onIo {
        api().playlistsApi.addItemToPlaylist(
            playlistId = playlistId,
            ids = itemIds,
            userId = source.session().userUuid
        )
        changeBus.emit(LibraryChange.ItemUpdated(playlistId.toString(), null))
    }

    suspend fun removeFromPlaylist(playlistId: UUID, entryIds: List<String>) = onIo {
        api().playlistsApi.removeItemFromPlaylist(
            playlistId = playlistId.toString(),
            entryIds = entryIds
        )
        changeBus.emit(LibraryChange.ItemUpdated(playlistId.toString(), null))
    }

    suspend fun deletePlaylist(playlistId: UUID) = onIo {
        api().libraryApi.deleteItem(playlistId)
        changeBus.emit(LibraryChange.LibraryContentChanged)
    }

    suspend fun movePlaylistItem(
        playlistId: UUID,
        playlistItemId: String,
        newIndex: Int
    ) = onIo {
        api().playlistsApi.moveItem(
            playlistId = playlistId.toString(),
            itemId = playlistItemId,
            newIndex = newIndex
        )
        changeBus.emit(LibraryChange.ItemUpdated(playlistId.toString(), null))
    }
}
