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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

data class HomeResult(
    val session: UserSession,
    val rows: List<HomeRow>,
    val libraries: List<BrowseDest.Library>,
    val playlistsAvailable: Boolean,
    val resume: List<BaseItemDto>,
    val nextUp: List<BaseItemDto>,
    val queuedDates: Map<UUID, LocalDateTime>,
    val latestByLibrary: List<Pair<BaseItemDto, List<BaseItemDto>>>
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
    private var inFlightKey: String? = null
    private var inFlight: Deferred<HomeResult>? = null

    fun cacheKey(session: UserSession): String = "${session.server.id}|${session.userId}"

    @Synchronized
    fun prefetch(session: UserSession): Deferred<HomeResult> {
        val key = cacheKey(session)
        inFlight?.let { existing ->
            if (inFlightKey == key && existing.isActive) return existing
        }
        return appScope.async { fetch(session) }.also {
            inFlight = it
            inFlightKey = key
        }
    }

    suspend fun fetch(session: UserSession): HomeResult {
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
        return coroutineScope {
            val resumeDeferred = async {
                runCatching { mediaRepository.resumeItems() }.getOrDefault(emptyList())
            }
            val nextUpDeferred = async {
                runCatching { mediaRepository.nextUp() }.getOrDefault(emptyList())
            }
            val latestDeferred = views.map { view ->
                async {
                    view to runCatching {
                        mediaRepository.latestInLibrary(view.id)
                    }.getOrDefault(emptyList())
                }
            }
            val hidden = hiddenResumeStore.snapshot()
            val resume = resumeDeferred.await()
            val nextUp = nextUpDeferred.await()
            val queuedDates = mediaRepository.queuedEpisodeDates(nextUp)
            val latest = latestDeferred.awaitAll()

            val discoverAvailable = seerrRepository.state.value.linkState == SeerrLinkState.Linked
            val availableIds = buildList {
                addAll(libraries.map { it.key })
                if (playlistsAvailable) add(NAV_ID_PLAYLISTS)
                if (discoverAvailable) add(NAV_ID_DISCOVER)
            }
            val layout = navLayoutStore.resolve(session.server.id, session.userId, availableIds)
            navRail.publish(session, libraries, discoverAvailable, playlistsAvailable, layout)
            val pinnedIds = navRail.pinnedLibraries().map { it.id }
            val rows =
                HomeContent.buildHomeRows(resume, nextUp, latest, pinnedIds, hidden, queuedDates)
            HomeResult(
                session = session,
                rows = rows,
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
