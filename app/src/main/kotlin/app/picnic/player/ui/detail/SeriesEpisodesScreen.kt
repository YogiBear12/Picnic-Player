@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package app.picnic.player.ui.detail

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.paging.map
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinFactory
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.paging.EpisodePagingSource
import app.picnic.player.di.IoDispatcher
import app.picnic.player.playback.LocalThemeMusicPlayer
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.LocalAmbientBackgrounds
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import app.picnic.player.ui.browse.CardTimeLeftBadge
import app.picnic.player.ui.browse.CardWatchedBadge
import app.picnic.player.ui.browse.HeroInfoLine
import app.picnic.player.ui.browse.HeroSpecs
import app.picnic.player.ui.browse.ShortDateFormat
import app.picnic.player.ui.browse.minutesLeft
import app.picnic.player.ui.browse.runtimeMinutes
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto

private const val TAG = "SeriesEpisodes"

@HiltViewModel
class SeriesEpisodesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val jellyfin: JellyfinFactory,
    private val changeBus: LibraryChangeBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    var seasons by mutableStateOf<List<BaseItemDto>>(emptyList())
        private set
    var session by mutableStateOf<UserSession?>(null)
        private set
    var seriesItem by mutableStateOf<BaseItemDto?>(null)
        private set
    var seasonsError by mutableStateOf<Throwable?>(null)
        private set

    // The series/season currently on screen — so the change bus can reload the right episodes
    // when an episode's watched/progress state changes (here, in the player, or elsewhere).
    private var currentSeriesId: String? = null

    private val _selectedSeasonId = MutableStateFlow<String?>(null)
    private val _refreshTrigger = MutableStateFlow(0)

    // Track local optimistic updates to apply to the PagingData stream
    private val episodeMutations = MutableStateFlow<Map<String, (BaseItemDto) -> BaseItemDto>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val episodes: Flow<PagingData<BaseItemDto>> = combine(
        _selectedSeasonId.filterNotNull().distinctUntilChanged(),
        _refreshTrigger
    ) { seasonId, _ -> seasonId }
        .flatMapLatest { seasonId ->
            val currentSession = session
            val seriesId = currentSeriesId
            if (currentSession == null || seriesId == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 50, enablePlaceholders = false, initialLoadSize = 50),
                    pagingSourceFactory = {
                        val api = jellyfin.api(currentSession.server.baseUrl, currentSession.accessToken)
                        EpisodePagingSource(
                            api = api,
                            ioDispatcher = ioDispatcher,
                            seriesId = seriesId,
                            seasonId = seasonId,
                            userId = currentSession.userId,
                            onTotalRecordCount = { count ->
                                val currentSeasons = seasons
                                val seasonIndex = currentSeasons.indexOfFirst { it.id.toString() == seasonId }
                                if (seasonIndex != -1 && currentSeasons[seasonIndex].childCount != count) {
                                    val updatedSeasons = currentSeasons.toMutableList()
                                    updatedSeasons[seasonIndex] = updatedSeasons[seasonIndex].copy(childCount = count)
                                    seasons = updatedSeasons
                                }
                            }
                        )
                    }
                ).flow.cachedIn(viewModelScope).combine(episodeMutations) { pagingData, mutations ->
                    if (mutations.isEmpty()) {
                        pagingData
                    } else {
                        pagingData.map { item -> mutations[item.id.toString()]?.invoke(item) ?: item }
                    }
                }
            }
        }
        .cachedIn(viewModelScope)
    init {
        viewModelScope.launch {
            session = authRepository.activeSession()
        }
        viewModelScope.launch {
            changeBus.events.collect { change ->
                if (change !is LibraryChange.ItemUpdated) return@collect
                val seriesId = currentSeriesId ?: return@collect
                // With Paging, we can't easily check if episodes contains the item synchronously,
                // so we just refresh if it might be this series.
                val affectsThisSeries = change.seriesId == seriesId || change.itemId == seriesId
                if (affectsThisSeries) {
                    _refreshTrigger.value++
                    loadSeasons(seriesId)
                }
            }
        }
    }

    // Loads seasons + series metadata only. Episode loading is driven by the selected
    // season from the screen so it survives composition disposal (e.g. returning from player).
    fun loadSeasons(seriesId: String) {
        val currentSession = session ?: return
        currentSeriesId = seriesId
        // The series metadata (for the poster) and the season list are independent calls. Run them
        // in separate coroutines so the season list isn't blocked behind the series-item fetch —
        // otherwise the left panel stays blank until both complete.
        viewModelScope.launch {
            runCatching { mediaRepository.item(currentSession, UUID.fromString(seriesId)) }
                .onSuccess { seriesItem = it }
                .onFailure { Log.w(TAG, "Series item load failed (series=$seriesId)", it) }
        }
        viewModelScope.launch {
            try {
                seasonsError = null
                // On IO: the SDK reads the response body on the calling dispatcher, and
                // viewModelScope is Main — a blocking socket read there throws
                // NetworkOnMainThreadException (blank season panel) and janks release builds.
                val fastResponse = withContext(ioDispatcher) {
                    val api = jellyfin.api(currentSession.server.baseUrl, currentSession.accessToken)
                    // Fast path: load seasons instantly without expensive CHILD_COUNT
                    api.tvShowsApi.getSeasons(
                        seriesId = UUID.fromString(seriesId),
                        userId = UUID.fromString(currentSession.userId),
                        fields = emptyList()
                    )
                }
                val fetchedSeasons = fastResponse.content.items ?: emptyList()

                // Preserve any counts we already have in case this is a reload (e.g. from changeBus)
                val existingCounts = seasons.associate { it.id.toString() to it.childCount }
                val mergedSeasons = fetchedSeasons.map { s ->
                    val existing = existingCounts[s.id.toString()]
                    if (existing != null) s.copy(childCount = existing) else s
                }

                seasons = mergedSeasons.sortedWith(compareBy({ it.indexNumber == 0 }, { it.indexNumber }))
            } catch (e: Exception) {
                // A swallowed failure here renders as a silently blank season panel.
                Log.e(TAG, "Season list load failed (series=$seriesId)", e)
                seasonsError = e
            }
        }
    }

    fun prefetchSeasonCounts(visibleIndices: List<Int>) {
        val currentSession = session ?: return
        val currentSeasons = seasons
        if (currentSeasons.isEmpty()) return

        val missingIds = mutableListOf<UUID>()
        for (idx in visibleIndices) {
            // Buffer around visible items
            for (i in (idx - 2)..(idx + 4)) {
                val s = currentSeasons.getOrNull(i)
                if (s != null && s.childCount == null) {
                    missingIds.add(s.id)
                }
            }
        }
        val distinctMissingIds = missingIds.distinct()
        if (distinctMissingIds.isEmpty()) return

        viewModelScope.launch {
            try {
                val fetchedCounts = mediaRepository.seasonCounts(currentSession, distinctMissingIds)
                if (fetchedCounts.isNotEmpty()) {
                    seasons = seasons.map { s ->
                        val count = fetchedCounts[s.id]
                        if (count != null && count > 0) s.copy(childCount = count) else s
                    }
                }
            } catch (e: Exception) {
                // Pre-fetch failed, ignore
            }
        }
    }

    fun loadEpisodes(seriesId: String, seasonId: String) {
        // Must not record the season before a session exists: the pager reads [session] at
        // collect time and distinctUntilChanged would suppress the retry that arrives with it.
        if (session == null) return
        currentSeriesId = seriesId
        _selectedSeasonId.value = seasonId
    }

    fun markWatched(episodeId: String, played: Boolean) {
        val currentSession = session ?: return
        val series = currentSeriesId?.let(UUID::fromString)

        // Optimistic local update via mutation flow
        val currentMutations = episodeMutations.value.toMutableMap()
        currentMutations[episodeId] = { ep -> ep.copy(userData = ep.userData?.copy(played = played)) }
        episodeMutations.value = currentMutations

        viewModelScope.launch {
            runCatching { mediaRepository.setWatched(currentSession, UUID.fromString(episodeId), played, series) }
        }
    }

    fun markFavorite(episodeId: String, favorite: Boolean) {
        val currentSession = session ?: return
        val series = currentSeriesId?.let(UUID::fromString)

        // Optimistic local update
        val currentMutations = episodeMutations.value.toMutableMap()
        currentMutations[episodeId] = { ep -> ep.copy(userData = ep.userData?.copy(isFavorite = favorite)) }
        episodeMutations.value = currentMutations

        viewModelScope.launch {
            runCatching { mediaRepository.setFavorite(currentSession, UUID.fromString(episodeId), favorite, series) }
        }
    }

    fun markSeasonWatched(seasonId: String, played: Boolean) {
        val currentSession = session ?: return
        val series = currentSeriesId?.let(UUID::fromString)

        seasons = seasons.map { s ->
            if (s.id.toString() == seasonId) {
                s.copy(userData = s.userData?.copy(played = played))
            } else {
                s
            }
        }

        viewModelScope.launch {
            runCatching {
                mediaRepository.setWatched(currentSession, UUID.fromString(seasonId), played, series)
            }
            // Season markPlayed cascades to episodes on the server; clear optimistic episode
            // patches and reload so badges match the new season play state.
            episodeMutations.value = emptyMap()
            _refreshTrigger.value++
            currentSeriesId?.let { loadSeasons(it) }
        }
    }

    fun markSeasonFavorite(seasonId: String, favorite: Boolean) {
        val currentSession = session ?: return
        val series = currentSeriesId?.let(UUID::fromString)

        seasons = seasons.map { s ->
            if (s.id.toString() == seasonId) {
                s.copy(userData = s.userData?.copy(isFavorite = favorite))
            } else {
                s
            }
        }

        viewModelScope.launch {
            runCatching {
                mediaRepository.setFavorite(currentSession, UUID.fromString(seasonId), favorite, series)
            }
        }
    }
}

