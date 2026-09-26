@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package app.picnic.player.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.SeerrPersonDetails
import app.picnic.player.data.seerr.catalogKey
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.BrowseHeroSummaryLineHeight
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.DetailContentStartInset
import app.picnic.player.ui.browse.DetailMediaRow
import app.picnic.player.ui.browse.RowTitleBottomGap
import app.picnic.player.ui.browse.ScrollToTopBringIntoView
import app.picnic.player.ui.browse.SkeletonTitleBarCards
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.ContentCacheWindow
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.RowFocusState
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.common.SkeletonCardRow
import app.picnic.player.ui.common.SkeletonTextBar
import app.picnic.player.ui.common.SkeletonTextHeight
import app.picnic.player.ui.common.SkeletonTextLine
import app.picnic.player.ui.common.SkeletonTitleHeight
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.rememberRowRevealed
import app.picnic.player.ui.common.rememberSkeletonPulse
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.skeletonFill
import app.picnic.player.ui.common.skeletonPulse
import app.picnic.player.ui.grid.GridCardSkeleton
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.MetaLineReserve
import app.picnic.player.ui.grid.gridCellSlot
import app.picnic.player.ui.seerr.SeerrLabeledCard
import app.picnic.player.ui.seerr.seerrLabeledSlotHeight
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

private enum class PersonSection {
    Overview,
    Movies,
    Shows,
    KnownFor
}

private const val MoviesRowTitle = "Movies"
private const val ShowsRowTitle = "Shows"
private const val KnownForRowTitle = "Known for"

private val PersonRowBottomPadding = 16.dp
private val PersonHeroImageHeight = 232.dp
private const val PersonHeroImageAspect = 2f / 3f
private val PersonHeroImageCorner = 12.dp
private val PersonHeroImageGap = 16.dp
private val PersonHeroTextInset = 16.dp
private val PersonNameMetaGap = 8.dp
private val PersonHeroSummaryGap = 16.dp
private val PersonSummaryTextPadding = 16.dp
private val PersonHeroTopPadding = 48.dp
private val PersonHeroBottomPadding = 24.dp
private const val PersonNameBarWidth = 0.5f
private val PersonNameBarHeight = 24.dp
private const val PersonMetaBarWidth = 0.35f
private val PersonSummaryBarWidths = listOf(1f, 0.95f, 0.7f)

private const val ROW_HERO = "hero"
private const val ROW_MOVIES = "movies"
private const val ROW_SHOWS = "shows"
private const val ROW_KNOWN_FOR = "known-for"

