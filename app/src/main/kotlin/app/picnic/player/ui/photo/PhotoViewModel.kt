package app.picnic.player.ui.photo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.media.GridSortSpec
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.ui.navigation.PhotoKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private const val TAG = "PhotoViewModel"

data class PhotoFrame(val id: UUID, val name: String, val imageTag: String?)

/** A photo at the Library root reports the CollectionFolder or UserView as its parent, which lists as the Library itself. */
fun externalPhotoParent(libraryId: UUID, ancestorsNearestFirst: List<BaseItemDto>): UUID? {
    val parent = ancestorsNearestFirst.firstOrNull() ?: return null
    return when (parent.type) {
        BaseItemKind.FOLDER, BaseItemKind.PHOTO_ALBUM -> parent.id
        BaseItemKind.COLLECTION_FOLDER, BaseItemKind.USER_VIEW -> libraryId
        else -> null
    }
}

@HiltViewModel
class PhotoViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {
    data class UiState(
        val photos: List<PhotoFrame> = emptyList(),
        val index: Int = 0,
        val listLoaded: Boolean = false,
        val listFailed: Boolean = false,
        val overlay: Boolean = false,
        val sessionExpiredServerId: String? = null
    ) {
        val current: PhotoFrame? get() = photos.getOrNull(index)
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var key: PhotoKey? = null
    private var loadJob: Job? = null

    fun bind(key: PhotoKey) {
        if (this.key == key) return
        this.key = key
        _state.value = UiState(photos = listOf(PhotoFrame(UUID.fromString(key.photoId), key.photoName, key.imageTag)))
        loadSiblings()
    }

    fun retry() {
        if (!_state.value.listLoaded) loadSiblings()
    }

    fun next() = step(1)

    fun previous() = step(-1)

    fun toggleOverlay() = _state.update { it.copy(overlay = !it.overlay) }

    fun consumeSessionExpired() = _state.update { it.copy(sessionExpiredServerId = null) }

    private fun step(delta: Int) = _state.update { it.copy(index = (it.index + delta).coerceIn(0, it.photos.lastIndex)) }

    private fun loadSiblings() {
        val key = key ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(listFailed = false) }
            try {
                val photoId = UUID.fromString(key.photoId)
                val parent = key.folder?.id
                    ?: externalPhotoParent(key.library.id, mediaRepository.folderAncestors(photoId))
                if (parent == null) {
                    _state.update { it.copy(listFailed = true) }
                    return@launch
                }
                val photos = mediaRepository.folderPhotos(parent, key.folder?.sort ?: GridSortSpec()).map { it.toFrame() }
                val index = photos.indexOfFirst { it.id == photoId }
                if (index >= 0) {
                    _state.update { it.copy(photos = photos, index = index, listLoaded = true) }
                } else {
                    _state.update { it.copy(listLoaded = true) }
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (e: Exception) {
                Log.w(TAG, "Photo list failed (photo=${key.photoId})", e)
                if (e.isAuthFailure()) expireSession() else _state.update { it.copy(listFailed = true) }
            }
        }
    }

    private suspend fun expireSession() {
        val session = authRepository.activeSession() ?: return
        authRepository.expireStoredSession(session.server.id, session.userId)
        _state.update { it.copy(sessionExpiredServerId = session.server.id) }
    }
}

private fun BaseItemDto.toFrame() = PhotoFrame(id, name.orEmpty(), imageTags?.get(ImageType.PRIMARY))
