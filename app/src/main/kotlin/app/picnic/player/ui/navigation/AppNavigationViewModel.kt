package app.picnic.player.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

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
        if (item.type == BaseItemKind.EPISODE && seriesId != null) {
            push(
                EpisodesKey(
                    seriesId = seriesId.toString(),
                    ambUrl = ambUrl,
                    focusEpisodeId = item.id.toString(),
                    seasonId = item.seasonId?.toString()
                )
            )
        } else {
            push(DetailKey(item.id.toString(), bgUrl, ambUrl))
        }
    }

    fun replaceTop(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }
}