@Composable
fun PersonScreen(
    jellyfinPersonId: String?,
    tmdbId: Int?,
    onItem: (item: BaseItemDto, bgUrl: String?, ambUrl: String?) -> Unit,
    onSeerrItem: (SeerrCatalogItem, String?, String?) -> Unit,
    onFilmography: (tmdbId: Int, name: String, knownForDepartment: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: PersonViewModel = hiltViewModel<PersonViewModel, PersonViewModel.Factory>(
        creationCallback = { factory -> factory.create(jellyfinPersonId, tmdbId) }
    )
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    PublishBackdrop(null)

    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) {
        val metrics = browseLayoutMetrics(maxWidth, maxHeight)
        Column(Modifier.fillMaxSize()) {
            PersonHeroSkeleton(Modifier.personHeroPadding(metrics))
            PersonPosterRowSkeleton(metrics)
        }
        return@BoxWithConstraints
    }

    if (state.error != null && state.person == null && state.seerrPerson == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(state.error.orEmpty(), color = PicnicColors.OnDark)
        }
        return@BoxWithConstraints
    }

    val session = state.session
    val metrics = browseLayoutMetrics(maxWidth, maxHeight)
    val listState = rememberLazyListState(cacheWindow = ContentCacheWindow)
    var showSummaryDialog by remember { mutableStateOf(false) }
    var summaryText by remember { mutableStateOf<String?>(null) }

    val overviewFocusRequester = remember { FocusRequester() }
    val movieFocus = rememberPersonRowFocus()
    val showFocus = rememberPersonRowFocus()
    val knownForFocus = rememberSeerrRowFocus()

    var lastSection by rememberSaveable { mutableStateOf(PersonSection.Overview) }
    var focusRestored by remember { mutableStateOf(false) }
    val builtRows = remember { mutableSetOf<String>() }

    val jellyfinPerson = state.person
    val seerrPerson = state.seerrPerson
    val heroOverview = if (jellyfinPerson != null && session != null) {
        jellyfinPerson.overview
    } else {
        seerrPerson?.biography
    }
    val hasOverview = !heroOverview.isNullOrBlank()
    val filmographyTmdbId = state.resolvedTmdbId.takeIf { state.seerrLinked }
    val hasOverviewSection = hasOverview || filmographyTmdbId != null
    val filmographyButtonFr = remember { FocusRequester() }
    val libraryFromJellyfin = session != null &&
        (state.libraryMovies.isNotEmpty() || state.libraryShows.isNotEmpty())
    val movieItems = if (libraryFromJellyfin) state.libraryMovies else emptyList()
    val showItems = if (libraryFromJellyfin) state.libraryShows else emptyList()
    val (movieCredits, showCredits) = remember(state.libraryCredits, libraryFromJellyfin) {
        if (libraryFromJellyfin) {
            emptyList<SeerrCatalogItem>() to emptyList()
        } else {
            state.libraryCredits.partition { it.mediaType == SeerrMediaType.MOVIE }
        }
    }
    val hasMovies = movieItems.isNotEmpty() || movieCredits.isNotEmpty()
    val hasShows = showItems.isNotEmpty() || showCredits.isNotEmpty()
    val hasKnownFor = state.knownFor.isNotEmpty()

    fun sectionLazyIndex(section: PersonSection): Int {
        val keys = buildList {
            add(ROW_HERO)
            if (hasMovies) add(ROW_MOVIES)
            if (hasShows) add(ROW_SHOWS)
            if (hasKnownFor) add(ROW_KNOWN_FOR)
        }
        val key = when (section) {
            PersonSection.Overview -> ROW_HERO
            PersonSection.Movies -> ROW_MOVIES
            PersonSection.Shows -> ROW_SHOWS
            PersonSection.KnownFor -> ROW_KNOWN_FOR
        }
        return keys.indexOf(key)
    }

    fun sectionReady(section: PersonSection): Boolean = when (section) {
        PersonSection.Overview -> hasOverviewSection
        PersonSection.Movies -> hasMovies
        PersonSection.Shows -> hasShows
        PersonSection.KnownFor -> hasKnownFor
    }

    fun sectionTarget(section: PersonSection, firstCard: Boolean): FocusRequester = when (section) {
        PersonSection.Overview -> if (filmographyTmdbId != null) filmographyButtonFr else overviewFocusRequester
        PersonSection.Movies -> movieFocus.target(movieItems, firstCard)
        PersonSection.Shows -> showFocus.target(showItems, firstCard)
        PersonSection.KnownFor -> knownForFocus.requester
    }

    suspend fun restoreFirstAvailableSection() {
        val section = when {
            hasOverviewSection -> PersonSection.Overview
            hasMovies -> PersonSection.Movies
            hasShows -> PersonSection.Shows
            hasKnownFor -> PersonSection.KnownFor
            else -> return
        }
        lastSection = section
        sectionTarget(section, firstCard = true).requestFocusWhenAttached()
    }

    LaunchedEffect(
        state.libraryMovies,
        state.libraryShows,
        state.libraryCredits,
        state.knownFor,
        hasOverviewSection
    ) {
        if (focusRestored) return@LaunchedEffect
        if (!sectionReady(lastSection)) {
            focusRestored = true
            restoreFirstAvailableSection()
            return@LaunchedEffect
        }
        when (lastSection) {
            PersonSection.Overview -> Unit
            PersonSection.Movies -> movieFocus.resolveAgainst(movieItems, movieCredits)
            PersonSection.Shows -> showFocus.resolveAgainst(showItems, showCredits)
            PersonSection.KnownFor -> knownForFocus.resolveAgainst(state.knownFor)
        }
        focusRestored = true
        val lazyIndex = sectionLazyIndex(lastSection)
        if (lazyIndex >= 0) {
            if (lazyIndex > 0) {
                runCatching { listState.scrollToItem(lazyIndex) }
            }
            val target = sectionTarget(lastSection, firstCard = false)
            if (target.requestFocusWhenAttached(maxFrames = 30)) {
                return@LaunchedEffect
            }
        }
        restoreFirstAvailableSection()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        if (!focusRestored) {
            focusRestored = true
            restoreFirstAvailableSection()
        }
    }

    val horizontalRowSpec = LocalBringIntoViewSpec.current
    val pinSpec = with(androidx.compose.ui.platform.LocalDensity.current) {
        remember { ScrollToTopBringIntoView((304.dp - MetaLineReserve).toPx()) }
    }
    val posterStyle = posterCardStyle(sy = metrics.sy)
    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val images = LocalImageUrls.current

    fun openSeerrItem(item: SeerrCatalogItem) {
        val nav = SeerrImages.navImages(state.seerrBaseUrl, item, state.seerrCacheImages)
        ambientPrewarmer.warm(nav.ambUrl)
        onSeerrItem(item, nav.bgUrl, nav.ambUrl)
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides pinSpec) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.bottomInset)
        ) {
            item(key = ROW_HERO) {
                val hero = when {
                    jellyfinPerson != null && session != null -> PersonHeroContent(
                        imageUrl = images.primary(jellyfinPerson),
                        name = jellyfinPerson.name,
                        meta = jellyfinPersonMeta(jellyfinPerson),
                        overview = jellyfinPerson.overview
                    )
                    seerrPerson != null -> PersonHeroContent(
                        imageUrl = SeerrImages.profile(
                            state.seerrBaseUrl,
                            seerrPerson.profilePath,
                            state.seerrCacheImages,
                            "w500"
                        ),
                        name = seerrPerson.name,
                        meta = seerrPersonMeta(seerrPerson),
                        overview = seerrPerson.biography
                    )
                    else -> null
                }
                if (hero != null) {
                    PersonHero(
                        content = hero,
                        focusRequester = overviewFocusRequester,
                        filmographyFocus = filmographyButtonFr,
                        onFocused = { lastSection = PersonSection.Overview },
                        onSummaryClick = {
                            summaryText = hero.overview
                            showSummaryDialog = true
                        },
                        onFilmography = filmographyTmdbId?.let { id ->
                            { onFilmography(id, hero.name.orEmpty(), state.knownForDepartment) }
                        },
                        modifier = Modifier.personHeroPadding(metrics)
                    )
                }
            }

            if (hasMovies) {
                item(key = ROW_MOVIES) {
                    PersonLibraryRow(
                        title = MoviesRowTitle,
                        rowKey = ROW_MOVIES,
                        items = movieItems,
                        credits = movieCredits,
                        lines = state.libraryLines,
                        focus = movieFocus,
                        listState = listState,
                        builtRows = builtRows,
                        seerrBaseUrl = state.seerrBaseUrl,
                        cacheImages = state.seerrCacheImages,
                        cardStyle = posterStyle,
                        horizontalRowSpec = horizontalRowSpec,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        onItemClick = { item ->
                            val nav = images.navImages(item)
                            onItem(item, nav.bgUrl, nav.ambUrl)
                        },
                        onSeerrClick = ::openSeerrItem,
                        onFocused = { lastSection = PersonSection.Movies }
                    )
                }
            }

            if (hasShows) {
                item(key = ROW_SHOWS) {
                    PersonLibraryRow(
                        title = ShowsRowTitle,
                        rowKey = ROW_SHOWS,
                        items = showItems,
                        credits = showCredits,
                        lines = state.libraryLines,
                        focus = showFocus,
                        listState = listState,
                        builtRows = builtRows,
                        seerrBaseUrl = state.seerrBaseUrl,
                        cacheImages = state.seerrCacheImages,
                        cardStyle = posterStyle,
                        horizontalRowSpec = horizontalRowSpec,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        onItemClick = { item ->
                            val nav = images.navImages(item)
                            onItem(item, nav.bgUrl, nav.ambUrl)
                        },
                        onSeerrClick = ::openSeerrItem,
                        onFocused = { lastSection = PersonSection.Shows }
                    )
                }
            }

            if (hasKnownFor) {
                item(key = ROW_KNOWN_FOR) {
                    val revealed by rememberRowRevealed(listState, builtRows, ROW_KNOWN_FOR)
                    PersonSeerrMediaRow(
                        title = KnownForRowTitle,
                        items = state.knownFor,
                        seerrBaseUrl = state.seerrBaseUrl,
                        cacheImages = state.seerrCacheImages,
                        cardStyle = posterStyle,
                        focus = knownForFocus,
                        revealed = revealed,
                        onIndexChange = { lastSection = PersonSection.KnownFor },
                        onItemClick = ::openSeerrItem,
                        horizontalRowSpec = horizontalRowSpec,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        personCreditMetaline = true
                    )
                }
            }
        }
    }

    if (showSummaryDialog) {
        summaryText?.takeIf { it.isNotBlank() }?.let { overview ->
            ScrollableTextDialog(
                text = overview,
                onDismiss = { showSummaryDialog = false },
                maxTextHeight = 420.dp
            )
        }
    }
}

