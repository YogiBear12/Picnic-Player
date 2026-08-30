package app.picnic.player.data.media

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi

@Singleton
class UserDataRepository @Inject constructor(
    private val source: SessionApi,
    private val changeBus: LibraryChangeBus,
    private val hiddenResumeStore: HiddenResumeStore
) {
    private suspend fun api() = source.client()

    private suspend fun <T> onIo(block: suspend () -> T): T = source.onIo(block)

    suspend fun setWatched(
        itemId: UUID,
        played: Boolean,
        seriesId: UUID? = null
    ) = onIo {
        (
            if (played) {
                api().playStateApi.markPlayedItem(itemId).content
            } else {
                api().playStateApi.markUnplayedItem(itemId).content
            }
            ).also {
            if (played) hiddenResumeStore.unhide((seriesId ?: itemId).toString())
            notifyItemChanged(itemId, seriesId)
        }
    }

    suspend fun setFavorite(
        itemId: UUID,
        favorite: Boolean,
        seriesId: UUID? = null
    ) = onIo {
        (
            if (favorite) {
                api().userLibraryApi.markFavoriteItem(itemId).content
            } else {
                api().userLibraryApi.unmarkFavoriteItem(itemId).content
            }
            ).also { notifyItemChanged(itemId, seriesId) }
    }

    private fun notifyItemChanged(itemId: UUID, seriesId: UUID?) {
        changeBus.emit(LibraryChange.ItemUpdated(itemId.toString(), seriesId?.toString()))
    }
}
