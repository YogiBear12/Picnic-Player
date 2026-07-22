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

/** One home fetch's payload: the built rows plus the raw inputs the VM keeps for pin re-ordering. */
data class HomeResult(
    val session: UserSession,
    val rows: List<HomeRow>,
    val libraries: List<BrowseDest.Library>,
    val playlistsAvailable: Boolean,
    val resume: List<BaseItemDto>,
    val nextUp: List<BaseItemDto>,
    val latestByLibrary: List<Pair<BaseItemDto, List<BaseItemDto>>>
)

/**
 * Owns the home-rows network fetch so it can run BEFORE the home screen exists: the startup
 * splash fires [prefetch] the moment it has a session, so the round-trip overlaps the splash and
 * the fresh rows are usually ready by the time Home composes (no stale-then-fresh swap). The
 * work runs in the application scope so it survives the splash → Home navigation, and is memoized
 * per session so Home re-uses the in-flight [Deferred] instead of firing a second fetch.
 *
 * Publishing the nav rail is part of [fetch] (not the caller) so the drawer is correct the instant
 * Home composes, and so a mid-session refresh re-publishes it the same way.
 */
@Singleton
class HomeContentLoader @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val navLayoutStore: NavLayoutStore,
    private val navRail: NavRailState,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private var inFlightKey: String? = null
    private var inFlight: Deferred<HomeResult>? = null

    fun cacheKey(session: UserSession): String = "${session.server.id}|${session.userId}"

    /**
     * Starts (or re-uses) the home fetch for [session] in the application scope. Re-uses only work
     * that is still IN-FLIGHT — so the splash and a near-simultaneous Home share one fetch — but a
     * COMPLETED deferred is never handed back: this is a @Singleton that outlives a relaunch, and
     * returning a finished result would serve last-launch (stale) data. Every fresh cold entry
     * therefore kicks a new fetch.
     */
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

    /**
     * Fresh home fetch, bypassing the prefetch memo — used by mid-session refreshes where the rows
     * are already on screen and staleness must be re-checked against the network.
     */
    suspend fun fetch(session: UserSession): HomeResult {
        val views = mediaRepository.userViews(session)
        val playlistsAvailable = views.any { it.collectionType == CollectionType.PLAYLISTS }
        val libraries = views.mapNotNull { view ->
            val kinds = when (view.collectionType) {
                CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
                CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
                // Mixed content libraries report no collection type.
                null, CollectionType.UNKNOWN -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                else -> return@mapNotNull null // music / photos / books / etc.
            }
            BrowseDest.Library(view.id, view.name.orEmpty(), kinds)
        }
        return coroutineScope {
            val resumeDeferred = async {
                runCatching { mediaRepository.resumeItems(session) }.getOrDefault(emptyList())
            }
            val nextUpDeferred = async {
                runCatching { mediaRepository.nextUp(session) }.getOrDefault(emptyList())
            }
            val latestDeferred = views.map { view ->
                async {
                    view to runCatching {
                        mediaRepository.latestInLibrary(session, view.id)
                    }.getOrDefault(emptyList())
                }
            }
            val resume = resumeDeferred.await()
            val nextUp = nextUpDeferred.await()
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
            val rows = HomeContent.buildHomeRows(resume, nextUp, latest, pinnedIds)
            HomeResult(
                session = session,
                rows = rows,
                libraries = libraries,
                playlistsAvailable = playlistsAvailable,
                resume = resume,
                nextUp = nextUp,
                latestByLibrary = latest
            )
        }
    }
}