@Stable
private class SeerrRowFocus(
    val requester: FocusRequester,
    private val indexState: MutableIntState,
    private val keyState: MutableState<String?>
) {
    fun indexIn(items: List<SeerrCatalogItem>): Int = keyState.value
        ?.let { saved -> items.indexOfFirst { it.catalogKey == saved }.takeIf { it >= 0 } }
        ?: indexState.intValue.coerceIn(0, items.lastIndex.coerceAtLeast(0))

    fun onItemFocused(index: Int, item: SeerrCatalogItem) {
        indexState.intValue = index
        keyState.value = item.catalogKey
    }

    fun resolveAgainst(items: List<SeerrCatalogItem>) {
        indexState.intValue = indexIn(items)
    }
}

@Composable
private fun rememberSeerrRowFocus(): SeerrRowFocus {
    val requester = remember { FocusRequester() }
    val index = rememberSaveable { mutableIntStateOf(0) }
    val key = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { SeerrRowFocus(requester, index, key) }
}

@Stable
private class PersonRowFocus(val jellyfin: RowFocusState, val seerr: SeerrRowFocus) {
    fun target(items: List<BaseItemDto>, firstCard: Boolean): FocusRequester = if (items.isEmpty()) {
        seerr.requester
    } else {
        jellyfin.requesterAt(if (firstCard) 0 else jellyfin.focusedIndex)
    }

