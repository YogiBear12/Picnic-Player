package app.picnic.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.ui.navigation.FolderKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

fun folderPath(folderName: String, ancestorsNearestFirst: List<BaseItemDto>): List<String> {
    val between = ancestorsNearestFirst
        .takeWhile { it.type == BaseItemKind.FOLDER || it.type == BaseItemKind.PHOTO_ALBUM }
        .reversed()
        .map { it.name.orEmpty() }
    return between + folderName
}

@HiltViewModel
class FolderPathViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    private val _path = MutableStateFlow<List<String>>(emptyList())
    val path = _path.asStateFlow()

    fun bind(key: FolderKey) {
        if (_path.value.isNotEmpty()) return
        _path.value = listOf(key.folderName)
        viewModelScope.launch {
            runCatching { mediaRepository.folderAncestors(UUID.fromString(key.folderId)) }
                .onSuccess { _path.value = folderPath(key.folderName, it) }
        }
    }
}
