package app.picnic.player.data.seerr

import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.plugin.PicnicPluginClient
import app.picnic.player.di.IoDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class SeerrLinkState {
    /** No URL / credentials for the active User Session. */
    Unlinked,

    /** Cookie (or renew password) present and client ready. */
    Linked,

    /** Silent renew failed — user must re-enter password in Settings. */
    NeedsRelink
}

data class SeerrSessionState(
    val linkState: SeerrLinkState = SeerrLinkState.Unlinked,
    val serverUrl: String? = null,
    val user: SeerrUser? = null,
    val showDiscover: Boolean = true,
    val cacheImages: Boolean = false,
    val authMethod: SeerrAuthMethod = SeerrAuthMethod.JELLYFIN
)

/**
 * Seerr connection + API facade. One active link per Jellyfin User Session;
 * URL is scoped to the Jellyfin Server Connection.
 */
@Singleton
class SeerrRepository @Inject constructor(
    private val api: SeerrApiClient,
    private val store: SeerrCredentialStore,
    private val authRepository: AuthRepository,
    private val pluginClient: PicnicPluginClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val _state = MutableStateFlow(SeerrSessionState())
    val state: StateFlow<SeerrSessionState> = _state.asStateFlow()

    val isDiscoverVisible: Boolean
        get() = _state.value.linkState == SeerrLinkState.Linked && _state.value.showDiscover

    private suspend inline fun <T> onIo(crossinline block: () -> T): T = withContext(ioDispatcher) { block() }

    /** Attach Seerr for the active Jellyfin session (profile switch / cold start). */
    suspend fun attachActiveSession() {
        val session = authRepository.activeSession()
        if (session == null) {
            clearRuntime()
            return
        }
        attach(session)
    }

    suspend fun attach(session: UserSession) {
        val serverId = session.server.id
        val userId = session.userId
        var url = store.serverUrl(serverId)
        val showDiscover = store.showDiscover(serverId, userId)
        // Companion-plugin prefill: pull the admin-configured Seerr URL and store it
        // where a manually-entered URL would go, so the Account page is pre-populated. Only
        // when the field is empty or a value we ourselves prefilled — never clobber a URL the
        // user typed. Server-side `Seerr.Enabled` gates it.
        if (url.isNullOrBlank() || store.serverUrlSource(serverId) == SeerrUrlSource.PLUGIN) {
            url = prefillFromPlugin(session, serverId, url) ?: url
        }
        if (url.isNullOrBlank()) {
            clearRuntime()
            _state.value = SeerrSessionState(showDiscover = showDiscover)
            return
        }
        api.setBaseUrl(url)
        val cookie = store.sessionCookie(serverId, userId)
        if (cookie != null) {
            api.loadCookie(url, cookie)
            val user = runCatching { onIo { api.authMe() } }.getOrNull()
            if (user != null) {
                persistCookie(url, serverId, userId)
                val settings = runCatching { onIo { api.publicSettings() } }.getOrNull()
                _state.value = SeerrSessionState(
                    linkState = SeerrLinkState.Linked,
                    serverUrl = url,
                    user = user,
                    showDiscover = showDiscover,
                    cacheImages = settings?.cacheImages == true
                )
                return
            }
        }
        val password = store.jellyfinPassword(serverId, userId)
        if (!password.isNullOrBlank()) {
            val renewed = runCatching {
                loginInternal(session, url, password, persistPassword = true)
            }.isSuccess
            if (renewed) {
                _state.value = _state.value.copy(showDiscover = showDiscover)
                return
            }
            markNeedsRelink(serverId, userId, url, showDiscover)
            return
        }
        if (cookie != null) {
            markNeedsRelink(serverId, userId, url, showDiscover)
        } else {
            clearRuntime()
            _state.value = SeerrSessionState(
                serverUrl = url,
                showDiscover = showDiscover
            )
        }
    }

    /**
     * Ask the companion plugin for the admin-configured Seerr URL and persist it when it's
     * new. Returns the stored (normalised) URL if it changed, else null. Silent on any
     * failure — a server without the plugin just yields null.
     */
    private suspend fun prefillFromPlugin(
        session: UserSession,
        serverId: String,
        currentUrl: String?
    ): String? {
        val seerr = onIo { pluginClient.info(session) }?.seerr ?: return null
        if (!seerr.enabled) return null
        val discovered = seerr.url?.takeIf { it.isNotBlank() } ?: return null
        if (SeerrCredentialStore.normalizeBaseUrl(discovered) == currentUrl) return null
        store.setServerUrl(serverId, discovered)
        store.setServerUrlSource(serverId, SeerrUrlSource.PLUGIN)
        return store.serverUrl(serverId)
    }

    /**
     * Connect with Jellyfin password. Username comes from the active User Session.
     * Stores encrypted password for silent renew until token auth exists.
     */
    suspend fun connect(urlInput: String, password: String): Result<SeerrUser> {
        val session = authRepository.activeSession()
            ?: return Result.failure(IllegalStateException("No active Jellyfin session"))
        val url = SeerrCredentialStore.normalizeBaseUrl(urlInput)
        return runCatching {
            loginInternal(session, url, password, persistPassword = true)
        }.onSuccess {
            // The user committed a URL via the Account form — take ownership so plugin
            // prefill won't overwrite it on later sessions.
            store.setServerUrlSource(session.server.id, SeerrUrlSource.USER)
        }
    }

    /** Drop this user's Seerr session + password; keep URL. */
    suspend fun disconnect() {
        val session = authRepository.activeSession() ?: return
        store.disconnectUser(session.server.id, session.userId)
        api.clearCookies()
        val url = store.serverUrl(session.server.id)
        _state.value = SeerrSessionState(
            linkState = SeerrLinkState.Unlinked,
            serverUrl = url,
            showDiscover = store.showDiscover(session.server.id, session.userId)
        )
    }

    suspend fun setShowDiscover(enabled: Boolean) {
        val session = authRepository.activeSession() ?: return
        store.setShowDiscover(session.server.id, session.userId, enabled)
        _state.value = _state.value.copy(showDiscover = enabled)
    }

    suspend fun forgetUser(serverId: String, userId: String) {
        store.forgetUser(serverId, userId)
        val active = authRepository.activeSession()
        if (active?.server?.id == serverId && active.userId == userId) {
            clearRuntime()
        }
    }

    suspend fun forgetServer(serverId: String, userIds: List<String>) {
        store.forgetServer(serverId, userIds)
        val active = authRepository.activeSession()
        if (active?.server?.id == serverId) {
            clearRuntime()
        }
    }

    suspend fun discoverRows(): List<SeerrDiscoverRow> = authed {
        val genres = genreNamesById()
        listOf(
            "Trending" to api.discoverTrending(),
            "Popular movies" to api.discoverMovies(),
            "Popular TV" to api.discoverTv(),
            "Upcoming movies" to api.discoverMoviesUpcoming(),
            "Upcoming TV" to api.discoverTvUpcoming()
        ).mapNotNull { (title, page) ->
            val items = page.results.mapNotNull { it.toCatalogItem(genres) }
            if (items.isEmpty()) null else SeerrDiscoverRow(title, items)
        }
    }

    suspend fun search(query: String): List<SeerrCatalogItem> = authed {
        val genres = genreNamesById()
        api.search(query).results.mapNotNull { it.toCatalogItem(genres) }
    }

    /** Resolve TMDB genre ids → names (cached for the process lifetime). */
    private var cachedGenreNames: Map<Int, String>? = null

    /** TMDB title/poster cache for Settings request rows. Cleared with runtime. */
    private val titleCache = ConcurrentHashMap<SeerrTitleCacheKey, SeerrCachedTitle>()

    private fun genreNamesById(): Map<Int, String> {
        cachedGenreNames?.let { return it }
        val movie = runCatching { api.movieGenres() }.getOrDefault(emptyList())
        val tv = runCatching { api.tvGenres() }.getOrDefault(emptyList())
        val map = (movie + tv)
            .mapNotNull { g -> g.name?.takeIf { it.isNotBlank() }?.let { g.id to it } }
            .toMap()
        cachedGenreNames = map
        return map
    }

    suspend fun movie(id: Int): SeerrMovieDetails = authed { api.movie(id) }

    suspend fun tv(id: Int): SeerrTvDetails = authed { api.tv(id) }

    suspend fun movieRecommendations(id: Int): List<SeerrCatalogItem> = authed {
        val genres = genreNamesById()
        api.movieRecommendations(id).results.mapNotNull {
            it.toCatalogItem(genres, defaultMediaType = SeerrMediaType.MOVIE)
        }.take(SeerrRecommendationsLimit)
    }

    suspend fun tvRecommendations(id: Int): List<SeerrCatalogItem> = authed {
        val genres = genreNamesById()
        api.tvRecommendations(id).results.mapNotNull {
            it.toCatalogItem(genres, defaultMediaType = SeerrMediaType.TV)
        }.take(SeerrRecommendationsLimit)
    }

    suspend fun person(id: Int): SeerrPersonDetails = authed { api.person(id) }

    /** Mixed cast+crew credits, deduped by `(mediaType, tmdbId)`. Uncapped. */
    suspend fun personCredits(id: Int): List<SeerrPersonCredit> = authed {
        val combined = api.personCombinedCredits(id)
        mixPersonCredits(combined.cast, combined.crew)
    }

    /** Detail enrich for Discover hero (runtime / seasons / cert / year range). No ratings. */
    suspend fun enrichCatalogItem(item: SeerrCatalogItem): SeerrCatalogItem = authed {
        when (item.mediaType) {
            SeerrMediaType.MOVIE -> api.movie(item.tmdbId).toCatalogItem()
            SeerrMediaType.TV -> api.tv(item.tmdbId).toCatalogItem()
        }
    }

    suspend fun requestMovie(tmdbId: Int): SeerrMediaRequest {
        val result = authed {
            api.createRequest(SeerrCreateRequestBody(mediaType = "movie", mediaId = tmdbId))
        }
        persistCookie()
        return result
    }

    suspend fun requestTv(
        tmdbId: Int,
        seasons: List<Int>,
        existingRequestId: Int?
    ): SeerrMediaRequest {
        val result = authed {
            if (existingRequestId != null) {
                api.updateRequest(
                    existingRequestId,
                    SeerrUpdateRequestBody(mediaType = "tv", seasons = seasons)
                )
            } else {
                api.createRequest(
                    SeerrCreateRequestBody(
                        mediaType = "tv",
                        mediaId = tmdbId,
                        seasons = seasons
                    )
                )
            }
        }
        persistCookie()
        return result
    }

    suspend fun cancelRequest(requestId: Int) {
        authed { api.deleteRequest(requestId) }
        persistCookie()
    }

    suspend fun myRequests(): List<SeerrMediaRequest> {
        val result = authed {
            val user = _state.value.user ?: api.authMe()
            user to api.listRequests(filter = "all").results.filter { it.requestedBy?.id == user.id }
        }
        if (_state.value.user == null) {
            _state.value = _state.value.copy(user = result.first)
        }
        return result.second
    }

    /**
     * Display rows from inline media fields + in-memory title cache (no network).
     * Call [hydrateRequestDisplays] to fill missing titles via movie/tv detail.
     */
    fun requestDisplaysCached(requests: List<SeerrMediaRequest>): List<SeerrRequestDisplay> {
        for (req in requests) {
            val key = req.titleCacheKeyOrNull() ?: continue
            val title = req.mediaTitleOrNull() ?: continue
            titleCache.putIfAbsent(key, SeerrCachedTitle(title, req.mediaPosterOrNull()))
        }
        return requestDisplaysFromCache(requests, titleCache)
    }

    /**
     * Fetch `/movie` or `/tv` for requests missing a title or year metadata. Bounded concurrency
     * avoids N+1 jank on TV; results land in [titleCache] for the process lifetime.
     */
    suspend fun hydrateRequestDisplays(
        requests: List<SeerrMediaRequest>
    ): List<SeerrRequestDisplay> {
        val missing = requests.mapNotNull { req ->
            val key = req.titleCacheKeyOrNull() ?: return@mapNotNull null
            val cached = titleCache[key]
            val needsTitle = req.mediaTitleOrNull() == null && cached == null
            val needsYear = cached == null || !cached.hasYearMeta(key.mediaType)
            if (!needsTitle && !needsYear) return@mapNotNull null
            key
        }.distinct()
        if (missing.isNotEmpty()) {
            missing.chunked(TITLE_HYDRATE_CONCURRENCY).forEach { chunk ->
                coroutineScope {
                    chunk.map { key ->
                        async {
                            val cached = runCatching { fetchTitle(key) }.getOrNull() ?: return@async
                            titleCache[key] = cached
                        }
                    }.awaitAll()
                }
            }
        }
        return requestDisplaysFromCache(requests, titleCache)
    }

    private suspend fun fetchTitle(key: SeerrTitleCacheKey): SeerrCachedTitle {
        val existing = titleCache[key]
        return when (key.mediaType) {
            SeerrMediaType.MOVIE -> {
                val details = movie(key.tmdbId)
                SeerrCachedTitle(
                    title = details.title?.takeIf { it.isNotBlank() }
                        ?: existing?.title
                        ?: "Untitled",
                    posterPath = details.posterPath?.takeIf { it.isNotBlank() }
                        ?: existing?.posterPath,
                    releaseDate = details.releaseDate
                )
            }
            SeerrMediaType.TV -> {
                val details = tv(key.tmdbId)
                SeerrCachedTitle(
                    title = details.name?.takeIf { it.isNotBlank() }
                        ?: existing?.title
                        ?: "Untitled",
                    posterPath = details.posterPath?.takeIf { it.isNotBlank() }
                        ?: existing?.posterPath,
                    releaseDate = details.firstAirDate,
                    lastAirDate = details.lastAirDate,
                    seriesStatus = details.status
                )
            }
        }
    }

    private companion object {
        const val TITLE_HYDRATE_CONCURRENCY = 4
    }

    /**
     * Whether the primary Request CTA should be offered.
     *
     * Movies: blocked for pending/processing/available and when an active
     * request already exists. [SeerrMediaStatus.PARTIALLY_AVAILABLE] is treated
     * as not requestable for movies.
     * TV: partial availability stays allowed so remaining seasons can be requested.
     */
    fun canRequest(
        user: SeerrUser?,
        mediaType: SeerrMediaType,
        status: Int?,
        hasActiveRequest: Boolean = false
    ): Boolean = canRequestSeerrMedia(user, mediaType, status, hasActiveRequest)

    /** Pending or approved request — not declined/failed/completed. */
    fun isActiveRequest(request: SeerrMediaRequest?): Boolean {
        if (request == null) return false
        return when (request.status) {
            SeerrRequestStatus.DECLINED,
            SeerrRequestStatus.FAILED,
            SeerrRequestStatus.COMPLETED
            -> false
            else -> true
        }
    }

    fun canCancel(user: SeerrUser?, request: SeerrMediaRequest?): Boolean {
        if (user == null || request == null) return false
        // Seerr: MANAGE_REQUESTS may delete any; otherwise only own *pending* requests.
        if (SeerrPermission.has(user.permissions, SeerrPermission.MANAGE_REQUESTS)) return true
        return request.requestedBy?.id == user.id &&
            request.status == SeerrRequestStatus.PENDING
    }

    private suspend fun <T> authed(block: () -> T): T {
        ensureLinkedOrThrow()
        return try {
            onIo { block() }
        } catch (e: SeerrHttpException) {
            if (e.code != 401 && e.code != 403) throw e
            silentRenewOrRelink()
            onIo { block() }
        }
    }

    private fun ensureLinkedOrThrow() {
        if (_state.value.linkState != SeerrLinkState.Linked) {
            throw IllegalStateException("Seerr not linked")
        }
    }

    private suspend fun silentRenewOrRelink() {
        val session = authRepository.activeSession()
            ?: throw IllegalStateException("Seerr not linked")
        val url = store.serverUrl(session.server.id)
            ?: throw IllegalStateException("Seerr not linked")
        val password = store.jellyfinPassword(session.server.id, session.userId)
        if (password.isNullOrBlank()) {
            markNeedsRelink(
                session.server.id,
                session.userId,
                url,
                store.showDiscover(session.server.id, session.userId)
            )
            throw IllegalStateException("Seerr session expired")
        }
        runCatching {
            loginInternal(session, url, password, persistPassword = true)
        }.getOrElse {
            markNeedsRelink(
                session.server.id,
                session.userId,
                url,
                store.showDiscover(session.server.id, session.userId)
            )
            throw IllegalStateException("Seerr session expired")
        }
    }

    private suspend fun loginInternal(
        session: UserSession,
        url: String,
        password: String,
        persistPassword: Boolean
    ): SeerrUser {
        val showDiscover = store.showDiscover(session.server.id, session.userId)
        val user = onIo {
            api.clearCookies()
            api.setBaseUrl(url)
            api.status()
            api.authJellyfin(session.username, password)
            api.authMe()
        }
        store.setServerUrl(session.server.id, url)
        if (persistPassword) {
            store.setJellyfinPassword(session.server.id, session.userId, password)
        }
        store.setSeerrUserId(session.server.id, session.userId, user.id)
        persistCookie(url, session.server.id, session.userId)
        val settings = runCatching { onIo { api.publicSettings() } }.getOrNull()
        _state.value = SeerrSessionState(
            linkState = SeerrLinkState.Linked,
            serverUrl = url,
            user = user,
            showDiscover = showDiscover,
            cacheImages = settings?.cacheImages == true,
            authMethod = SeerrAuthMethod.JELLYFIN
        )
        return user
    }

    private suspend fun persistCookie(
        url: String? = null,
        serverId: String? = null,
        userId: String? = null
    ) {
        val resolvedUrl = url ?: _state.value.serverUrl ?: return
        val session = authRepository.activeSession()
        val resolvedServerId = serverId ?: session?.server?.id ?: return
        val resolvedUserId = userId ?: session?.userId ?: return
        val sid = api.exportConnectSid(resolvedUrl) ?: return
        store.setSessionCookie(resolvedServerId, resolvedUserId, sid)
    }

    private suspend fun markNeedsRelink(
        serverId: String,
        userId: String,
        url: String,
        showDiscover: Boolean
    ) {
        store.disconnectUser(serverId, userId)
        api.clearCookies()
        _state.value = SeerrSessionState(
            linkState = SeerrLinkState.NeedsRelink,
            serverUrl = url,
            showDiscover = showDiscover
        )
    }

    private fun clearRuntime() {
        api.clearCookies()
        api.setBaseUrl(null)
        cachedGenreNames = null
        titleCache.clear()
        _state.value = SeerrSessionState()
    }
}
