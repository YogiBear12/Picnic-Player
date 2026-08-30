@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package app.picnic.player.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellSlot
import app.picnic.player.ui.seerr.SeerrLabeledCard
import app.picnic.player.ui.seerr.seerrLabeledSlotHeight
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jellyfin.sdk.model.api.BaseItemDto

private enum class PersonSection {
    Overview,
    Library,
    KnownFor
}

private const val LibraryRowTitle = "Movies and Shows in your libraries"
private const val KnownForRowTitle = "Known for"

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
    val libraryFocus = rememberRowFocusState()
    val librarySeerrRowFocus = remember { FocusRequester() }
    val knownForRowFocus = remember { FocusRequester() }

    var focusedLibrarySeerrIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedLibrarySeerrKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedKnownForIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedKnownForKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSection by rememberSaveable { mutableStateOf(PersonSection.Overview) }
    var focusRestored by remember { mutableStateOf(false) }

    val hasOverview = !state.person?.overview.isNullOrBlank() ||
        !state.seerrPerson?.biography.isNullOrBlank()
    val hasLibraryJf = session != null && state.libraryItems.isNotEmpty()
    val hasLibrarySeerr = state.libraryCredits.isNotEmpty()
    val hasLibrary = hasLibraryJf || hasLibrarySeerr
    val hasKnownFor = state.knownFor.isNotEmpty()

    fun sectionLazyIndex(section: PersonSection): Int {
        val keys = buildList {
            add("hero")
            if (hasLibrary) add("library")
            if (hasKnownFor) add("known-for")
        }
        val key = when (section) {
            PersonSection.Overview -> "hero"
            PersonSection.Library -> "library"
            PersonSection.KnownFor -> "known-for"
        }
        return keys.indexOf(key)
    }

    fun sectionReady(section: PersonSection): Boolean = when (section) {
        PersonSection.Overview -> hasOverview
        PersonSection.Library -> hasLibrary
        PersonSection.KnownFor -> hasKnownFor
    }

    suspend fun restoreFirstAvailableSection() {
        when {
            hasOverview -> {
                lastSection = PersonSection.Overview
                overviewFocusRequester.requestFocusWhenAttached()
            }
            hasLibraryJf -> {
                lastSection = PersonSection.Library
                libraryFocus.requesterAt(0).requestFocusWhenAttached()
            }
            hasLibrarySeerr -> {
                lastSection = PersonSection.Library
                librarySeerrRowFocus.requestFocusWhenAttached()
            }
            hasKnownFor -> {
                lastSection = PersonSection.KnownFor
                knownForRowFocus.requestFocusWhenAttached()
            }
        }
    }

    LaunchedEffect(
        state.libraryItems,
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
            PersonSection.Library -> {
                if (hasLibraryJf) {
                    libraryFocus.resolveAgainst(state.libraryItems)
                } else {
                    focusedLibrarySeerrIndex = focusedLibrarySeerrKey
                        ?.let { key ->
                            state.libraryCredits.indexOfFirst {
                                "${it.mediaType}-${it.tmdbId}" == key
                            }.takeIf { it >= 0 }
                        }
                        ?: focusedLibrarySeerrIndex.coerceIn(0, state.libraryCredits.lastIndex)
                }
            }
            PersonSection.KnownFor ->
                focusedKnownForIndex = focusedKnownForKey
                    ?.let { key ->
                        state.knownFor.indexOfFirst {
                            "${it.mediaType}-${it.tmdbId}" == key
                        }.takeIf { it >= 0 }
                    }
                    ?: focusedKnownForIndex.coerceIn(0, state.knownFor.lastIndex)
        }
        focusRestored = true
        val lazyIndex = sectionLazyIndex(lastSection)
        if (lazyIndex >= 0) {
            if (lazyIndex > 0) {
                runCatching { listState.scrollToItem(lazyIndex) }
            }
            val target = when (lastSection) {
                PersonSection.Overview -> overviewFocusRequester
                PersonSection.Library ->
                    if (hasLibraryJf) {
                        libraryFocus.requesterAt(libraryFocus.focusedIndex)
                    } else {
                        librarySeerrRowFocus
                    }
                PersonSection.KnownFor -> knownForRowFocus
            }
            if (target?.requestFocusWhenAttached(maxFrames = 30) == true) {
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
        remember { ScrollToTopBringIntoView(304.dp.toPx()) }
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
            item(key = "hero") {
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

            if (hasLibraryJf && session != null) {
                item(key = "library") {
                    DetailMediaRow(
                        title = LibraryRowTitle,
                        items = state.libraryItems,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = libraryFocus.rowModifier(),
                        modifier = Modifier.padding(bottom = 16.dp),
                        titleFontWeight = FontWeight.Bold
                    ) { index, item ->
                        val lines = state.libraryLines[item.id]
                        Box(
                            Modifier.gridCellSlot(posterStyle, metaLine = true),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            MediaGridCard(
                                item = item,
                                style = posterStyle,
                                focusRequester = libraryFocus.requesterAt(index),
                                upFocus = null,
                                subtitleOverride = lines?.role.orEmpty(),
                                metaLine = lines?.detail.orEmpty(),
                                onClick = {
                                    val nav = images.navImages(item)
                                    onItem(item, nav.bgUrl, nav.ambUrl)
                                },
                                onFocused = {
                                    libraryFocus.onItemFocused(index)
                                    lastSection = PersonSection.Library
                                },
                                modifier = Modifier
                                    .padding(top = posterStyle.topInset)
                                    .focusProperties {
                                        if (index == 0) left = FocusRequester.Cancel
                                    }
                            )
                        }
                    }
                }
            } else if (hasLibrarySeerr) {
                item(key = "library") {
                    val focusIndex = focusedLibrarySeerrKey
                        ?.let { key ->
                            state.libraryCredits.indexOfFirst {
                                "${it.mediaType}-${it.tmdbId}" == key
                            }.takeIf { it >= 0 }
                        }
                        ?: focusedLibrarySeerrIndex.coerceIn(0, state.libraryCredits.lastIndex)
                    PersonSeerrMediaRow(
                        title = LibraryRowTitle,
                        items = state.libraryCredits,
                        seerrBaseUrl = state.seerrBaseUrl,
                        cacheImages = state.seerrCacheImages,
                        cardStyle = posterStyle,
                        rowFocus = librarySeerrRowFocus,
                        focusIndex = focusIndex,
                        onIndexChange = { index, item ->
                            focusedLibrarySeerrIndex = index
                            focusedLibrarySeerrKey = "${item.mediaType}-${item.tmdbId}"
                            lastSection = PersonSection.Library
                        },
                        onItemClick = ::openSeerrItem,
                        horizontalRowSpec = horizontalRowSpec,
                        endInset = metrics.hInset,
                        cardSpacing = metrics.cardSpacing
                    )
                }
            }

            if (hasKnownFor) {
                item(key = "known-for") {
                    val focusIndex = focusedKnownForKey
                        ?.let { key ->
                            state.knownFor.indexOfFirst {
                                "${it.mediaType}-${it.tmdbId}" == key
                            }.takeIf { it >= 0 }
                        }
                        ?: focusedKnownForIndex.coerceIn(0, state.knownFor.lastIndex)
                    PersonSeerrMediaRow(
                        title = KnownForRowTitle,
                        items = state.knownFor,
                        seerrBaseUrl = state.seerrBaseUrl,
                        cacheImages = state.seerrCacheImages,
                        cardStyle = posterStyle,
                        rowFocus = knownForRowFocus,
                        focusIndex = focusIndex,
                        onIndexChange = { index, item ->
                            focusedKnownForIndex = index
                            focusedKnownForKey = "${item.mediaType}-${item.tmdbId}"
                            lastSection = PersonSection.KnownFor
                        },
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

@Composable
private fun PersonSeerrMediaRow(
    title: String,
    items: List<SeerrCatalogItem>,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    cardStyle: BrowseCardStyle,
    rowFocus: FocusRequester,
    focusIndex: Int,
    onIndexChange: (Int, SeerrCatalogItem) -> Unit,
    onItemClick: (SeerrCatalogItem) -> Unit,
    horizontalRowSpec: androidx.compose.foundation.gestures.BringIntoViewSpec,
    endInset: androidx.compose.ui.unit.Dp,
    cardSpacing: androidx.compose.ui.unit.Dp,
    personCreditMetaline: Boolean = false
) {
    DetailMediaRow(
        title = title,
        items = items,
        endInset = endInset,
        cardSpacing = cardSpacing,
        horizontalRowSpec = horizontalRowSpec,
        rowFocus = Modifier.focusRestorer(rowFocus).focusGroup(),
        modifier = Modifier.padding(bottom = 16.dp),
        titleFontWeight = FontWeight.Bold,
        key = { _, item -> "${item.mediaType}-${item.tmdbId}" }
    ) { index, item ->
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
                focusRequester = if (index == focusIndex) rowFocus else null,
                leftFocus = if (index == 0) FocusRequester.Cancel else null,
                onFocused = { onIndexChange(index, item) },
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