    fun resolveAgainst(items: List<BaseItemDto>, credits: List<SeerrCatalogItem>) {
        if (items.isEmpty()) seerr.resolveAgainst(credits) else jellyfin.resolveAgainst(items)
    }
}

@Composable
private fun rememberPersonRowFocus(): PersonRowFocus {
    val jellyfin = rememberRowFocusState()
    val seerr = rememberSeerrRowFocus()
    return remember { PersonRowFocus(jellyfin, seerr) }
}

@Composable
private fun PersonLibraryRow(
    title: String,
    rowKey: String,
    items: List<BaseItemDto>,
    credits: List<SeerrCatalogItem>,
    lines: Map<UUID, PersonCreditLines>,
    focus: PersonRowFocus,
    listState: LazyListState,
    builtRows: MutableSet<String>,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    cardStyle: BrowseCardStyle,
    horizontalRowSpec: BringIntoViewSpec,
    endInset: Dp,
    cardSpacing: Dp,
    onItemClick: (BaseItemDto) -> Unit,
    onSeerrClick: (SeerrCatalogItem) -> Unit,
    onFocused: () -> Unit
) {
    val revealed by rememberRowRevealed(listState, builtRows, rowKey)
    if (items.isEmpty()) {
        PersonSeerrMediaRow(
            title = title,
            items = credits,
            seerrBaseUrl = seerrBaseUrl,
            cacheImages = cacheImages,
            cardStyle = cardStyle,
            focus = focus.seerr,
            revealed = revealed,
            onIndexChange = onFocused,
            onItemClick = onSeerrClick,
            horizontalRowSpec = horizontalRowSpec,
            endInset = endInset,
            cardSpacing = cardSpacing
        )
        return
    }
    val rowFocus = focus.jellyfin
    val contextMenu = LocalContextMenuHandler.current
    DetailMediaRow(
        title = title,
        items = items,
        endInset = endInset,
        cardSpacing = cardSpacing,
        horizontalRowSpec = horizontalRowSpec,
        rowFocus = rowFocus.rowModifier(),
        modifier = Modifier.padding(bottom = PersonRowBottomPadding),
        titleFontWeight = FontWeight.Bold,
        key = { _, item -> item.id }
    ) { index, item ->
        if (!revealed && index != rowFocus.focusedIndex) {
            Spacer(Modifier.gridCellSlot(cardStyle, metaLine = true))
            return@DetailMediaRow
        }
        val itemLines = lines[item.id]
        Box(
            Modifier.gridCellSlot(cardStyle, metaLine = true),
            contentAlignment = Alignment.TopCenter
        ) {
            MediaGridCard(
                item = item,
                style = cardStyle,
                focusRequester = rowFocus.requesterAt(index),
                upFocus = null,
                subtitleOverride = itemLines?.role.orEmpty(),
                metaLine = itemLines?.detail.orEmpty(),
                onClick = { onItemClick(item) },
                onLongClick = { contextMenu.show(item) },
                onFocused = {
                    rowFocus.onItemFocused(index)
                    onFocused()
                },
                modifier = Modifier
                    .padding(top = cardStyle.topInset)
                    .focusProperties {
                        if (index == 0) left = FocusRequester.Cancel
                    }
            )
        }
    }
}

