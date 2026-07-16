package app.picnic.player.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrPersonDetails
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.data.seerr.libraryLinkedPersonCredits
import app.picnic.player.data.seerr.sortedByReleaseDateDesc
import app.picnic.player.data.seerr.tmdbIdFromProviderIds
import app.picnic.player.data.seerr.toCatalogItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel(assistedFactory = PersonViewModel.Factory::class)
class PersonViewModel @AssistedInject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    @Assisted("jellyfinPersonId") private val jellyfinPersonId: String?,
    @Assisted("tmdbId") private val tmdbId: Int?
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("jellyfinPersonId") jellyfinPersonId: String?,
            @Assisted("tmdbId") tmdbId: Int?
        ): PersonViewModel
    }

    /** Library Cast entry when Jellyfin UUID is present; Seerr Cast entry otherwise. */
    val isLibraryEntry: Boolean get() = !jellyfinPersonId.isNullOrBlank()

    data class UiState(
        val loading: Boolean = true,
        val person: BaseItemDto? = null,
        val seerrPerson: SeerrPersonDetails? = null,
        /** Library entry: merged Movies + Series, newest → oldest. */
        val libraryItems: List<BaseItemDto> = emptyList(),
        /** Seerr-only entry: credits with a Jellyfin library link. */
        val libraryCredits: List<SeerrCatalogItem> = emptyList(),
        /** Full combined_credits (cast+crew deduped); not Missing-filtered. */
        val knownFor: List<SeerrCatalogItem> = emptyList(),
        val session: UserSession? = null,
        val seerrBaseUrl: String? = null,
        val seerrCacheImages: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val session = authRepository.activeSession()
        if (session == null) {
            _state.update { it.copy(error = "No active session", loading = false) }
            return
        }
        _state.update { it.copy(session = session) }

        val seerrState = seerrRepository.state.value
        val seerrLinked = seerrState.linkState == SeerrLinkState.Linked
        if (seerrLinked) {
            _state.update {
                it.copy(
                    seerrBaseUrl = seerrState.serverUrl,
                    seerrCacheImages = seerrState.cacheImages
                )
            }
        }

        try {
            if (isLibraryEntry) {
                loadLibraryEntry(session, seerrLinked)
            } else {
                loadSeerrEntry(seerrLinked)
            }
            _state.update { it.copy(loading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message, loading = false) }
        }
    }

    private suspend fun loadLibraryEntry(session: UserSession, seerrLinked: Boolean) {
        val uuid = UUID.fromString(jellyfinPersonId)
        val person = mediaRepository.getPerson(session, uuid)
        _state.update { it.copy(person = person) }

        coroutineScope {
            val moviesDeferred = async {
                if ((person.movieCount ?: 0) > 0) {
                    mediaRepository.getItemsByPerson(session, uuid, listOf(BaseItemKind.MOVIE))
                } else {
                    emptyList()
                }
            }
            val seriesDeferred = async {
                if ((person.seriesCount ?: 0) > 0) {
                    mediaRepository.getItemsByPerson(session, uuid, listOf(BaseItemKind.SERIES))
                } else {
                    emptyList()
                }
            }
            val movies = moviesDeferred.await()
            val series = seriesDeferred.await()
            val libraryItems = sortedJellyfinByReleaseDesc(movies + series)
            _state.update { it.copy(libraryItems = libraryItems) }

            val hybridTmdb = tmdbId
                ?: tmdbIdFromProviderIds(person.providerIds)
            if (seerrLinked && hybridTmdb != null) {
                val credits = runCatching { seerrRepository.personCredits(hybridTmdb) }
                    .getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        knownFor = credits
                            .sortedByReleaseDateDesc { it.releaseDate }
                            .map { c -> c.toCatalogItem() }
                    )
                }
            }
        }
    }

    private suspend fun loadSeerrEntry(seerrLinked: Boolean) {
        val id = tmdbId
        if (id == null) {
            _state.update { it.copy(error = "Missing person id", loading = false) }
            return
        }
        if (!seerrLinked) {
            _state.update {
                it.copy(error = "Seerr not linked", loading = false)
            }
            return
        }

        coroutineScope {
            val detailsDeferred = async {
                runCatching { seerrRepository.person(id) }.getOrNull()
            }
            val creditsDeferred = async {
                runCatching { seerrRepository.personCredits(id) }.getOrDefault(emptyList())
            }
            val details = detailsDeferred.await()
            val credits = creditsDeferred.await()
            if (details == null) {
                _state.update { it.copy(error = "Person not found") }
                return@coroutineScope
            }
            val sorted = credits.sortedByReleaseDateDesc { it.releaseDate }
            _state.update {
                it.copy(
                    seerrPerson = details,
                    libraryCredits = libraryLinkedPersonCredits(sorted)
                        .map { c -> c.toCatalogItem() },
                    knownFor = sorted.map { c -> c.toCatalogItem() }
                )
            }
        }
    }
}

/**
 * Newest → oldest by premiere date, else production year; null/unknown last.
 */
internal fun sortedJellyfinByReleaseDesc(items: List<BaseItemDto>): List<BaseItemDto> = items.sortedWith(
    Comparator { a, b ->
        val da = jellyfinReleaseDate(a)
        val db = jellyfinReleaseDate(b)
        when {
            da == null && db == null -> 0
            da == null -> 1
            db == null -> -1
            else -> db.compareTo(da)
        }
    }
)

private fun jellyfinReleaseDate(item: BaseItemDto): LocalDate? {
    item.premiereDate?.let { return it.toLocalDate() }
    val year = item.productionYear ?: return null
    return runCatching { LocalDate.of(year, 1, 1) }.getOrNull()
}
