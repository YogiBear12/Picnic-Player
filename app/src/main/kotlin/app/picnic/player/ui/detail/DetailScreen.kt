@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.playback.LocalThemeMusicPlayer
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.ScrollToTopBringIntoView
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellHeight
import app.picnic.player.ui.navigation.PersonKey
import app.picnic.player.ui.seerr.SeasonRequestDialog
import app.picnic.player.ui.theme.PicnicColors
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun DetailScreen(
    itemId: String,
    bgUrl: String?,
    ambUrl: String?,
    onPlay: (itemId: String, startTicks: Long?, sourceId: String?) -> Unit,
    onItem: (item: BaseItemDto, bgUrl: String?, ambUrl: String?) -> Unit,
    onBack: () -> Unit,
    onEpisodes: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onCollection: (BaseItemDto) -> Unit = {},
    onPersonClick: (PersonKey) -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel<DetailViewModel, DetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(itemId) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item
    val session = state.session

    // Theme music for this item while any screen about it is up. The player refcounts by
    // owner id, so pushing the episodes screen for the same series keeps the track going.
    val themeMusic = LocalThemeMusicPlayer.current
    DisposableEffect(itemId) {
        val ownerId = runCatching { UUID.fromString(itemId) }.getOrNull()
        ownerId?.let(themeMusic::acquire)
        onDispose { ownerId?.let(themeMusic::release) }
    }

    // The app-level backdrop host renders this; arriving with the same URLs the previous
    // screen published (Home → Detail) means nothing is redrawn. Nav args win so the
    // backdrop is correct before the item loads; item-derived URLs cover deep links.
    val derivedNav = item?.let { i -> session?.let { s -> JellyfinImages.navImages(s, i) } }
    PublishBackdrop(
        BackdropSpec(
            backdropUrl = bgUrl ?: derivedNav?.bgUrl,
            ambientUrl = ambUrl ?: derivedNav?.ambUrl
        )
    )

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
            item == null || session == null -> {
                BackHandler { onBack() }
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error ?: "Not found", color = PicnicColors.OnDark)
                }
            }
            else -> DetailContent(
                item = item,
                session = session,
                state = state,
                onPlay = onPlay,
                onItem = onItem,
                onEpisodes = onEpisodes,
                onCollection = onCollection,
                onPersonClick = onPersonClick,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun DetailContent(
    item: BaseItemDto,
    session: UserSession,
    state: DetailViewModel.UiState,
    onPlay: (String, Long?, String?) -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onEpisodes: (String, String?, String?, String?) -> Unit,
    onCollection: (BaseItemDto) -> Unit,
    onPersonClick: (PersonKey) -> Unit,
    viewModel: DetailViewModel
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    // Back pops to the previous screen (standard TV back model) — the nav host owns it.
    val people = item.people.orEmpty()
    val collections = state.collections
    val similarItems = state.similarItems
    val localTrailers = state.localTrailers
    val remoteTrailers = item.remoteTrailers?.toList() ?: emptyList()
    val trailerCount = remoteTrailers.size + localTrailers.size
    // Shown for any series Seerr knows about; the picker's per-season badges say what is
    // already requested or available, and submitting stays gated on what is selectable.
    val requestMoreVisible = item.type == BaseItemKind.SERIES && state.requestMoreTmdbId != null
    val context = LocalContext.current
    // Warm the next detail's wash cache on selecting a "More like this" card.
    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val contextMenu = LocalContextMenuHandler.current

    val metrics = browseLayoutMetrics(maxWidth, maxHeight)
    // Same vertical geometry as Home (ImmersiveBrowseScaffold): the hero anchors to the
    // bottom of the region above where Home's rows start, so logo/metadata/summary sit in
    // exactly the position they had on the Home screen — no shift on navigation.
    val heroRegionHeight = maxHeight - metrics.rowsRegionHeight

    val focus = rememberDetailPageFocus(people, collections, similarItems)

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTrailersDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }

    val playTarget = detailPlayTarget(item, state.nextUpEpisode)
    val cardStyle = posterCardStyle(sy = metrics.sy)

    // Where a focused row card pins vertically: the exact y where Home's focused row sits.
    // The button row rests *above* this line, so focusing a button asks for a negative
    // scroll that clamps at 0 — the page never nudges — and focus moving back up from the
    // rows scrolls the page fully back to the top through the same clamp.
    val rowsGap = metrics.rowsViewportOffset + metrics.rowTitleHeight
    // Gap between the action buttons and the first row (Cast & crew) — a third of rowsGap so
    // the people row sits higher while keeping a clear break under the buttons. The focus-pin
    // line moves up by the same amount so focusing a row doesn't nudge the page.
    val buttonsToRowGap = rowsGap / 3
    val pinSpec = with(LocalDensity.current) {
        remember(heroRegionHeight, buttonsToRowGap) {
            ScrollToTopBringIntoView((heroRegionHeight + buttonsToRowGap).toPx())
        }
    }
    // The vertical pivot above must not leak into the rows' own horizontal scrolling —
    // capture the platform default (the TV pivot that eases cards through a fixed focus
    // point, same as Home's rows) and restore it inside each LazyRow.
    val horizontalRowSpec = LocalBringIntoViewSpec.current

    CompositionLocalProvider(LocalBringIntoViewSpec provides pinSpec) {
        LazyColumn(
            state = focus.listState,
            // Full-bleed to the screen edges: each block carries its own resting inset
            // (hero/buttons/text via a modifier, rows via LazyRow contentPadding), so a row's
            // cards scroll under that inset and off the true left edge instead of being clipped
            // by a column-level start padding. The backdrop is the app-level full-bleed layer.
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.bottomInset),
            // A bit roomier than Home's rowSpacing: Home rows carry extra slack inside their
            // card slots, detail rows end flush at their label text — this evens the felt gap.
            verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing + metrics.sy(12f))
        ) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth()) {
                    DetailHero(
                        item = item,
                        session = session,
                        metrics = metrics,
                        heroRegionHeight = heroRegionHeight,
                        leadStreams = state.leadStreams,
                        focus = focus,
                        onSummaryClick = { showSummaryDialog = true }
                    )
                    DetailActionButtons(
                        item = item,
                        playTitle = playTarget.title,
                        trailerCount = trailerCount,
                        requestMoreError = state.requestMoreError,
                        metrics = metrics,
                        focus = focus,
                        buttonsToRowGap = buttonsToRowGap,
                        actions = DetailButtonActions(
                            onPlay = { onPlay(playTarget.itemId, playTarget.resumeTicks, null) },
                            onEpisodes = {
                                onEpisodes(
                                    item.id.toString(),
                                    JellyfinImages.ambient(session, item),
                                    state.nextUpEpisode?.seasonId?.toString(),
                                    state.nextUpEpisode?.id?.toString()
                                )
                            },
                            onToggleWatched = viewModel::toggleWatched,
                            onTrailers = {
                                if (trailerCount == 1) {
                                    if (localTrailers.isNotEmpty()) {
                                        onPlay(localTrailers.first().id.toString(), null, null)
                                    } else {
                                        context.launchRemoteTrailer(remoteTrailers.first().url ?: "", state.trailerYouTubePackage)
                                    }
                                } else {
                                    showTrailersDialog = true
                                }
                            },
                            onMore = { showOverflowMenu = true }
                        )
                    )
                }
            }

            if (people.isNotEmpty()) {
                item(key = "cast") {
                    DetailCastRow(
                        people = people,
                        session = session,
                        metrics = metrics,
                        horizontalRowSpec = horizontalRowSpec,
                        focus = focus,
                        onPersonClick = { person -> viewModel.resolvePersonKey(person.id, onPersonClick) }
                    )
                }
            }

            if (collections.isNotEmpty()) {
                item(key = "collections") {
                    DetailPosterRow(
                        title = "Included in",
                        items = collections,
                        session = session,
                        metrics = metrics,
                        cardStyle = cardStyle,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = focus.collections,
                        onClick = { onCollection(it) },
                        onLongClick = { contextMenu.show(it) },
                        onFocused = { index, collection -> focus.onCollectionFocused(index, collection.id.toString()) }
                    )
                }
            }

            if (similarItems.isNotEmpty()) {
                item(key = "similar") {
                    DetailPosterRow(
                        title = "More like this",
                        items = similarItems,
                        session = session,
                        metrics = metrics,
                        cardStyle = cardStyle,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = focus.similar,
                        onClick = { similarItem ->
                            val nav = JellyfinImages.navImages(session, similarItem)
                            ambientPrewarmer.warm(nav.ambUrl)
                            onItem(similarItem, nav.bgUrl, nav.ambUrl)
                        },
                        onLongClick = { contextMenu.show(it) },
                        onFocused = { index, similarItem -> focus.onSimilarFocused(index, similarItem.id.toString()) }
                    )
                }
            }
        }
    }

    if (showSummaryDialog) {
        item.overview?.let { overview ->
            ScrollableTextDialog(
                text = overview,
                onDismiss = { showSummaryDialog = false }
            )
        }
    }

    if (showTrailersDialog) {
        TrailersDialog(
            localTrailers = localTrailers,
            remoteTrailers = remoteTrailers,
            trailerYouTubePackage = state.trailerYouTubePackage,
            onPlayLocal = { onPlay(it, null, null) },
            onDismiss = { showTrailersDialog = false }
        )
    }

    if (showOverflowMenu) {
        OverflowMenuDialog(
            item = item,
            onPlayVersion = { sourceId ->
                showOverflowMenu = false
                onPlay(item.id.toString(), null, sourceId)
            },
            onDismiss = { showOverflowMenu = false },
            onToggleFavorite = viewModel::setFavorite,
            showRequestMore = requestMoreVisible,
            requestMoreBusy = state.requestMoreBusy,
            onRequestMore = { viewModel.showSeasonPicker() }
        )
    }

    if (state.showSeasonPicker) {
        SeasonRequestDialog(
            seasons = state.requestMoreSeasons,
            onConfirm = viewModel::requestMoreSeasons,
            onDismiss = viewModel::dismissSeasonPicker
        )
    }
}

/**
 * Detail-row poster card: the grid card (poster + title + year, library-grid styling)
 * inside a slot box that reserves headroom for the focus scale/glow, like the home rows'
 * [app.picnic.player.ui.browse.BrowseMediaCard]. The first card blocks D-pad left so
 * focus can't escape the row.
 */
@Composable
internal fun DetailRowCard(
    item: BaseItemDto,
    session: UserSession,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    firstInRow: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit
) {
    Box(
        Modifier.width(style.width).height(style.topInset + gridCellHeight(style)),
        contentAlignment = Alignment.TopCenter
    ) {
        MediaGridCard(
            item = item,
            session = session,
            style = style,
            focusRequester = focusRequester,
            upFocus = null,
            leftFocus = if (firstInRow) FocusRequester.Cancel else null,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            modifier = Modifier.padding(top = style.topInset)
        )
    }
}