@Composable
private fun PersonSeerrMediaRow(
    title: String,
    items: List<SeerrCatalogItem>,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    cardStyle: BrowseCardStyle,
    focus: SeerrRowFocus,
    revealed: Boolean,
    onIndexChange: () -> Unit,
    onItemClick: (SeerrCatalogItem) -> Unit,
    horizontalRowSpec: BringIntoViewSpec,
    endInset: Dp,
    cardSpacing: Dp,
    personCreditMetaline: Boolean = false
) {
    val focusIndex = focus.indexIn(items)
    DetailMediaRow(
        title = title,
        items = items,
        endInset = endInset,
        cardSpacing = cardSpacing,
        horizontalRowSpec = horizontalRowSpec,
        rowFocus = Modifier.focusRestorer(focus.requester).focusGroup(),
        modifier = Modifier.padding(bottom = PersonRowBottomPadding),
        titleFontWeight = FontWeight.Bold,
        key = { _, item -> item.catalogKey }
    ) { index, item ->
        if (!revealed && index != focusIndex) {
            Spacer(
                Modifier
                    .width(cardStyle.width)
                    .height(seerrLabeledSlotHeight(cardStyle, personCreditMetaline))
            )
            return@DetailMediaRow
        }
        Box(
            Modifier
                .width(cardStyle.width)
                .height(seerrLabeledSlotHeight(cardStyle, personCreditMetaline)),
            contentAlignment = Alignment.TopCenter
        ) {
            SeerrLabeledCard(
                item = item,
                seerrBaseUrl = seerrBaseUrl,
                cacheImages = cacheImages,
                style = cardStyle,
                focusRequester = if (index == focusIndex) focus.requester else null,
                leftFocus = if (index == 0) FocusRequester.Cancel else null,
                onFocused = {
                    focus.onItemFocused(index, item)
                    onIndexChange()
                },
                onClick = { onItemClick(item) },
                modifier = Modifier.padding(top = cardStyle.topInset)
            )
        }
    }
}

private val DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

@Stable
private class PersonHeroContent(
    val imageUrl: String?,
    val name: String?,
    val meta: List<String>,
    val overview: String?
)

@Composable
private fun PersonHero(
    content: PersonHeroContent,
    focusRequester: FocusRequester,
    filmographyFocus: FocusRequester,
    onFocused: () -> Unit,
    onSummaryClick: () -> Unit,
    onFilmography: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(content.imageUrl) { mutableStateOf(false) }
    val summary = content.overview?.takeIf { it.isNotBlank() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PersonHeroImageHeight),
        verticalAlignment = Alignment.Top
    ) {
        PersonHeroImage(
            imageUrl = content.imageUrl,
            imageFailed = imageFailed,
            onImageFailed = { imageFailed = true },
            name = content.name
        )

        Spacer(modifier = Modifier.width(PersonHeroImageGap))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = content.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(horizontal = PersonHeroTextInset)
            )

            if (content.meta.isNotEmpty()) {
                Spacer(modifier = Modifier.height(PersonNameMetaGap))
                Text(
                    text = content.meta.joinToString("  •  "),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = PersonHeroTextInset)
                )
            }

            Spacer(modifier = Modifier.height(PersonHeroSummaryGap))

            if (summary != null) {
                SummarySurface(
                    overview = summary,
                    focusRequester = focusRequester,
                    downFocus = filmographyFocus.takeIf { onFilmography != null },
                    onFocused = onFocused,
                    onSummaryClick = onSummaryClick,
                    modifier = Modifier.weight(1f)
                )
            }

            if (onFilmography != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton(
                    label = "Filmography",
                    onActivate = onFilmography,
                    focusRequester = filmographyFocus,
                    modifier = Modifier
                        .padding(horizontal = PersonHeroTextInset)
                        .onFocusChanged { if (it.isFocused) onFocused() }
                        .focusProperties { if (summary != null) up = focusRequester }
                )
            }
        }
    }
}

