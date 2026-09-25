package app.picnic.player.data.media

import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NAV_ID_DISCOVER
import app.picnic.player.data.nav.NAV_ID_PLAYLISTS
import app.picnic.player.data.nav.NavLayoutStore
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.di.ApplicationScope
import app.picnic.player.ui.browse.BrowseDest
import app.picnic.player.ui.browse.NavRailState
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

data class HomeResult(
    val session: UserSession,
    val libraries: List<BrowseDest.Library>,
    val playlistsAvailable: Boolean,
    val resume: List<BaseItemDto>,
    val nextUp: List<BaseItemDto>,
    val queuedDates: Map<UUID, LocalDateTime>,
    val latestByLibrary: List<Pair<BaseItemDto, List<BaseItemDto>>>
)

data class HomeLoad(
    val slots: List<HomeSlot>? = null,
    val libraries: List<BrowseDest.Library> = emptyList(),
    val playlistsAvailable: Boolean = false,
    val resolved: Map<String, HomeRow?> = emptyMap(),
    val outcome: Result<HomeResult>? = null
)

@Singleton
class HomeContentLoader @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val navLayoutStore: NavLayoutStore,
    private val navRail: NavRailState,
    private val hiddenResumeStore: HiddenResumeStore,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private var inFlight: Pair<String, StateFlow<HomeLoad>>? = null

    fun cacheKey(session: UserSession): String = "${session.server.id}|${session.userId}"

    @Synchronized
    fun prefetch(session: UserSession): StateFlow<HomeLoad> {
        val key = cacheKey(session)
        inFlight?.let { (inFlightKey, existing) ->
            if (inFlightKey == key && existing.value.outcome == null) return existing
        }
        val load = MutableStateFlow(HomeLoad())
        appScope.launch {
            try {
                val result = fetch(session, load)
                load.update { it.copy(outcome = Result.success(result)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                load.update { it.copy(outcome = Result.failure(e)) }
            }
        }
        inFlight = key to load
        return load
    }

    suspend fun fetch(session: UserSession, progress: MutableStateFlow<HomeLoad>? = null): HomeResult {
        val views = mediaRepository.userViews()
        val playlistsAvailable = views.any { it.collectionType == CollectionType.PLAYLISTS }
        val libraries = views.mapNotNull { view ->
            val kinds = when (view.collectionType) {
                CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
                CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
                null, CollectionType.UNKNOWN -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                else -> return@mapNotNull null
            }
            BrowseDest.Library(view.id, view.name.orEmpty(), kinds)
        }
        val discoverAvailable = seerrRepository.state.value.linkState == SeerrLinkState.Linked
        val availableIds = buildList {
            addAll(libraries.map { it.key })
            if (playlistsAvailable) add(NAV_ID_PLAYLISTS)
            if (discoverAvailable) add(NAV_ID_DISCOVER)
        }
        val layout = navLayoutStore.resolve(session.server.id, session.userId, availableIds)
        navRail.publish(session, libraries, discoverAvailable, playlistsAvailable, layout)
        val slots = HomeContent.homeSlots(views)
        progress?.update {
            it.copy(slots = slots, libraries = libraries, playlistsAvailable = playlistsAvailable)
        }
        val slotByLibrary = slots.associateBy { it.libraryId }
        fun resolve(slot: HomeSlot?, items: List<BaseItemDto>) {
            if (slot == null) return
            progress?.update { it.copy(resolved = it.resolved + (slot.key to slot.row(items))) }
        }

        return coroutineScope {
            val resumeDeferred = async {
                runCatching { mediaRepository.resumeItems() }.getOrDefault(emptyList())
            }
            val nextUpDeferred = async {
                runCatching { mediaRepository.nextUp() }.getOrDefault(emptyList())
            }
            val latestDeferred = views.map { view ->
                async {
                    val items = runCatching {
                        mediaRepository.latestInLibrary(view.id)
                    }.getOrDefault(emptyList())
                    resolve(slotByLibrary[view.id], items)
                    view to items
                }
            }
            val hidden = hiddenResumeStore.snapshot()
            val resume = resumeDeferred.await()
            val nextUp = nextUpDeferred.await()
            val queuedDates = mediaRepository.queuedEpisodeDates(nextUp)
            resolve(
                slots.first { it.continueWatching },
                HomeContent.continueWatchingItems(resume, nextUp, hidden, queuedDates)
            )
            val latest = latestDeferred.awaitAll()

            HomeResult(
                session = session,
                libraries = libraries,
                playlistsAvailable = playlistsAvailable,
                resume = resume,
                nextUp = nextUp,
                queuedDates = queuedDates,
                latestByLibrary = latest
            )
        }
    }
}
