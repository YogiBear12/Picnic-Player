@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.seerr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.jellyfinDetailIdOrNull
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.DetailContentStartInset
import app.picnic.player.ui.browse.DetailMediaRow
import app.picnic.player.ui.browse.ScrollToTopBringIntoView
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.CircularPersonCard
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors

@Composable
fun SeerrDetailScreen(
    tmdbId: Int,
    mediaType: SeerrMediaType,
    bgUrl: String?,
    ambUrl: String?,
    onPlayLibraryItem: (jellyfinMediaId: String) -> Unit,
    onRecommendedItem: (SeerrCatalogItem, String?, String?) -> Unit,
    onPersonClick: (tmdbPersonId: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: SeerrDetailViewModel = hiltViewModel<SeerrDetailViewModel, SeerrDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(tmdbId, mediaType) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog = state.catalog

    // Hybrid Detail redirect (#46): once details load, any library id (HD, else
    // 4K) leaves Seerr Detail for Jellyfin Detail via the shared gate.
    val redirectId = jellyfinDetailIdOrNull(catalog?.jellyfinMediaId, catalog?.jellyfinMediaId4k)
    LaunchedEffect(redirectId) {
        if (redirectId != null) onPlayLibraryItem(redirectId)
    }

    val resolvedBg = bgUrl
        ?: SeerrImages.backdrop(state.serverUrl, catalog?.backdropPath, state.cacheImages)
    val resolvedAmb = ambUrl
        ?: SeerrImages.ambient(
            state.serverUrl,
            catalog?.backdropPath ?: catalog?.posterPath,
            state.cacheImages
        )

    PublishBackdrop(BackdropSpec(backdropUrl = resolvedBg, ambientUrl = resolvedAmb))
    BackHandler { onBack() }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
            catalog == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(state.error ?: "Not found", color = PicnicColors.OnDark)
            }
            redirectId != null -> {
                // Brief hold while replaceTop runs — avoid flashing Request UI.
            }
            else -> SeerrDetailContent(
                catalog = catalog,
                state = state,
                actionRow = viewModel.actionRow(),
                onPlayLibraryItem = onPlayLibraryItem,
                onRecommendedItem = onRecommendedItem,
                onPersonClick = onPersonClick,
                onRequest = viewModel::onRequestClicked,
                onCancel = viewModel::cancelRequest
            )
        }
    }

    if (state.showSeasonPicker) {
        SeasonRequestDialog(
            seasons = viewModel.seasonPickItems(),
            onConfirm = viewModel::requestSeasons,
            onDismiss = viewModel::dismissSeasonPicker
        )
    }
}

