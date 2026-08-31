package app.picnic.player.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.media.LibraryChangeBatch
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.UserDataRepository
import app.picnic.player.data.media.batches
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

enum class ChildrenPhase { LOADING, PARTIAL, COMPLETE, FAILED }

@HiltViewModel(assistedFactory = CollectionViewModel.Factory::class)
class CollectionViewModel @AssistedInject constructor(
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository,
    private val changeBus: LibraryChangeBus,
    @Assisted private val collectionId: String
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(collectionId: String): CollectionViewModel
    }

    data class UiState(
        val loading: Boolean = true,
        val item: BaseItemDto? = null,
        val rows: List<CollectionRow> = emptyList(),
        val queue: List<BaseItemDto> = emptyList(),
        val phase: ChildrenPhase = ChildrenPhase.LOADING,
        val queuePhase: ChildrenPhase = ChildrenPhase.LOADING,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var children: List<BaseItemDto> = emptyList()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            changeBus.batches().collect { batch -> applyBatch(batch) }
        }
    }

    private suspend fun fetchItem(itemId: String): BaseItemDto? = runCatching { mediaRepository.item(UUID.fromString(itemId)) }.getOrNull()

    private suspend fun load() {
        val item = fetchItem(collectionId)
        if (item == null) {
            _state.update { it.copy(loading = false, error = "Could not load collection") }
            return
        }
        _state.update { it.copy(loading = false, item = item) }
        viewModelScope.launch { loadChildren(item.id) }
        viewModelScope.launch { loadQueue(item.id) }
    }

    private suspend fun loadQueue(id: UUID) {
        runCatching {
            mediaRepository.collectionQueue(id).collect { items ->
                _state.update { it.copy(queue = items, queuePhase = ChildrenPhase.PARTIAL) }
            }
        }
            .onSuccess { _state.update { it.copy(queuePhase = ChildrenPhase.COMPLETE) } }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
                _state.update { it.copy(queuePhase = ChildrenPhase.FAILED) }
            }
    }

    private suspend fun loadChildren(id: UUID) {
        runCatching {
            mediaRepository.collectionChildren(id).collect { publishChildren(it, ChildrenPhase.PARTIAL) }
        }
            .onSuccess { _state.update { it.copy(phase = ChildrenPhase.COMPLETE) } }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
                _state.update { it.copy(phase = ChildrenPhase.FAILED) }
            }
    }

    private fun publishChildren(items: List<BaseItemDto>, phase: ChildrenPhase) {
        children = items
        _state.update {
            it.copy(rows = collectionRows(items), phase = phase)
        }
    }

    private suspend fun refreshItem() {
        val updated = fetchItem(collectionId) ?: return
        _state.update { it.copy(item = updated) }
    }

    private suspend fun applyBatch(batch: LibraryChangeBatch) {
        val rootChanged = batch.contentChanged || collectionId in batch.itemIds
        if (rootChanged) refreshItem()
        val shown = children + state.value.queue
        val changedIds = if (rootChanged) shown.mapTo(mutableSetOf()) { it.id.toString() } else batch.itemIds
        val fresh = mediaRepository.refreshChanged(shown, changedIds)
        if (fresh.isEmpty()) return
        publishChildren(children.map { fresh[it.id] ?: it }, state.value.phase)
        _state.update { state -> state.copy(queue = state.queue.map { fresh[it.id] ?: it }) }
    }

    fun toggleWatched() {
        val item = state.value.item ?: return
        val played = item.userData?.played ?: false
        viewModelScope.launch {
            runCatching { userDataRepository.setWatched(item.id, !played) }
                .onSuccess { userData ->
                    _state.update { it.copy(item = it.item?.copy(userData = userData)) }
                }
        }
    }

    fun setFavorite(favorite: Boolean) {
        val item = state.value.item ?: return
        viewModelScope.launch {
            runCatching { userDataRepository.setFavorite(item.id, favorite) }
                .onSuccess { userData -> _state.update { it.copy(item = it.item?.copy(userData = userData)) } }
        }
    }
}
