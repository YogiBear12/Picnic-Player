package app.picnic.player.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.picnic.player.data.media.FolderContext
import app.picnic.player.data.media.PersonalLibrary
import app.picnic.player.data.media.isFolderContainer
import app.picnic.player.ui.common.resumeTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

@HiltViewModel
class AppNavigationViewModel @Inject constructor() : ViewModel() {
    val backStack = NavBackStack<NavKey>(StartupKey)

    var pendingDeepLink: NavKey? = null

    fun consumePendingDeepLink(): NavKey? {
        val key = pendingDeepLink
        pendingDeepLink = null
        return key
    }

    fun resetTo(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun pop() {
        backStack.removeLastOrNull()
    }

    fun openItem(item: BaseItemDto, bgUrl: String?, ambUrl: String?) {
        val seriesId = item.seriesId
        when {
            item.type == BaseItemKind.EPISODE && seriesId != null -> push(
                EpisodesKey(
                    seriesId = seriesId.toString(),
                    ambUrl = ambUrl,
                    focusEpisodeId = item.id.toString(),
                    seasonId = item.seasonId?.toString()
                )
            )
            item.type == BaseItemKind.SEASON && seriesId != null -> push(
                EpisodesKey(
                    seriesId = seriesId.toString(),
                    ambUrl = ambUrl,
                    seasonId = item.id.toString()
                )
            )
            item.type == BaseItemKind.BOX_SET -> push(CollectionKey(item.id.toString()))
            item.type == BaseItemKind.PLAYLIST -> push(PlaylistKey(item.id.toString(), item.name))
            item.type == BaseItemKind.VIDEO -> playVideo(item)
            else -> push(DetailKey(item.id.toString(), bgUrl, ambUrl))
        }
    }

    fun openPersonal(library: PersonalLibrary, item: BaseItemDto, from: FolderContext?) {
        when {
            item.isFolderContainer() -> push(FolderKey(library, item.id.toString(), item.name.orEmpty()))
            item.type == BaseItemKind.PHOTO -> push(
                PhotoKey(
                    library = library,
                    photoId = item.id.toString(),
                    photoName = item.name.orEmpty(),
                    imageTag = item.imageTags?.get(ImageType.PRIMARY),
                    folder = from
                )
            )
            item.type == BaseItemKind.VIDEO -> playVideo(item)
        }
    }

    private fun playVideo(item: BaseItemDto) = push(PlayerKey(item.id.toString(), startTicks = item.resumeTicks()))

    fun replaceTop(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }
}
