package app.picnic.player.ui.common

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.media.NavImages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.sdk.model.api.BaseItemDto

@Immutable
class ImageUrls internal constructor(private val session: UserSession?) {
    fun primary(item: BaseItemDto, fillWidth: Int = 480): String? = session?.let { JellyfinImages.primary(it, item, fillWidth) }

    fun personPrimary(personId: String, tag: String?, fillWidth: Int = 480): String? = session?.let { JellyfinImages.personPrimary(it, personId, tag, fillWidth) }

    fun rowPoster(item: BaseItemDto, fillWidth: Int = 480): String? = session?.let { JellyfinImages.rowPoster(it, item, fillWidth) }

    fun episodeStill(item: BaseItemDto, fillWidth: Int = 640): String? = session?.let { JellyfinImages.episodeStill(it, item, fillWidth) }

    fun thumb(item: BaseItemDto, fillWidth: Int = 640): String? = session?.let { JellyfinImages.thumb(it, item, fillWidth) }

    fun backdrop(item: BaseItemDto, fillWidth: Int = 1280): String? = session?.let { JellyfinImages.backdrop(it, item, fillWidth) }

    fun seriesPrimary(item: BaseItemDto, fillWidth: Int = 480): String? = session?.let { JellyfinImages.seriesPrimary(it, item, fillWidth) }

    fun ambient(item: BaseItemDto): String? = session?.let { JellyfinImages.ambient(it, item) }

    fun logo(item: BaseItemDto, fillWidth: Int = 480): String? = session?.let { JellyfinImages.logo(it, item, fillWidth) }

    fun navImages(item: BaseItemDto): NavImages = session?.let { JellyfinImages.navImages(it, item) } ?: NavImages(null, null)
}

val LocalImageUrls = staticCompositionLocalOf { ImageUrls(null) }

@HiltViewModel
class ImageUrlsViewModel @Inject constructor(
    authRepository: AuthRepository
) : ViewModel() {
    val imageUrls: StateFlow<ImageUrls> = authRepository.activeSessionFlow
        .map { ImageUrls(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ImageUrls(null))
}