@Composable
private fun SeerrDetailContent(
    catalog: SeerrCatalogItem,
    state: SeerrDetailViewModel.UiState,
    actionRow: SeerrActionRow,
    onPlayLibraryItem: (String) -> Unit,
    onRecommendedItem: (SeerrCatalogItem, String?, String?) -> Unit,
    onPersonClick: (tmdbPersonId: Int) -> Unit,
    onRequest: () -> Unit,
    onCancel: () -> Unit
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val metrics = browseLayoutMetrics(maxWidth, maxHeight)
    val heroRegionHeight = maxHeight - metrics.rowsRegionHeight
    val listState = rememberLazyListState()
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val primaryFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val summaryFocus = remember { FocusRequester() }
    val hasSummary = !catalog.overview.isNullOrBlank()
    val cast = state.cast
    val recommended = state.recommended

    val castFocus = rememberRowFocusState(cast)
    val recommendedFocus = rememberRowFocusState(recommended)

    var lastActionFocus by rememberSaveable { mutableStateOf(SeerrActionFocusTarget.Primary) }
    var lastSection by rememberSaveable { mutableStateOf(SeerrDetailSection.Actions) }

    fun actionFocusRequester(): FocusRequester = when (lastActionFocus) {
        SeerrActionFocusTarget.Cancel -> if (actionRow.showCancel) cancelFocus else primaryFocus
        SeerrActionFocusTarget.Summary -> if (hasSummary) summaryFocus else primaryFocus
        SeerrActionFocusTarget.Primary -> primaryFocus
    }

    fun sectionLazyIndex(section: SeerrDetailSection): Int = when (section) {
        SeerrDetailSection.Actions -> 0
        SeerrDetailSection.Cast -> if (cast.isEmpty()) -1 else 1
        SeerrDetailSection.Recommended -> {
            if (recommended.isEmpty()) {
                -1
            } else {
                1 + if (cast.isEmpty()) 0 else 1
            }
        }
    }

    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(cast, recommended, actionRow.initialFocus, actionRow.primary, actionRow.showCancel) {
        if (focusRestored) return@LaunchedEffect
        if (lastSection == SeerrDetailSection.Actions) {
            focusRestored = true
            val target = when (actionRow.initialFocus) {
                SeerrActionFocusTarget.Primary -> primaryFocus
                SeerrActionFocusTarget.Cancel ->
                    if (actionRow.showCancel) cancelFocus else primaryFocus
                SeerrActionFocusTarget.Summary ->
                    if (hasSummary) summaryFocus else primaryFocus
            }
            target.requestFocusWhenAttached()
            return@LaunchedEffect
        }
        val ready = when (lastSection) {
            SeerrDetailSection.Cast -> cast.isNotEmpty()
            SeerrDetailSection.Recommended -> recommended.isNotEmpty()
            SeerrDetailSection.Actions -> true
        }
        if (!ready) return@LaunchedEffect
        when (lastSection) {
            SeerrDetailSection.Cast -> castFocus.resolveAgainst(cast)
            SeerrDetailSection.Recommended ->
                recommendedFocus.resolveAgainst(recommended) { "${it.mediaType}-${it.tmdbId}" }
            SeerrDetailSection.Actions -> Unit
        }
        focusRestored = true
        val lazyIndex = sectionLazyIndex(lastSection)
        if (lazyIndex > 0) {
            runCatching { listState.scrollToItem(lazyIndex) }
            val restored = when (lastSection) {
                SeerrDetailSection.Cast -> castFocus.restoreFocus()
                SeerrDetailSection.Recommended -> recommendedFocus.restoreFocus()
                SeerrDetailSection.Actions -> false
            }
            if (restored) {
                return@LaunchedEffect
            }
        }
        lastSection = SeerrDetailSection.Actions
        runCatching { listState.scrollToItem(0) }
        primaryFocus.requestFocusWhenAttached()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        if (!focusRestored) {
            focusRestored = true
            lastSection = SeerrDetailSection.Actions
            runCatching { listState.scrollToItem(0) }
            primaryFocus.requestFocusWhenAttached()
        }
    }

    val cardStyle = posterCardStyle(metrics.sy)
    val rowsGap = metrics.rowsViewportOffset + metrics.rowTitleHeight
    val buttonsToRowGap = rowsGap / 3
    val pinSpec = with(LocalDensity.current) {
        remember(heroRegionHeight, buttonsToRowGap) {
            ScrollToTopBringIntoView((heroRegionHeight + buttonsToRowGap).toPx())
        }
    }
    val horizontalRowSpec = LocalBringIntoViewSpec.current
    val ambientPrewarmer = LocalAmbientPrewarmer.current

    CompositionLocalProvider(LocalBringIntoViewSpec provides pinSpec) {
        LazyColumn(
            state = listState,
            // Full-bleed to the screen edges: hero/buttons/text carry their resting inset via a
            // modifier, rows via LazyRow contentPadding, so row cards scroll off the true left
            // edge instead of being clipped by a column-level start padding.
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.bottomInset),
            verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing + metrics.sy(12f))
        ) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(heroRegionHeight)) {
                        SeerrHero(
                            item = catalog,
                            logoHeight = metrics.logoHeight,
                            onSummaryClick = { showSummaryDialog = true },
                            summaryDown = {
                                if (actionRow.showCancel) cancelFocus else primaryFocus
                            },
                            summaryFocusRequester = summaryFocus,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(
                                    start = DetailContentStartInset,
                                    end = metrics.hInset,
                                    bottom = metrics.heroGap
                                )
                                .width(metrics.heroContentWidth)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .padding(start = DetailContentStartInset, end = metrics.hInset)
                            .height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SeerrActionButton(
                            title = actionRow.primaryLabel,
                            icon = primaryIcon(actionRow.primary),
                            onClick = {
                                if (state.busy) return@SeerrActionButton
                                when (actionRow.primary) {
                                    SeerrPrimaryAction.Play -> {
                                        val id = catalog.jellyfinMediaId
                                        if (!id.isNullOrBlank()) onPlayLibraryItem(id)
                                    }
                                    SeerrPrimaryAction.Request,
                                    SeerrPrimaryAction.RequestMore
                                    -> onRequest()
                                    SeerrPrimaryAction.Pending -> Unit
                                    SeerrPrimaryAction.Unavailable -> showInfoDialog = true
                                }
                            },
                            modifier = Modifier
                                .focusRequester(primaryFocus)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        lastActionFocus = SeerrActionFocusTarget.Primary
                                        lastSection = SeerrDetailSection.Actions
                                    }
                                }
                        )
                        if (actionRow.showCancel) {
                            SeerrActionButton(
                                title = actionRow.cancelLabel,
                                icon = Icons.Default.Close,
                                onClick = { if (!state.busy) onCancel() },
                                modifier = Modifier
                                    .focusRequester(cancelFocus)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            lastActionFocus = SeerrActionFocusTarget.Cancel
                                            lastSection = SeerrDetailSection.Actions
                                        }
                                    }
                            )
                        }
                    }

                    state.actionError?.let { err ->
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
                    if (cast.isNotEmpty() || recommended.isNotEmpty()) {
                        Spacer(Modifier.height(buttonsToRowGap))
                    }
                }
            }

            if (cast.isNotEmpty()) {
                item(key = "cast") {
                    DetailMediaRow(
                        title = "Cast",
                        items = cast,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = castFocus.rowModifier(),
                        key = { index, member -> "${member.id ?: member.name}-$index" }
                    ) { index, member ->
                        CircularPersonCard(
                            imageUrl = SeerrImages.profile(
                                state.serverUrl,
                                member.profilePath,
                                state.cacheImages
                            ),
                            name = member.name,
                            subtitle = member.character?.takeIf { it.isNotBlank() },
                            imageSize = metrics.sy(96f),
                            onClick = {
                                member.id?.let(onPersonClick)
                            },
                            modifier = Modifier
                                .focusRequester(castFocus.requesterAt(index))
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        castFocus.onItemFocused(index)
                                        lastSection = SeerrDetailSection.Cast
                                    }
                                }
                                // Up returns to the action button we left from (must sit on the
                                // card's own focus node). First card blocks left so focus can't
                                // escape the row.
                                .focusProperties {
                                    up = actionFocusRequester()
                                    if (index == 0) left = FocusRequester.Cancel
                                }
                        )
                    }
                }
            }

            if (recommended.isNotEmpty()) {
                item(key = "recommended") {
                    DetailMediaRow(
                        title = "Recommended",
                        items = recommended,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = recommendedFocus.rowModifier(),
                        key = { _, item -> "${item.mediaType}-${item.tmdbId}" }
                    ) { index, item ->
                        Box(
                            Modifier.width(cardStyle.width).height(seerrLabeledSlotHeight(cardStyle)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            SeerrLabeledCard(
                                item = item,
                                seerrBaseUrl = state.serverUrl,
                                cacheImages = state.cacheImages,
                                style = cardStyle,
                                focusRequester = recommendedFocus.requesters[index],
                                upFocus = if (cast.isEmpty()) actionFocusRequester() else null,
                                leftFocus = if (index == 0) FocusRequester.Cancel else null,
                                onFocused = {
                                    recommendedFocus.onItemFocused(index, "${item.mediaType}-${item.tmdbId}")
                                    lastSection = SeerrDetailSection.Recommended
                                },
                                onClick = {
                                    val nav = SeerrImages.navImages(
                                        state.serverUrl,
                                        item,
                                        state.cacheImages
                                    )
                                    ambientPrewarmer.warm(nav.ambUrl)
                                    onRecommendedItem(item, nav.bgUrl, nav.ambUrl)
                                },
                                modifier = Modifier.padding(top = cardStyle.topInset)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        ScrollableTextDialog(
            text = actionRow.infoMessage.orEmpty(),
            onDismiss = { showInfoDialog = false },
            width = 420.dp,
            textAlign = TextAlign.Center
        )
    }

    if (showSummaryDialog) {
        catalog.overview?.let { overview ->
            ScrollableTextDialog(
                text = overview,
                onDismiss = { showSummaryDialog = false }
            )
        }
    }
}

private enum class SeerrDetailSection {
    Actions,
    Cast,
    Recommended
}

private fun primaryIcon(action: SeerrPrimaryAction): ImageVector = when (action) {
    SeerrPrimaryAction.Play -> Icons.Default.PlayArrow
    SeerrPrimaryAction.Request,
    SeerrPrimaryAction.RequestMore
    -> Icons.Default.Add
    SeerrPrimaryAction.Pending -> Icons.Default.Check
    SeerrPrimaryAction.Unavailable -> Icons.Default.Close
}
