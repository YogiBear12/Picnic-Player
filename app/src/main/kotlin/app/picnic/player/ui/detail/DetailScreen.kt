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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.playback.LocalThemeMusicPlayer
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.BrowseHero
import app.picnic.player.ui.browse.DetailContentStartInset
import app.picnic.player.ui.browse.DetailMediaRow
import app.picnic.player.ui.browse.ScrollToTopBringIntoView
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.CircularPersonCard
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellHeight
import app.picnic.player.ui.navigation.PersonKey
import app.picnic.player.ui.seerr.SeasonRequestDialog
import app.picnic.player.ui.theme.PicnicColors
import java.util.UUID
import kotlinx.coroutines.delay
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

/** Sections the page scrolls through; saved so back-navigation restores the exact spot. */
private const val SECTION_BUTTONS = 0
private const val SECTION_CAST = 1
private const val SECTION_COLLECTIONS = 2
private const val SECTION_SIMILAR = 3

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
    val leadStreams = state.leadStreams
    val nextUpEpisode = state.nextUpEpisode
    val similarItems = state.similarItems
    val collections = state.collections
    val localTrailers = state.localTrailers
    val trailerYouTubePackage = state.trailerYouTubePackage
    val requestMoreVisible = item.type == BaseItemKind.SERIES &&
        state.requestMoreCanRequest &&
        state.requestMoreSeasons.any { it.selectable }
    val context = LocalContext.current

    val metrics = browseLayoutMetrics(maxWidth, maxHeight)
    // Same vertical geometry as Home (ImmersiveBrowseScaffold): the hero anchors to the
    // bottom of the region above where Home's rows start, so logo/metadata/summary sit in
    // exactly the position they had on the Home screen — no shift on navigation.
    val heroRegionHeight = maxHeight - metrics.rowsRegionHeight

    val listState = rememberLazyListState()
    // Warm the next detail's wash cache on selecting a "More like this" card.
    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val contextMenu = LocalContextMenuHandler.current
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTrailersDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }

    // One requester per action button so the cast row below can hand focus back to the
    // exact button it left from, not the spatially nearest one.
    val buttonFocusRequesters = remember { List(7) { FocusRequester() } }
    var lastFocusedButtonIndex by rememberSaveable { mutableIntStateOf(0) }
    val playFocus = buttonFocusRequesters[0]

    // Row focus state restores by stable ID first: collections/similar load async and the
    // server can reorder them between visits, so back-navigation only falls back to index.
    val people = item.people.orEmpty()
    val castFocus = rememberRowFocusState(people)
    val collectionFocus = rememberRowFocusState(collections)
    val similarFocus = rememberRowFocusState(similarItems)

    // Which section last held focus, survives navigation. On re-entry (back from a pushed
    // detail) we restore the exact card rather than grabbing Play.
    var lastSection by rememberSaveable { mutableIntStateOf(SECTION_BUTTONS) }

    // LazyColumn index of each section (rows are conditional).
    fun sectionLazyIndex(section: Int): Int {
        var index = 0
        if (section == SECTION_CAST) return if (people.isEmpty()) -1 else 1
        index = 1 + (if (people.isEmpty()) 0 else 1)
        if (section == SECTION_COLLECTIONS) return if (collections.isEmpty()) -1 else index
        index += if (collections.isEmpty()) 0 else 1
        if (section == SECTION_SIMILAR) return if (similarItems.isEmpty()) -1 else index
        return 0
    }

    // Restore focus once, but only when the saved section's data has actually arrived —
    // collections/similar load async, and restoring against an empty row silently fell back
    // to Play even though the user left from a card further down.
    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(people, collections, similarItems) {
        if (focusRestored) return@LaunchedEffect
        if (lastSection == SECTION_BUTTONS) {
            focusRestored = true
            playFocus.requestFocus()
            return@LaunchedEffect
        }
        val ready = when (lastSection) {
            SECTION_CAST -> people.isNotEmpty()
            SECTION_COLLECTIONS -> collections.isNotEmpty()
            else -> similarItems.isNotEmpty()
        }
        if (!ready) return@LaunchedEffect // reruns when the row's data lands
        // Resolve the saved card by stable ID first — the row's order can differ this visit.
        when (lastSection) {
            SECTION_COLLECTIONS -> collectionFocus.resolveAgainst(collections) { it.id.toString() }
            SECTION_SIMILAR -> similarFocus.resolveAgainst(similarItems) { it.id.toString() }
            else -> castFocus.resolveAgainst(people)
        }
        focusRestored = true
        // The saved row is far down a freshly composed page: scroll it in first, then poll
        // briefly until the saved card's requester attaches; fall back to Play.
        val lazyIndex = sectionLazyIndex(lastSection)
        if (lazyIndex > 0) {
            runCatching { listState.scrollToItem(lazyIndex) }
            val restored = when (lastSection) {
                SECTION_CAST -> castFocus.restoreFocus()
                SECTION_COLLECTIONS -> collectionFocus.restoreFocus()
                else -> similarFocus.restoreFocus()
            }
            if (restored) {
                return@LaunchedEffect
            }
        }
        lastSection = SECTION_BUTTONS
        runCatching { listState.scrollToItem(0) }
        playFocus.requestFocus()
    }
    // Failsafe: if the saved section's row never arrives this visit (list empty now), the
    // effect above never fires its restore — don't leave the page with nothing focused.
    LaunchedEffect(Unit) {
        delay(2000)
        if (!focusRestored) {
            focusRestored = true
            lastSection = SECTION_BUTTONS
            runCatching { listState.scrollToItem(0) }
            runCatching { playFocus.requestFocus() }
        }
    }

    // Back pops to the previous screen (standard TV back model) — the nav host owns it.
    val isSeries = item.type == BaseItemKind.SERIES
    val playId = if (isSeries && nextUpEpisode != null) nextUpEpisode.id.toString() else item.id.toString()
    val playResumeTicks = if (isSeries && nextUpEpisode != null) {
        nextUpEpisode.userData?.playbackPositionTicks?.takeIf { it > 0 }
    } else {
        item.userData?.playbackPositionTicks?.takeIf { it > 0 }
    }
    val playTitle = if (isSeries && nextUpEpisode != null) {
        val s = nextUpEpisode.parentIndexNumber
        val e = nextUpEpisode.indexNumber
        val base = if (s == 0) "S0 E$e" else "S$s E$e"
        if (playResumeTicks != null) "Resume $base" else "Play $base"
    } else {
        if (playResumeTicks != null) "Resume" else "Play"
    }
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
            state = listState,
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
                // Same fixed-height block as Home, bottom-anchored heroGap above where the
                // rows start, so the logo/details line sit at the identical Y on both screens
                // (no shift navigating Home ↔ Detail). Summary length is type-driven inside
                // BrowseHero, matching Home.
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(heroRegionHeight)) {
                        BrowseHero(
                            item = item,
                            session = session,
                            logoHeight = metrics.logoHeight,
                            continueWatching = false,
                            streamsOverride = leadStreams,
                            seasonCount = if (item.type == BaseItemKind.SERIES) item.childCount else null,
                            onSummaryClick = { showSummaryDialog = true },
                            summaryDown = { buttonFocusRequesters[lastFocusedButtonIndex] },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(
                                    // Rail supplies the left gutter — align with the shell
                                    // panes so Home → Detail keeps one horizontal origin.
                                    start = DetailContentStartInset,
                                    end = metrics.hInset,
                                    bottom = metrics.heroGap
                                )
                                .width(metrics.heroContentWidth)
                        )
                    }

                    val hasVersions = (item.mediaSources?.size ?: 0) > 1
                    // The overflow always shows now — it's the home for the Add-to actions
                    // (favorites, playlist) plus media info / versions / request more.
                    val showMoreButton = true
                    val remoteTrailersCount = item.remoteTrailers?.size ?: 0
                    val localTrailersCount = localTrailers.size
                    val totalTrailersCount = remoteTrailersCount + localTrailersCount
                    var nextButtonIndex = 0
                    val playButtonIndex = nextButtonIndex++
                    val episodesButtonIndex = if (isSeries) nextButtonIndex++ else -1
                    val watchedButtonIndex = nextButtonIndex++
                    val trailersButtonIndex = if (totalTrailersCount > 0) nextButtonIndex++ else -1
                    val moreButtonIndex = if (showMoreButton) nextButtonIndex++ else -1

                    Row(
                        modifier = Modifier
                            .padding(
                                start = DetailContentStartInset,
                                end = metrics.hInset
                            )
                            .height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        fun Modifier.actionButton(index: Int) = this
                            .focusRequester(buttonFocusRequesters[index])
                            .onFocusChanged {
                                if (it.isFocused) {
                                    lastFocusedButtonIndex = index
                                    lastSection = SECTION_BUTTONS
                                }
                            }

                        ExpandableButton(
                            title = playTitle,
                            icon = Icons.Default.PlayArrow,
                            onClick = { onPlay(playId, playResumeTicks, null) },
                            modifier = Modifier.actionButton(playButtonIndex)
                        )

                        if (isSeries) {
                            val episodesAmbUrl = JellyfinImages.ambient(session, item)
                            ExpandableButton(
                                title = "Episodes",
                                icon = Icons.Default.List,
                                onClick = { onEpisodes(item.id.toString(), episodesAmbUrl, nextUpEpisode?.seasonId?.toString(), nextUpEpisode?.id?.toString()) },
                                modifier = Modifier.actionButton(episodesButtonIndex)
                            )
                        }

                        ExpandableButton(
                            title = if (item.userData?.played == true) "Mark Unwatched" else "Mark Watched",
                            icon = Icons.Default.Check,
                            onClick = viewModel::toggleWatched,
                            modifier = Modifier.actionButton(watchedButtonIndex)
                        )

                        if (totalTrailersCount > 0) {
                            ExpandableButton(
                                title = if (totalTrailersCount == 1) "Play trailer" else "Trailers",
                                icon = Icons.Default.Movie,
                                onClick = {
                                    val remoteTrailers = item.remoteTrailers?.toList() ?: emptyList()
                                    if (totalTrailersCount == 1) {
                                        if (localTrailers.isNotEmpty()) {
                                            onPlay(localTrailers.first().id.toString(), null, null)
                                        } else {
                                            context.launchRemoteTrailer(remoteTrailers.first().url ?: "", trailerYouTubePackage)
                                        }
                                    } else {
                                        showTrailersDialog = true
                                    }
                                },
                                modifier = Modifier.actionButton(trailersButtonIndex)
                            )
                        }

                        if (showMoreButton) {
                            ExpandableButton(
                                title = "More",
                                icon = Icons.Default.MoreVert,
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.actionButton(moreButtonIndex)
                            )
                        }
                    }

                    state.requestMoreError?.let { err ->
                        Text(
                            err,
                            color = PicnicColors.Accent,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(
                                start = DetailContentStartInset,
                                end = metrics.hInset,
                                top = 12.dp
                            )
                        )
                    }

                    // Keeps the rows' resting position at page top aligned with where they
                    // pin when focused (buttonsToRowGap below the pin line).
                    Spacer(Modifier.height(buttonsToRowGap))
                }
            }

            if (people.isNotEmpty()) {
                item(key = "cast") {
                    DetailMediaRow(
                        title = "Cast & crew",
                        items = people,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = castFocus.rowModifier()
                    ) { index, person ->
                        CircularPersonCard(
                            imageUrl = JellyfinImages.personPrimary(
                                session,
                                person.id.toString(),
                                person.primaryImageTag
                            ),
                            name = person.name,
                            subtitle = person.role,
                            imageSize = metrics.sy(96f),
                            onClick = {
                                viewModel.resolvePersonKey(person.id, onPersonClick)
                            },
                            modifier = Modifier
                                .focusRequester(castFocus.requesterAt(index))
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        castFocus.onItemFocused(index)
                                        lastSection = SECTION_CAST
                                    }
                                }
                                // Up returns to the button we left from, not the
                                // spatially nearest one (must sit on the card's own
                                // focus node — a group-level `up` is not consulted
                                // for searches leaving a child). First card also
                                // blocks left so focus can't escape the row.
                                .focusProperties {
                                    up = buttonFocusRequesters[lastFocusedButtonIndex]
                                    if (index == 0) left = FocusRequester.Cancel
                                }
                        )
                    }
                }
            }

            if (collections.isNotEmpty()) {
                item(key = "collections") {
                    DetailMediaRow(
                        title = "Included in",
                        items = collections,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = collectionFocus.rowModifier(),
                        key = { _, it -> it.id }
                    ) { index, collection ->
                        DetailRowCard(
                            item = collection,
                            session = session,
                            style = cardStyle,
                            focusRequester = collectionFocus.requesters[index],
                            firstInRow = index == 0,
                            onClick = { onCollection(collection) },
                            onLongClick = { contextMenu.show(collection) },
                            onFocused = {
                                collectionFocus.onItemFocused(index, collection.id.toString())
                                lastSection = SECTION_COLLECTIONS
                            }
                        )
                    }
                }
            }

            if (similarItems.isNotEmpty()) {
                item(key = "similar") {
                    DetailMediaRow(
                        title = "More like this",
                        items = similarItems,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = similarFocus.rowModifier(),
                        key = { _, it -> it.id }
                    ) { index, similarItem ->
                        DetailRowCard(
                            item = similarItem,
                            session = session,
                            style = cardStyle,
                            focusRequester = similarFocus.requesters[index],
                            firstInRow = index == 0,
                            onClick = {
                                val nav = JellyfinImages.navImages(session, similarItem)
                                ambientPrewarmer.warm(nav.ambUrl)
                                onItem(similarItem, nav.bgUrl, nav.ambUrl)
                            },
                            onLongClick = { contextMenu.show(similarItem) },
                            onFocused = {
                                similarFocus.onItemFocused(index, similarItem.id.toString())
                                lastSection = SECTION_SIMILAR
                            }
                        )
                    }
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
            remoteTrailers = item.remoteTrailers?.toList() ?: emptyList(),
            trailerYouTubePackage = trailerYouTubePackage,
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
            onToggleFavorite = { viewModel.toggleFavorite() },
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
private fun DetailRowCard(
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
