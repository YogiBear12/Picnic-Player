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
import androidx.compose.material3.CircularProgressIndicator
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
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.DetailContentStartInset
import app.picnic.player.ui.browse.DetailMediaRow
import app.picnic.player.ui.browse.ScrollToTopBringIntoView
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.ContentCacheWindow
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.RowFocusState
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.rememberRowRevealed
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.MetaLineReserve
import app.picnic.player.ui.grid.gridCellSlot
import app.picnic.player.ui.seerr.SeerrLabeledCard
import app.picnic.player.ui.seerr.seerrLabeledSlotHeight
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import java.time.LocalDate
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

private const val MoviesRowTitle = "Movies in your libraries"
private const val ShowsRowTitle = "Shows in your libraries"
private const val KnownForRowTitle = "Known for"

private val PersonRowBottomPadding = 16.dp

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
    onBack: () -> Unit,
    viewModel: PersonViewModel = hiltViewModel<PersonViewModel, PersonViewModel.Factory>(
        creationCallback = { factory -> factory.create(jellyfinPersonId, tmdbId) }
    )
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    PublishBackdrop(null)

    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = PicnicColors.Accent)
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

    val hasOverview = !state.person?.overview.isNullOrBlank() ||
        !state.seerrPerson?.biography.isNullOrBlank()
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
        PersonSection.Overview -> hasOverview
        PersonSection.Movies -> hasMovies
        PersonSection.Shows -> hasShows
        PersonSection.KnownFor -> hasKnownFor
    }

    fun sectionTarget(section: PersonSection, firstCard: Boolean): FocusRequester = when (section) {
        PersonSection.Overview -> overviewFocusRequester
        PersonSection.Movies -> movieFocus.target(movieItems, firstCard)
        PersonSection.Shows -> showFocus.target(showItems, firstCard)
        PersonSection.KnownFor -> knownForFocus.requester
    }

    suspend fun restoreFirstAvailableSection() {
        val section = when {
            hasOverview -> PersonSection.Overview
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
        hasOverview
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
                when {
                    state.person != null && session != null -> {
                        PersonHero(
                            person = state.person!!,
                            focusRequester = overviewFocusRequester,
                            onFocused = { lastSection = PersonSection.Overview },
                            onSummaryClick = {
                                summaryText = state.person?.overview
                                showSummaryDialog = true
                            },
                            modifier = Modifier.padding(
                                start = DetailContentStartInset,
                                end = metrics.hInset,
                                top = 48.dp,
                                bottom = 24.dp
                            )
                        )
                    }
                    state.seerrPerson != null -> {
                        SeerrPersonHero(
                            person = state.seerrPerson!!,
                            seerrBaseUrl = state.seerrBaseUrl,
                            cacheImages = state.seerrCacheImages,
                            focusRequester = overviewFocusRequester,
                            onFocused = { lastSection = PersonSection.Overview },
                            onSummaryClick = {
                                summaryText = state.seerrPerson?.biography
                                showSummaryDialog = true
                            },
                            modifier = Modifier.padding(
                                start = DetailContentStartInset,
                                end = metrics.hInset,
                                top = 48.dp,
                                bottom = 24.dp
                            )
                        )
                    }
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

private fun seerrCardKey(item: SeerrCatalogItem): String = "${item.mediaType}-${item.tmdbId}"

@Stable
private class SeerrRowFocus(
    val requester: FocusRequester,
    private val indexState: MutableIntState,
    private val keyState: MutableState<String?>
) {
    fun indexIn(items: List<SeerrCatalogItem>): Int = keyState.value
        ?.let { saved -> items.indexOfFirst { seerrCardKey(it) == saved }.takeIf { it >= 0 } }
        ?: indexState.intValue.coerceIn(0, items.lastIndex.coerceAtLeast(0))

    fun onItemFocused(index: Int, item: SeerrCatalogItem) {
        indexState.intValue = index
        keyState.value = seerrCardKey(item)
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
        key = { _, item -> seerrCardKey(item) }
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

@Composable
private fun PersonHero(
    person: BaseItemDto,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onSummaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = LocalImageUrls.current.primary(person)
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(232.dp)
    ) {
        PersonHeroImage(
            imageUrl = imageUrl,
            imageFailed = imageFailed,
            onImageFailed = { imageFailed = true },
            name = person.name
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = person.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            val metaParts = mutableListOf<String>()
            person.premiereDate?.let { birthDate ->
                val dateStr = runCatching { birthDate.format(DATE_FMT) }.getOrNull()
                if (dateStr != null) {
                    val end = person.endDate ?: java.time.LocalDateTime.now()
                    var age = end.year - birthDate.year
                    if (end.monthValue < birthDate.monthValue ||
                        (end.monthValue == birthDate.monthValue && end.dayOfMonth < birthDate.dayOfMonth)
                    ) {
                        age--
                    }
                    metaParts.add("Born $dateStr (age $age)")
                }
            }
            person.productionLocations?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                metaParts.add(it)
            }
            if (metaParts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = metaParts.joinToString("  •  "),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            person.overview?.let { overview ->
                SummarySurface(
                    overview = overview,
                    focusRequester = focusRequester,
                    onFocused = onFocused,
                    onSummaryClick = onSummaryClick
                )
            }
        }
    }
}

@Composable
private fun SeerrPersonHero(
    person: SeerrPersonDetails,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onSummaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = SeerrImages.profile(seerrBaseUrl, person.profilePath, cacheImages, "w500")
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(232.dp)
    ) {
        PersonHeroImage(
            imageUrl = imageUrl,
            imageFailed = imageFailed,
            onImageFailed = { imageFailed = true },
            name = person.name
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = person.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            val metaParts = seerrPersonMeta(person)
            if (metaParts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = metaParts.joinToString("  •  "),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            person.biography?.takeIf { it.isNotBlank() }?.let { overview ->
                SummarySurface(
                    overview = overview,
                    focusRequester = focusRequester,
                    onFocused = onFocused,
                    onSummaryClick = onSummaryClick
                )
            }
        }
    }
}

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
                .fillMaxHeight()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
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
                .fillMaxHeight()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

@Composable
private fun SummarySurface(
    overview: String,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onSummaryClick: () -> Unit
) {
    Surface(
        onClick = onSummaryClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = overview,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = app.picnic.player.ui.browse.BrowseHeroSummaryLineHeight,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun seerrPersonMeta(person: SeerrPersonDetails): List<String> {
    val metaParts = mutableListOf<String>()
    val birthday = person.birthday?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
    }
    if (birthday != null) {
        val deathday = person.deathday?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
        }
        val end = deathday ?: LocalDate.now()
        var age = end.year - birthday.year
        if (end.monthValue < birthday.monthValue ||
            (end.monthValue == birthday.monthValue && end.dayOfMonth < birthday.dayOfMonth)
        ) {
            age--
        }
        val dateStr = birthday.format(DATE_FMT)
        metaParts.add(
            if (deathday != null) {
                "Born $dateStr (died ${deathday.format(DATE_FMT)}, age $age)"
            } else {
                "Born $dateStr (age $age)"
            }
        )
    }
    person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { metaParts.add(it) }
    return metaParts
}
