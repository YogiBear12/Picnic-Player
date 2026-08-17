@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package app.picnic.player.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.playback.LocalThemeMusicPlayer
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import app.picnic.player.ui.browse.CardTimeLeftBadge
import app.picnic.player.ui.browse.CardWatchedBadge
import app.picnic.player.ui.browse.HeroInfoLine
import app.picnic.player.ui.browse.HeroSpecs
import app.picnic.player.ui.browse.ShortDateFormat
import app.picnic.player.ui.browse.minutesLeft
import app.picnic.player.ui.browse.runtimeMinutes
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import java.util.UUID
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun SeriesEpisodesScreen(
    seriesId: String,
    ambUrl: String?,
    onPlay: (String, Long?) -> Unit,
    onBack: () -> Unit,
    initialSeasonId: String? = null,
    initialFocusEpisodeId: String? = null,
    onGoToSeries: ((String) -> Unit)? = null,
    viewModel: SeriesEpisodesViewModel = hiltViewModel()
) {
    LaunchedEffect(seriesId, viewModel.session) {
        if (viewModel.session != null) viewModel.loadSeasons(seriesId)
    }

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
    var selectedSeasonId by rememberSaveable { mutableStateOf(initialSeasonId) }
    var pendingFocusEpisodeId by rememberSaveable { mutableStateOf(initialFocusEpisodeId) }

    LaunchedEffect(viewModel.seasons) {
        if (selectedSeasonId == null && viewModel.seasons.isNotEmpty()) {
            selectedSeasonId = viewModel.seasons.first().id.toString()
        }
    }

    LaunchedEffect(selectedSeasonId, viewModel.session) {
        val sid = selectedSeasonId ?: return@LaunchedEffect
        viewModel.loadEpisodes(seriesId, sid)
    }

    val selectedSeasonIndex = viewModel.seasons.indexOfFirst { it.id.toString() == selectedSeasonId }.coerceAtLeast(0)
    val selectedSeason = viewModel.seasons.getOrNull(selectedSeasonIndex)

    val seasonRail = rememberSeasonRail(
        seasons = viewModel.seasons,
        selectedIndex = selectedSeasonIndex,
        onVisibleIndices = viewModel::prefetchSeasonCounts
    )
    val selectedSeasonFr = seasonRail.requesterFor(selectedSeasonIndex)

    val episodeFocus = rememberEpisodeFocus()
    val episodes = viewModel.episodes.collectAsLazyPagingItems()

    var contextMenuEpisode by remember { mutableStateOf<BaseItemDto?>(null) }
    var contextMenuSeason by remember { mutableStateOf<BaseItemDto?>(null) }
    var lastScrolledSeasonId by rememberSaveable { mutableStateOf<String?>(null) }

    val initialLoadComplete = rememberInitialLoadComplete(
        seasons = viewModel.seasons,
        seasonsError = viewModel.seasonsError,
        selectedSeasonId = selectedSeasonId,
        episodes = episodes
    )

    var initialFocusRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(initialLoadComplete.value, selectedSeasonId, episodes.itemCount) }
            .collect { (complete, _, count) ->
                if (!complete || count == 0) return@collect
                val returningIndex = pendingFocusEpisodeId
                    ?.let { id -> episodes.itemSnapshotList.indexOfFirst { it?.id?.toString() == id } }
                    ?.takeIf { it >= 0 }
                if (returningIndex != null) {
                    lastScrolledSeasonId = selectedSeasonId
                    episodeFocus.restoreTo(returningIndex)
                    pendingFocusEpisodeId = null
                    initialFocusRequested = true
                    return@collect
                }
                if (selectedSeasonId != lastScrolledSeasonId) {
                    val target = if (initialFocusRequested) {
                        0
                    } else {
                        episodes.itemSnapshotList.indexOfFirst { ep ->
                            ep != null && ((ep.userData?.playbackPositionTicks ?: 0L) > 0L || ep.userData?.played != true)
                        }.coerceAtLeast(0)
                    }
                    episodeFocus.scrollTo(target)
                    lastScrolledSeasonId = selectedSeasonId
                }

                if (!initialFocusRequested) {
                    episodeFocus.targetRequester.requestFocusWhenAttached(maxFrames = 20)
                    initialFocusRequested = true
                }
            }
    }

    BackHandler(enabled = episodeFocus.hasFocus) {
        if (selectedSeasonFr != null && viewModel.seasons.isNotEmpty()) {
            selectedSeasonFr.requestFocus()
        } else {
            onBack()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().graphicsLayer { alpha = if (initialLoadComplete.value) 1f else 0f }) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(start = 60.dp, end = 16.dp)
            ) {
                Spacer(Modifier.height(76.dp))
                val seriesItem = viewModel.seriesItem
                if (seriesItem != null) SeriesHeader(seriesItem, session)

                SeasonList(
                    seasons = viewModel.seasons,
                    seasonsError = viewModel.seasonsError,
                    selectedSeasonId = selectedSeasonId,
                    rail = seasonRail,
                    rightTarget = { episodeFocus.targetRequester },
                    onRightPressed = { episodeFocus.rightConsumed(episodes.itemCount) },
                    onSelect = { id ->
                        selectedSeasonId = id
                    },
                    onRetry = { viewModel.loadSeasons(seriesId) },
                    onLongPress = { contextMenuSeason = it }
                )
            }

            Box(Modifier.fillMaxSize()) {
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
                    state = episodeFocus.listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged { episodeFocus.hasFocus = it.hasFocus },
                    contentPadding = PaddingValues(start = 44.dp, end = 40.dp, top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = episodes.itemCount,
                        contentType = episodes.itemContentType { "episode" }
                    ) { index ->
                        val episode = episodes[index]
                        if (episode != null) {
                            EpisodeItem(
                                episode = episode,
                                session = session,
                                leftFocus = selectedSeasonFr,
                                enterFr = episodeFocus.requesterFor(index),
                                onFocused = { episodeFocus.onEpisodeFocused(index) },
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

        if (!initialLoadComplete.value) {
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
                onGoToSeries = onGoToSeries?.let { go ->
                    { targetSeriesId ->
                        pendingFocusEpisodeId = ep.id.toString()
                        go(targetSeriesId)
                    }
                }
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
private fun rememberInitialLoadComplete(
    seasons: List<BaseItemDto>,
    seasonsError: Throwable?,
    selectedSeasonId: String?,
    episodes: LazyPagingItems<BaseItemDto>
): State<Boolean> {
    val complete = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(seasons, selectedSeasonId) {
        snapshotFlow { episodes.itemCount }.collect { count ->
            val selectedSeasonCountReady = seasons.find { it.id.toString() == selectedSeasonId }?.childCount != null
            if (seasons.isNotEmpty() && count > 0 && selectedSeasonCountReady) {
                complete.value = true
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(5000)
        complete.value = true
    }
    LaunchedEffect(Unit) {
        snapshotFlow { episodes.loadState.refresh }.collect {
            if (it is LoadState.Error) complete.value = true
        }
    }
    LaunchedEffect(seasonsError) {
        if (seasonsError != null) complete.value = true
    }
    return complete
}

@Composable
private fun SeriesHeader(seriesItem: BaseItemDto, session: UserSession) {
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PicnicColors.ArtworkPlaceholder)
                )
                if (imageUrl != null) {
                    ArtworkImage(
                        url = imageUrl,
                        contentDescription = null,
                        crossfade = true,
                        holdLastImage = true,
                        label = "episode='${episode.name}'",
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