@Composable
fun SeriesEpisodesScreen(
    seriesId: String,
    ambUrl: String?,
    onPlay: (String, Long?) -> Unit,
    onBack: () -> Unit,
    // When arriving from an episode card (e.g. Next Up), open on that episode's season
    // and put focus on the episode itself instead of the default first-unwatched.
    initialSeasonId: String? = null,
    initialFocusEpisodeId: String? = null,
    onGoToSeries: ((String) -> Unit)? = null,
    viewModel: SeriesEpisodesViewModel = hiltViewModel()
) {
    LaunchedEffect(seriesId, viewModel.session) {
        if (viewModel.session != null) viewModel.loadSeasons(seriesId)
    }

    // The series owns the theme here. Arriving from the series' detail screen finds the
    // owner already attached, so the track keeps playing instead of restarting; before the
    // early session return so the acquire/release pair runs exactly once per composition.
    val themeMusic = LocalThemeMusicPlayer.current
    DisposableEffect(seriesId) {
        val ownerId = runCatching { UUID.fromString(seriesId) }.getOrNull()
        ownerId?.let(themeMusic::acquire)
        onDispose { ownerId?.let(themeMusic::release) }
    }

    val session = viewModel.session ?: return

    val paletteUrl = ambUrl
        ?: viewModel.seriesItem?.let { JellyfinImages.primary(session, it, fillWidth = 240) }
    PublishBackdrop(BackdropSpec(backdropUrl = null, ambientUrl = paletteUrl))
    // Saveable so the chosen season survives composition disposal when navigating to the player.
    // Seeded from the launching episode's season so its listing opens on the right season.
    var selectedSeasonId by rememberSaveable { mutableStateOf(initialSeasonId) }
    // The episode that launched playback — focus is restored to it when the screen returns.
    // Seeded from the launching episode so it starts focused on first load (reuses the same
    // return-from-player focus path below).
    var pendingFocusEpisodeId by rememberSaveable { mutableStateOf(initialFocusEpisodeId) }

    LaunchedEffect(viewModel.seasons) {
        if (selectedSeasonId == null && viewModel.seasons.isNotEmpty()) {
            selectedSeasonId = viewModel.seasons.first().id.toString()
        }
    }

    // Drive episode loading from the selected season (not from loadSeasons), so returning from
    // the player reloads the season the user was actually on rather than resetting to season 1.
    LaunchedEffect(selectedSeasonId, viewModel.session) {
        val sid = selectedSeasonId ?: return@LaunchedEffect
        viewModel.loadEpisodes(seriesId, sid)
    }

    // OFF → static ocean wash; skip colour extraction entirely.
    val ambientOn = LocalAmbientBackgrounds.current

    val selectedSeasonIndex = viewModel.seasons.indexOfFirst { it.id.toString() == selectedSeasonId }.coerceAtLeast(0)
    val selectedSeason = viewModel.seasons.getOrNull(selectedSeasonIndex)

    // Created eagerly before selectedSeasonFr is derived so the map is always populated.
    val seasonFocusRequesters: Map<Int, FocusRequester> = remember(viewModel.seasons) {
        viewModel.seasons.indices.associateWith { FocusRequester() }
    }
    val selectedSeasonFr = seasonFocusRequesters[selectedSeasonIndex]
    val seasonListState = rememberLazyListState()

    // Bring the selected season into view once seasons load. Without this, arriving directly on
    // a later season (e.g. from a Next Up episode card) leaves that season's ListItem off-screen
    // and uncomposed, so D-pad Left / Back can't focus it and the episode list becomes a dead end.
    LaunchedEffect(viewModel.seasons) {
        if (viewModel.seasons.isEmpty()) return@LaunchedEffect
        val index = selectedSeasonIndex.coerceAtLeast(0)
        // Scroll so the selected season is slightly down the list (e.g. 4th item)
        // so that previous seasons are naturally visible without scrolling.
        val targetIndex = (index - 3).coerceAtLeast(0)
        seasonListState.scrollToItem(targetIndex)
    }

    // Opportunistically fetch counts for seasons as they scroll into view
    LaunchedEffect(seasonListState) {
        snapshotFlow { seasonListState.layoutInfo.visibleItemsInfo }
            .map { items -> items.map { it.index } }
            .distinctUntilChanged()
            .collect { visibleIndices ->
                viewModel.prefetchSeasonCounts(visibleIndices)
            }
    }

    // Single stable requester for D-pad Right from season list into episodes.
    val episodeListState = rememberLazyListState()
    val episodes = viewModel.episodes.collectAsLazyPagingItems()
    var episodeListHasFocus by remember { mutableStateOf(false) }
    // Last-focused episode in the current season. Updated on item focus so season→Right
    // lands on the episode the user left, not a stale load-time index whose requester may
    // be unattached (off-screen LazyColumn item).
    var targetEpisodeIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val episodeFocusRequesters = remember {
        object {
            private val map = mutableMapOf<Int, FocusRequester>()
            operator fun get(index: Int) = map.getOrPut(index) { FocusRequester() }
        }
    }

    /** Scroll the target episode into composition, then focus it (safe for LazyColumn). */
    fun focusTargetEpisode() {
        scope.launch {
            val count = episodes.itemCount
            if (count <= 0) return@launch
            val idx = targetEpisodeIndex.coerceIn(0, count - 1)
            targetEpisodeIndex = idx
            episodeListState.scrollToItem(idx)
            episodeFocusRequesters[idx].requestFocusWhenAttached(maxFrames = 20)
        }
    }

    var contextMenuEpisode by remember { mutableStateOf<BaseItemDto?>(null) }
    var contextMenuSeason by remember { mutableStateOf<BaseItemDto?>(null) }
    // Tracks the season we last auto-scrolled, so refreshing the same season's episodes
    // (e.g. after marking watched) doesn't yank the list back to the first unwatched item.
    var lastScrolledSeasonId by rememberSaveable { mutableStateOf<String?>(null) }

    // Reveal the whole page at once: hold a spinner over the ambient background until BOTH the
    // season list and the initial episode list have arrived, so the season panel never appears
    // blank next to a populated episode list. We only fetch the selected season's episodes (not
    // every season's), so the wait is just the slower of two concurrent calls.
    var initialLoadComplete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel.seasons, selectedSeasonId) {
        snapshotFlow { episodes.itemCount }.collect { count ->
            val selectedSeasonCountReady = viewModel.seasons.find { it.id.toString() == selectedSeasonId }?.childCount != null
            if (viewModel.seasons.isNotEmpty() && count > 0 && selectedSeasonCountReady) {
                initialLoadComplete = true
            }
        }
    }
    // Failsafe: a season with no episodes (or a slow/failed call) must never hang the spinner.
    LaunchedEffect(Unit) {
        delay(5000)
        initialLoadComplete = true
    }
    // A load error drops the spinner immediately so the error state underneath is visible.
    LaunchedEffect(Unit) {
        snapshotFlow { episodes.loadState.refresh }.collect {
            if (it is LoadState.Error) initialLoadComplete = true
        }
    }
    LaunchedEffect(viewModel.seasonsError) {
        if (viewModel.seasonsError != null) initialLoadComplete = true
    }

    // When episodes (re)load: restore focus to the episode we came back from, otherwise scroll
    // to the first in-progress/unwatched episode only when the season actually changed.
    var initialFocusRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { initialLoadComplete to episodes.itemCount }.collect { (complete, count) ->
            if (!complete || count == 0) return@collect
            val returningIndex = pendingFocusEpisodeId
                ?.let { id -> episodes.itemSnapshotList.indexOfFirst { it?.id?.toString() == id } }
                ?.takeIf { it >= 0 }
            if (returningIndex != null) {
                targetEpisodeIndex = returningIndex
                episodeListState.scrollToItem(returningIndex)
                lastScrolledSeasonId = selectedSeasonId
                // Request focus on the specific item's requester. This will safely wait for it to be attached.
                val fr = episodeFocusRequesters[returningIndex]
                fr.requestFocusWhenAttached(maxFrames = 20)
                pendingFocusEpisodeId = null
                initialFocusRequested = true
                return@collect
            }
            if (selectedSeasonId != lastScrolledSeasonId) {
                val target = episodes.itemSnapshotList.indexOfFirst { ep ->
                    ep != null && ((ep.userData?.playbackPositionTicks ?: 0L) > 0L || ep.userData?.played != true)
                }.coerceAtLeast(0)
                targetEpisodeIndex = target
                episodeListState.scrollToItem(target)
                lastScrolledSeasonId = selectedSeasonId
            }

            if (!initialFocusRequested) {
                val fr = episodeFocusRequesters[targetEpisodeIndex]
                fr.requestFocusWhenAttached(maxFrames = 20)
                initialFocusRequested = true
            }
        }
    }

    // Back from episodes → focus selected season; Back from seasons → nav pop (default).
    // With no seasons to land on (empty/failed season load) fall through to leaving the
    // screen — otherwise Back is consumed as a no-op and focus is trapped in the episode list.
    BackHandler(enabled = episodeListHasFocus) {
        if (selectedSeasonFr != null && viewModel.seasons.isNotEmpty()) {
            selectedSeasonFr.requestFocus()
        } else {
            onBack()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Kept composed while loading (alpha 0) so the season/episode focus + centering effects
        // position everything before it's revealed — no visible scroll jump on reveal.
        Row(Modifier.fillMaxSize().graphicsLayer { alpha = if (initialLoadComplete) 1f else 0f }) {
            // Sidebar: poster fills panel width, season list fills remaining height below
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(start = 60.dp, end = 16.dp)
            ) {
                Spacer(Modifier.height(76.dp))
                val seriesItem = viewModel.seriesItem
                if (seriesItem != null) {
                    val logoUrl = JellyfinImages.logo(session, seriesItem, fillWidth = 400)
                    if (logoUrl != null) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = seriesItem.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        )
                    } else {
                        Text(
                            text = seriesItem.name ?: "Series",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    val yearStr = (seriesItem.productionYear ?: seriesItem.premiereDate?.year)?.toString() ?: ""
                    val count = seriesItem.childCount ?: 0
                    val seasonsStr = if (count == 1) "1 Season" else "$count Seasons"
                    val meta = if (yearStr.isNotEmpty()) "$yearStr • $seasonsStr" else seasonsStr
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HeroInfoLine(
                            item = seriesItem,
                            meta = meta,
                            specs = HeroSpecs(
                                certificate = null,
                                resolution = null,
                                dynamicRange = null,
                                atmos = false,
                                audio = null,
                                hasSubtitles = false
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                }

                val seasonBringIntoViewSpec = remember {
                    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                    object : BringIntoViewSpec {
                        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                            // Anchor focus to roughly the 4th item's position to match standard TV LazyRow/Column behavior.
                            // We use `size * 3f` which perfectly aligns with our initial `scrollToItem(index - 3)`
                            // to ensure there's no visual jump when focus first lands.
                            val targetForLeadingEdge = size * 3f
                            return offset - targetForLeadingEdge
                        }
                    }
                }

                // A failed season fetch must surface here, not render a silently blank panel.
                val seasonsError = viewModel.seasonsError
                if (viewModel.seasons.isEmpty() && seasonsError != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Couldn't load seasons", color = PicnicColors.OnDark, textAlign = TextAlign.Center)
                        Text(
                            seasonsError.message ?: seasonsError.javaClass.simpleName,
                            style = MaterialTheme.typography.bodySmall,
                            color = PicnicColors.OnDarkMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 4
                        )
                        Button(onClick = { viewModel.loadSeasons(seriesId) }) { Text("Retry") }
                    }
                }

                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                CompositionLocalProvider(LocalBringIntoViewSpec provides seasonBringIntoViewSpec) {
                    LazyColumn(
                        state = seasonListState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            count = viewModel.seasons.size,
                            key = { viewModel.seasons[it].id.toString() }
                        ) { index ->
                            val season = viewModel.seasons[index]
                            val isSelected = season.id.toString() == selectedSeasonId
                            val fr = seasonFocusRequesters[index] ?: return@items
                            var seasonFocused by remember(season.id) { mutableStateOf(false) }
                            ListItem(
                                selected = isSelected,
                                onClick = {
                                    selectedSeasonId = season.id.toString()
                                    viewModel.loadEpisodes(seriesId, season.id.toString())
                                },
                                onLongClick = { contextMenuSeason = season },
                                headlineContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = season.name ?: "Season",
                                            color = Color.White,
                                            textAlign = if (isSelected) TextAlign.Start else TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f, fill = true)
                                                .then(if (seasonFocused) Modifier.basicMarquee() else Modifier)
                                        )
                                        val count = season.childCount
                                        if (isSelected && count != null && count > 0) {
                                            Text(
                                                text = if (count == 1) "1 episode" else "$count episodes",
                                                color = Color.White.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isSelected) Color.White.copy(alpha = 0.40f) else Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .focusRequester(fr)
                                    // Prefer last-focused episode when it is already composed;
                                    // onKeyEvent below covers the off-screen / unattached case.
                                    .focusProperties { right = episodeFocusRequesters[targetEpisodeIndex] }
                                    .onKeyEvent { event ->
                                        if (event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown) {
                                            focusTargetEpisode()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .onFocusChanged {
                                        seasonFocused = it.isFocused
                                        if (it.isFocused && !isSelected) {
                                            selectedSeasonId = season.id.toString()
                                            viewModel.loadEpisodes(seriesId, season.id.toString())
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // Episode list
            Box(Modifier.fillMaxSize()) {
                // A failed page load must surface, not render a silently blank pane.
                val refreshError = episodes.loadState.refresh as? LoadState.Error
                if (refreshError != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Couldn't load episodes", color = PicnicColors.OnDark)
                        Text(
                            refreshError.error.message ?: refreshError.error.javaClass.simpleName,
                            style = MaterialTheme.typography.bodySmall,
                            color = PicnicColors.OnDarkMuted,
                            maxLines = 3
                        )
                        Button(onClick = { episodes.retry() }) { Text("Retry") }
                    }
                }
                LazyColumn(
                    state = episodeListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer()
                        .onFocusChanged { episodeListHasFocus = it.hasFocus },
                    contentPadding = PaddingValues(start = 44.dp, end = 40.dp, top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = episodes.itemCount,
                        key = episodes.itemKey { it.id.toString() },
                        contentType = episodes.itemContentType { "episode" }
                    ) { index ->
                        val episode = episodes[index]
                        if (episode != null) {
                            EpisodeItem(
                                episode = episode,
                                session = session,
                                leftFocus = selectedSeasonFr,
                                enterFr = episodeFocusRequesters[index],
                                onFocused = { targetEpisodeIndex = index },
                                onPlay = { id, resumeTicks ->
                                    pendingFocusEpisodeId = id
                                    onPlay(id, resumeTicks)
                                },
                                onLongClick = { contextMenuEpisode = episode }
                            )
                        }
                    }
                }
            }
        }

        if (!initialLoadComplete) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = PicnicColors.Accent
            )
        }

        contextMenuEpisode?.let { ep ->
            EpisodeContextMenu(
                episode = ep,
                onDismiss = { contextMenuEpisode = null },
                onPlay = { ticks ->
                    contextMenuEpisode = null
                    pendingFocusEpisodeId = ep.id.toString()
                    onPlay(ep.id.toString(), ticks)
                },
                onMarkWatched = { played ->
                    viewModel.markWatched(ep.id.toString(), played)
                    contextMenuEpisode = null
                },
                onToggleFavorite = { fav ->
                    viewModel.markFavorite(ep.id.toString(), fav)
                    contextMenuEpisode = null
                },
                onGoToSeries = onGoToSeries
            )
        }

        contextMenuSeason?.let { season ->
            SeasonContextMenu(
                season = season,
                onDismiss = { contextMenuSeason = null },
                onMarkWatched = { played ->
                    viewModel.markSeasonWatched(season.id.toString(), played)
                    contextMenuSeason = null
                },
                onToggleFavorite = { fav ->
                    viewModel.markSeasonFavorite(season.id.toString(), fav)
                    contextMenuSeason = null
                },
                onGoToSeries = onGoToSeries?.let { go ->
                    { go(seriesId) }
                }
            )
        }
    }
}

@Composable
private fun EpisodeItem(
    episode: BaseItemDto,
    session: UserSession,
    leftFocus: FocusRequester?,
    enterFr: FocusRequester?,
    onFocused: () -> Unit = {},
    onPlay: (String, Long?) -> Unit,
    onLongClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val thumbShape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageUrl = JellyfinImages.primary(session, episode, fillWidth = 300)

        Card(
            // In-progress episode resumes from the saved position; the long-press menu
            // offers "Restart" (play from beginning) for the same episode.
            onClick = {
                val resumeTicks = episode.userData?.playbackPositionTicks?.takeIf { it > 0L }
                onPlay(episode.id.toString(), resumeTicks)
            },
            onLongClick = onLongClick,
            shape = CardDefaults.shape(thumbShape),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = CardDefaults.scale(focusedScale = 1.03f),
            border = CardDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent), shape = thumbShape),
                focusedBorder = Border(BorderStroke(CardFocusBorderWidth, Color.White), shape = thumbShape)
            ),
            glow = CardDefaults.glow(
                focusedGlow = rememberCardFocusGlow(Color.White, focused)
            ),
            modifier = Modifier
                .size(width = 200.dp, height = 112.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .then(if (leftFocus != null) Modifier.focusProperties { left = leftFocus } else Modifier)
                .then(if (enterFr != null) Modifier.focusRequester(enterFr) else Modifier)
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray)
                )
                val epProgress = ((episode.userData?.playedPercentage ?: 0.0) / 100.0).toFloat()
                if (epProgress > 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 6.dp, end = 6.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Black.copy(alpha = 0.50f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(epProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                }
                val epPlayed = episode.userData?.played ?: false
                val epRemaining = minutesLeft(episode)
                if (epRemaining != null && epProgress > 0.01f) {
                    CardTimeLeftBadge(epRemaining)
                } else if (epPlayed && epProgress < 0.01f) {
                    CardWatchedBadge()
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "S${episode.parentIndexNumber ?: "?"} E${episode.indexNumber ?: "?"}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = episode.name ?: "Unknown",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = if (focused) Modifier.basicMarquee() else Modifier
            )
            val airDate = episode.premiereDate?.let {
                runCatching { it.format(ShortDateFormat) }.getOrNull()
            }
            val runtimeMins = runtimeMinutes(episode)
            val metaLine = listOfNotNull(airDate, runtimeMins?.let { "$it mins" }).joinToString(" • ")
            if (metaLine.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = episode.overview.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