@Composable
private fun PersonHeroSkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    val summaryLine = with(LocalDensity.current) { BrowseHeroSummaryLineHeight.toDp() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PersonHeroImageHeight)
            .skeletonPulse(pulse),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .height(PersonHeroImageHeight)
                .aspectRatio(PersonHeroImageAspect)
                .skeletonFill(PersonHeroImageCorner)
        )
        Spacer(modifier = Modifier.width(PersonHeroImageGap))
        BoxWithConstraints(Modifier.weight(1f).padding(horizontal = PersonHeroTextInset)) {
            val width = maxWidth
            Column {
                SkeletonTextLine(MaterialTheme.typography.displayMedium, width * PersonNameBarWidth, PersonNameBarHeight)
                Spacer(modifier = Modifier.height(PersonNameMetaGap))
                SkeletonTextLine(MaterialTheme.typography.titleSmall, width * PersonMetaBarWidth, SkeletonTextHeight)
                Spacer(modifier = Modifier.height(PersonHeroSummaryGap + PersonSummaryTextPadding))
                for (fraction in PersonSummaryBarWidths) {
                    Box(Modifier.height(summaryLine), contentAlignment = Alignment.CenterStart) {
                        SkeletonTextBar(width = width * fraction)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonPosterRowSkeleton(metrics: BrowseLayoutMetrics) {
    val style = posterCardStyle(sy = metrics.sy)
    Column {
        SkeletonTextLine(
            style = MaterialTheme.typography.titleMedium,
            width = style.width * SkeletonTitleBarCards,
            barHeight = SkeletonTitleHeight,
            modifier = Modifier
                .padding(start = DetailContentStartInset, bottom = RowTitleBottomGap)
                .skeletonPulse(rememberSkeletonPulse())
        )
        SkeletonCardRow(cardWidth = style.width, spacing = metrics.cardSpacing, startInset = DetailContentStartInset) {
            GridCardSkeleton(style, metaLine = true)
        }
    }
}

private fun Modifier.personHeroPadding(metrics: BrowseLayoutMetrics): Modifier = padding(start = DetailContentStartInset, end = metrics.hInset, top = PersonHeroTopPadding, bottom = PersonHeroBottomPadding)

@Composable
private fun PersonHeroImage(
    imageUrl: String?,
    imageFailed: Boolean,
    onImageFailed: () -> Unit,
    name: String?
) {
    if (imageUrl == null || imageFailed) {
        Box(
            Modifier
                .height(PersonHeroImageHeight)
                .aspectRatio(PersonHeroImageAspect)
                .clip(RoundedCornerShape(PersonHeroImageCorner))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = name,
                tint = Color.LightGray,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            onState = { if (it is AsyncImagePainter.State.Error) onImageFailed() },
            modifier = Modifier
                .height(PersonHeroImageHeight)
                .aspectRatio(PersonHeroImageAspect)
                .clip(RoundedCornerShape(PersonHeroImageCorner))
        )
    }
}

@Composable
private fun SummarySurface(
    overview: String,
    focusRequester: FocusRequester,
    downFocus: FocusRequester?,
    onFocused: () -> Unit,
    onSummaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSummaryClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties { if (downFocus != null) down = downFocus }
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(PersonSummaryTextPadding)) {
            val lineHeightPx = with(LocalDensity.current) { BrowseHeroSummaryLineHeight.toPx() }
            val maxLines = (constraints.maxHeight / lineHeightPx).toInt().coerceAtLeast(1)
            Text(
                text = overview,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = BrowseHeroSummaryLineHeight,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun jellyfinPersonMeta(person: BaseItemDto): List<String> = buildList {
    val birthDate = person.premiereDate
    val dateStr = birthDate?.let { runCatching { it.format(DATE_FMT) }.getOrNull() }
    if (birthDate != null && dateStr != null) {
        val end = (person.endDate ?: LocalDateTime.now()).toLocalDate()
        add("Born $dateStr (age ${Period.between(birthDate.toLocalDate(), end).years})")
    }
    person.productionLocations?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { add(it) }
}

private fun seerrPersonMeta(person: SeerrPersonDetails): List<String> = buildList {
    val birthday = person.birthday?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
    }
    if (birthday != null) {
        val deathday = person.deathday?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
        }
        val age = Period.between(birthday, deathday ?: LocalDate.now()).years
        val dateStr = birthday.format(DATE_FMT)
        add(
            if (deathday != null) {
                "Born $dateStr (died ${deathday.format(DATE_FMT)}, age $age)"
            } else {
                "Born $dateStr (age $age)"
            }
        )
    }
    person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { add(it) }
}
