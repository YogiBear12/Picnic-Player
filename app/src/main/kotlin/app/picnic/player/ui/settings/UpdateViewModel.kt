package app.picnic.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.update.DownloadProgress
import app.picnic.player.data.update.UpdateRelease
import app.picnic.player.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository
) : ViewModel() {
    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data object UpToDate : Phase
        data class Available(val release: UpdateRelease) : Phase
        data class Downloading(val release: UpdateRelease, val progress: DownloadProgress) : Phase
        data class ReadyToInstall(val release: UpdateRelease) : Phase
        data class Failed(val message: String) : Phase
    }

    val enabled: Boolean = repository.enabled

    val updateAvailable: StateFlow<Boolean> = repository.updateAvailable
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    init {
        repository.updateAvailable.value?.let { _phase.value = Phase.Available(it) }
    }

    fun check() {
        if (_phase.value is Phase.Checking || _phase.value is Phase.Downloading) return
        _phase.value = Phase.Checking
        viewModelScope.launch {
            runCatching { repository.check(force = true) }
                .onSuccess { release ->
                    _phase.value = if (release != null) Phase.Available(release) else Phase.UpToDate
                }
                .onFailure { _phase.value = Phase.Failed("Could not reach the update server") }
        }
    }

    fun downloadAndInstall(release: UpdateRelease) {
        if (_phase.value is Phase.Downloading) return
        viewModelScope.launch {
            runCatching {
                repository.download(release).collect { progress ->
                    _phase.value = Phase.Downloading(release, progress)
                }
            }.onSuccess {
                _phase.value = Phase.ReadyToInstall(release)
                runCatching { repository.install(release) }
                    .onFailure { _phase.value = Phase.Failed("Installer could not be started") }
            }.onFailure {
                _phase.value = Phase.Failed("Download failed — check your connection")
            }
        }
    }

    fun dismiss() {
        _phase.value = repository.updateAvailable.value
            ?.let { Phase.Available(it) }
            ?: Phase.Idle
    }
}
