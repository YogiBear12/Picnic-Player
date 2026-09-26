package app.picnic.player.data.seerr

import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserScope
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.plugin.PicnicPluginClient
import app.picnic.player.di.IoDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class SeerrLinkState {
    Unlinked,

    Linked,

    NeedsRelink
}

data class SeerrSessionState(
    val linkState: SeerrLinkState = SeerrLinkState.Unlinked,
    val serverUrl: String? = null,
    val user: SeerrUser? = null,
    val showDiscover: Boolean = true,
    val cacheImages: Boolean = false
)

@Singleton
class SeerrRepository @Inject constructor(
    private val api: SeerrApiClient,
    private val store: SeerrCredentialStore,
    private val authRepository: AuthRepository,
    private val pluginClient: PicnicPluginClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    @Volatile private var attachedUser: UserScope? = null

    private val _state = MutableStateFlow(SeerrSessionState())
    val state: StateFlow<SeerrSessionState> = _state.asStateFlow()

    val isDiscoverVisible: Boolean
        get() = _state.value.linkState == SeerrLinkState.Linked && _state.value.showDiscover

    private suspend inline fun <T> onIo(crossinline block: () -> T): T = withContext(ioDispatcher) { block() }

    suspend fun attach(session: UserSession) {
        val serverId = session.server.id
        val userId = session.userId
        if (attachedUser != session.scope) {
            clearRuntime()
            attachedUser = session.scope
        }
        var url = store.serverUrl(serverId)
        val showDiscover = store.showDiscover(serverId, userId)
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
            val user = attempt { onIo { api.authMe() } }
            if (user != null) {
                persistCookie(url, serverId, userId)
                val settings = attempt { onIo { api.publicSettings() } }
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
        val credentials = store.credentials(serverId, userId)
        if (credentials != null) {
            val renewed = attempt { loginInternal(session, url, credentials) } != null
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

    /** Like `runCatching`, but a cancelled attach stops instead of writing the previous user's state. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

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

    suspend fun connect(urlInput: String, credentials: SeerrCredentials): Result<SeerrUser> {
        val session = authRepository.activeSession()
            ?: return Result.failure(IllegalStateException("No active Jellyfin session"))
        val url = SeerrCredentialStore.normalizeBaseUrl(urlInput)
        return runCatching {
            try {
                loginInternal(session, url, credentials)
            } catch (e: SeerrHttpException) {
                throw if (credentials is SeerrCredentials.Local) localFailure(e) else e
            }
        }.onSuccess {
            store.setServerUrlSource(session.server.id, SeerrUrlSource.USER)
        }
    }

    /**
     * `/auth/local` answers 403 for wrong credentials, and 500 both for "local sign-in disabled"
     * and for any other failure, so the server's `localLogin` flag tells those apart.
     */
    private suspend fun localFailure(e: SeerrHttpException): Exception {
        if (e.code == 403) return IllegalStateException("Incorrect email or password")
        val settings = runCatching { onIo { api.publicSettings() } }.getOrNull()
        return if (settings?.localLogin == false) IllegalStateException("Local sign-in is disabled on this Seerr server") else e
    }

    suspend fun lastLogin(): SeerrLastLogin = authRepository.activeSession()?.let { store.lastLogin(it.server.id, it.userId) } ?: SeerrLastLogin()

    suspend fun disconnect() {
        _issues.value = null
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
        DiscoverRowKind.entries.mapNotNull { kind ->
            val page = when (kind) {
                DiscoverRowKind.TRENDING -> api.discoverTrending()
                DiscoverRowKind.POPULAR_MOVIES -> api.discoverMovies()
                DiscoverRowKind.POPULAR_TV -> api.discoverTv()
                DiscoverRowKind.UPCOMING_MOVIES -> api.discoverMoviesUpcoming()
                DiscoverRowKind.UPCOMING_TV -> api.discoverTvUpcoming()
            }
            val items = page.results.mapNotNull { it.toCatalogItem(genres) }
            if (items.isEmpty()) null else SeerrDiscoverRow(kind, items)
        }
    }

    suspend fun search(query: String): List<SeerrCatalogItem> = authed {
        val genres = genreNamesById()
        api.search(query).results.mapNotNull { it.toCatalogItem(genres) }
    }

    private var cachedGenreNames: Map<Int, String>? = null

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

    suspend fun personCredits(id: Int): List<SeerrPersonCredit> = authed {
        val combined = api.personCombinedCredits(id)
        mixPersonCredits(combined.cast, combined.crew)
    }

    suspend fun personCombinedCredits(id: Int): SeerrPersonCombinedCredits = authed {
        api.personCombinedCredits(id)
    }

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

    suspend fun reportIssue(
        tmdbId: Int,
        isTv: Boolean,
        type: SeerrIssueType,
        message: String,
        problemSeason: Int = SEERR_ISSUE_ALL,
        problemEpisode: Int = SEERR_ISSUE_ALL
    ) {
        authed {
            val mediaId = (if (isTv) api.tv(tmdbId).mediaInfo?.id else api.movie(tmdbId).mediaInfo?.id)
                ?: throw IllegalStateException("Media not known to Seerr")
            api.createIssue(
                SeerrCreateIssueBody(
                    issueType = type.id,
                    message = message,
                    mediaId = mediaId,
                    problemSeason = problemSeason,
                    problemEpisode = problemEpisode
                )
            )
        }
        persistCookie()
    }

    suspend fun cancelRequest(requestId: Int) {
        authed { api.deleteRequest(requestId) }
        persistCookie()
    }

    private fun currentUser(): SeerrUser {
        _state.value.user?.let { return it }
        val user = api.authMe()
        _state.value = _state.value.let { if (it.user == null) it.copy(user = user) else it }
        return _state.value.user ?: user
    }

    suspend fun myRequests(): List<SeerrMediaRequest> = authed {
        val userId = currentUser().id
        api.listRequests(filter = "all").results.filter { it.requestedBy?.id == userId }
    }

    private val _issues = MutableStateFlow<List<SeerrIssue>?>(null)
    val issues: StateFlow<List<SeerrIssue>?> = _issues.asStateFlow()

    suspend fun refreshIssues(): List<SeerrIssue> = myIssues().also { _issues.value = it }

    suspend fun myIssues(): List<SeerrIssue> = authed {
        val userId = currentUser().id
        val mine = mutableListOf<SeerrIssue>()
        var skip = 0
        repeat(ISSUE_PAGE_LIMIT) {
            val page = api.issues(take = ISSUE_PAGE_SIZE, skip = skip)
            mine += page.results.filter { it.createdBy?.id == userId }
            skip += ISSUE_PAGE_SIZE
            val total = page.pageInfo?.results
            if (page.results.size < ISSUE_PAGE_SIZE || (total != null && skip >= total)) return@authed mine
        }
        mine
    }

    suspend fun issue(issueId: Int): SeerrIssue = authed { api.issue(issueId) }

    suspend fun addIssueComment(issueId: Int, message: String): SeerrIssue {
        val result = authed { api.addIssueComment(issueId, message) }
        persistCookie()
        return result
    }

    suspend fun setIssueResolved(issueId: Int, resolved: Boolean): SeerrIssue {
        val result = authed { api.setIssueStatus(issueId, resolved) }
        persistCookie()
        return result
    }

    suspend fun deleteIssue(issueId: Int) {
        authed { api.deleteIssue(issueId) }
        persistCookie()
    }

    private suspend fun warmTitles(keys: List<SeerrTitleCacheKey>) {
        keys.chunked(TITLE_HYDRATE_CONCURRENCY).forEach { chunk ->
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

    suspend fun hydrateIssueDisplays(issues: List<SeerrIssue>): List<SeerrIssueDisplay> {
        val keys = issues.mapNotNull { it.titleCacheKeyOrNull() }.distinct()
        val missing = keys.filter { titleCache[it] == null }
        warmTitles(missing)
        return issues.map { issue ->
            val cached = issue.titleCacheKeyOrNull()?.let { titleCache[it] }
            SeerrIssueDisplay(
                issue = issue,
                title = cached?.title ?: "Issue #${issue.id}",
                posterPath = cached?.posterPath,
                yearLabel = cached?.releaseDate?.take(4)?.takeIf { it.length == 4 }
            )
        }
    }

    fun requestDisplaysCached(requests: List<SeerrMediaRequest>): List<SeerrRequestDisplay> {
        for (req in requests) {
            val key = req.titleCacheKeyOrNull() ?: continue
            val title = req.mediaTitleOrNull() ?: continue
            titleCache.putIfAbsent(key, SeerrCachedTitle(title, req.mediaPosterOrNull()))
        }
        return requestDisplaysFromCache(requests, titleCache)
    }

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
        warmTitles(missing)
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
        const val ISSUE_PAGE_SIZE = 100
        const val ISSUE_PAGE_LIMIT = 50
    }

    fun canRequest(
        user: SeerrUser?,
        mediaType: SeerrMediaType,
        status: Int?,
        hasActiveRequest: Boolean = false
    ): Boolean = canRequestSeerrMedia(user, mediaType, status, hasActiveRequest)

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
        val credentials = store.credentials(session.server.id, session.userId)
        if (credentials == null) {
            markNeedsRelink(
                session.server.id,
                session.userId,
                url,
                store.showDiscover(session.server.id, session.userId)
            )
            throw IllegalStateException("Seerr session expired")
        }
        runCatching {
            loginInternal(session, url, credentials)
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
        credentials: SeerrCredentials
    ): SeerrUser {
        val showDiscover = store.showDiscover(session.server.id, session.userId)
        val user = onIo {
            api.clearCookies()
            api.setBaseUrl(url)
            api.status()
            when (credentials) {
                is SeerrCredentials.Jellyfin -> api.authJellyfin(session.username, credentials.password)
                is SeerrCredentials.Local -> api.authLocal(credentials.email, credentials.password)
            }
            api.authMe()
        }
        store.setServerUrl(session.server.id, url)
        store.setCredentials(session.server.id, session.userId, credentials)
        store.setSeerrUserId(session.server.id, session.userId, user.id)
        persistCookie(url, session.server.id, session.userId)
        val settings = runCatching { onIo { api.publicSettings() } }.getOrNull()
        _state.value = SeerrSessionState(
            linkState = SeerrLinkState.Linked,
            serverUrl = url,
            user = user,
            showDiscover = showDiscover,
            cacheImages = settings?.cacheImages == true
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
        attachedUser = null
        api.clearCookies()
        api.setBaseUrl(null)
        cachedGenreNames = null
        titleCache.clear()
        _state.value = SeerrSessionState()
    }
}
