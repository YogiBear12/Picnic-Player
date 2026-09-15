@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.media.GridContentType
import app.picnic.player.data.media.GridFilterFacets
import app.picnic.player.data.media.GridSortField
import app.picnic.player.data.media.GridSortSpec
import app.picnic.player.data.media.MediaGridFilter
import app.picnic.player.data.media.ResolutionFilter
import app.picnic.player.data.media.WatchedFilter
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.CenteredMessage
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelEdgeInset
import app.picnic.player.ui.common.PanelFadeLength
import app.picnic.player.ui.common.PanelFloatingHeight
import app.picnic.player.ui.common.PanelHeader
import app.picnic.player.ui.common.PanelRowKeys
import app.picnic.player.ui.common.PanelRowMetrics
import app.picnic.player.ui.common.PanelRowSpacing
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.panelGlass
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.rowPrimaryColor
import app.picnic.player.ui.common.rowTrailingColor
import app.picnic.player.ui.common.verticalFadingEdges
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PanelDimScrim = Color(0x66000000)
private val GridRowMetrics = PanelRowMetrics(innerPadding = 12.dp, verticalPadding = 7.dp)
private const val PanelAnimMs = 200

internal enum class GridFilterSection(val label: String, val icon: ImageVector) {
    CONTENT_TYPE("Content type", Icons.Filled.Movie),
    SORT_ORDER("Sort order", Icons.Filled.SwapVert),
    SORT_BY("Sort by", Icons.AutoMirrored.Filled.Sort),
    WATCHED("Watched status", Icons.Filled.CheckCircle),
    FAVORITES("Favorites", Icons.Filled.Star),
    GENRES("Genres", Icons.Filled.Category),
    STUDIOS("Studios", Icons.Filled.Business),
    COMMUNITY_RATING("Community rating", Icons.Filled.StarHalf),
    PARENTAL("Parental rating", Icons.Filled.Shield),
    RESOLUTION("Resolution", Icons.Filled.Hd),
    DECADE("Decade", Icons.Filled.DateRange)
}

internal fun availableFilterSections(
    facets: GridFilterFacets,
    offered: Set<GridFilterSection>
): List<GridFilterSection> = GridFilterSection.entries.filter { section ->
    when (section) {
        GridFilterSection.CONTENT_TYPE -> GridFilterSection.CONTENT_TYPE in offered
        GridFilterSection.GENRES -> GridFilterSection.GENRES in offered && facets.genres.isNotEmpty()
        GridFilterSection.STUDIOS -> facets.studios.isNotEmpty()
        GridFilterSection.PARENTAL -> facets.parentalRatings.isNotEmpty()
        GridFilterSection.DECADE -> facets.decades.isNotEmpty()
        else -> true
    }
}

internal fun activeFilterSections(
    filter: MediaGridFilter,
    sort: GridSortSpec
): Set<GridFilterSection> = buildSet {
    if (filter.contentType != GridContentType.ALL) add(GridFilterSection.CONTENT_TYPE)
    if (!sort.ascending) add(GridFilterSection.SORT_ORDER)
    if (sort.field != GridSortField.NAME) add(GridFilterSection.SORT_BY)
    if (filter.watched != WatchedFilter.ALL) add(GridFilterSection.WATCHED)
    if (filter.favoritesOnly) add(GridFilterSection.FAVORITES)
    if (filter.genreIds.isNotEmpty()) add(GridFilterSection.GENRES)
    if (filter.studioIds.isNotEmpty()) add(GridFilterSection.STUDIOS)
    if (filter.minCommunityRating != null) add(GridFilterSection.COMMUNITY_RATING)
    if (filter.parentalRatings.isNotEmpty()) add(GridFilterSection.PARENTAL)
    if (filter.resolution != ResolutionFilter.ANY) add(GridFilterSection.RESOLUTION)
    if (filter.decades.isNotEmpty()) add(GridFilterSection.DECADE)
}

private data class PanelOption(
    val label: String,
    val selected: Boolean,
    val trailingIcon: ImageVector? = null,
    val leadingIcon: ImageVector? = null,
    val leadingTint: Color? = null,
    val onClick: () -> Unit
)

@Composable
internal fun GridFilterPanel(
    filter: MediaGridFilter,
    sort: GridSortSpec,
    facets: GridFilterFacets,
    offered: Set<GridFilterSection>,
    onFilterChange: (MediaGridFilter) -> Unit,
    onSortChange: (GridSortSpec) -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val dismiss: () -> Unit = {
        if (shown) {
            shown = false
            scope.launch {
                delay(PanelAnimMs.toLong())
                onClose()
            }
        }
    }

    val sections = availableFilterSections(facets, offered)
    var openSection by remember { mutableStateOf<GridFilterSection?>(null) }
    var lastOpenedSection by remember { mutableStateOf<GridFilterSection?>(null) }

    Popup(
        onDismissRequest = {
            if (openSection != null) openSection = null else dismiss()
        },
        properties = PopupProperties(focusable = true)
    ) {
        val sectionRowFocus = remember(sections) { sections.associateWith { FocusRequester() } }
        val clearRowFocus = remember { FocusRequester() }
        val firstValueFocus = remember { FocusRequester() }
        val topListState = rememberLazyListState()
        val optionListState = key(openSection) { rememberLazyListState() }

        LaunchedEffect(openSection, shown, facets) {
            if (!shown) return@LaunchedEffect
            val returnTo = lastOpenedSection
            val target = when (val section = openSection) {
                null -> sectionRowFocus[returnTo ?: sections.firstOrNull()] ?: return@LaunchedEffect
                else -> {
                    lastOpenedSection = section
                    firstValueFocus
                }
            }
            if (target.requestFocusWhenAttached(maxFrames = 20)) return@LaunchedEffect
            if (openSection == null && returnTo != null) {
                runCatching { topListState.scrollToItem(sections.indexOf(returnTo).coerceAtLeast(0)) }
                target.requestFocusWhenAttached(maxFrames = 20)
            }
        }

        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize().background(PanelDimScrim))
            }
            AnimatedVisibility(
                visible = shown,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    Modifier
                        .padding(end = PanelEdgeInset)
                        .width(PanelWidth.Floating)
                        .height(PanelFloatingHeight)
                        .panelGlass()
                        .padding(vertical = 16.dp)
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusGroup()
                ) {
                    val section = openSection
                    PanelHeader(section?.label ?: "Sort & filter", icon = section?.icon)
                    val options = section?.let {
                        buildSectionOptions(
                            it,
                            filter,
                            sort,
                            facets,
                            onFilterChange = onFilterChange,
                            onSortChange = onSortChange
                        )
                    }
                    val listState = if (section == null) topListState else optionListState
                    val active = activeFilterSections(filter, sort)
                    val rows = options?.mapIndexed { index, option ->
                        FilterPanelRowModel(
                            label = option.label,
                            leadingIcon = option.leadingIcon,
                            leadingTint = option.leadingTint,
                            trailingIcon = option.trailingIcon,
                            selected = option.selected,
                            focusRequester = if (index == 0) firstValueFocus else null,
                            onClick = option.onClick
                        )
                    } ?: sections.map { entry ->
                        FilterPanelRowModel(
                            label = entry.label,
                            leadingIcon = entry.icon,
                            leadingTint = null,
                            trailingIcon = null,
                            selected = false,
                            activeDot = entry in active,
                            chevron = true,
                            activateOnRight = true,
                            focusRequester = sectionRowFocus[entry],
                            onClick = { openSection = entry }
                        )
                    }
                    if (options != null && options.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = PicnicColors.Accent,
                                modifier = Modifier
                                    .size(48.dp)
                                    .focusRequester(firstValueFocus)
                                    .focusable()
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .verticalFadingEdges(
                                    topFade = listState.canScrollBackward,
                                    bottomFade = listState.canScrollForward,
                                    length = PanelFadeLength
                                )
                                .focusGroup(),
                            contentPadding = PaddingValues(horizontal = PanelContentInset),
                            verticalArrangement = Arrangement.spacedBy(PanelRowSpacing)
                        ) {
                            rows.forEachIndexed { index, row ->
                                item(key = index) {
                                    FilterPanelRow(
                                        row = row,
                                        focusRequester = row.focusRequester
                                    )
                                }
                            }
                        }
                    }
                    if (section == null) {
                        ClearFiltersRow(
                            focusRequester = clearRowFocus,
                            onClick = onResetAll
                        )
                    }
                }
            }
        }
    }
}

private fun buildSectionOptions(
    section: GridFilterSection,
    filter: MediaGridFilter,
    sort: GridSortSpec,
    facets: GridFilterFacets,
    onFilterChange: (MediaGridFilter) -> Unit,
    onSortChange: (GridSortSpec) -> Unit
): List<PanelOption> = when (section) {
    GridFilterSection.CONTENT_TYPE -> GridContentType.entries.map { option ->
        PanelOption(
            label = option.label,
            selected = filter.contentType == option,
            onClick = { onFilterChange(filter.copy(contentType = option)) }
        )
    }
    GridFilterSection.SORT_ORDER -> listOf(
        PanelOption(
            label = "Ascending",
            selected = sort.ascending,
            trailingIcon = Icons.Filled.ArrowUpward,
            onClick = { onSortChange(sort.copy(ascending = true)) }
        ),
        PanelOption(
            label = "Descending",
            selected = !sort.ascending,
            trailingIcon = Icons.Filled.ArrowDownward,
            onClick = { onSortChange(sort.copy(ascending = false)) }
        )
    )
    GridFilterSection.SORT_BY -> GridSortField.entries.map { field ->
        PanelOption(
            label = field.label,
            selected = sort.field == field,
            onClick = { onSortChange(sort.copy(field = field)) }
        )
    }
    GridFilterSection.WATCHED -> WatchedFilter.entries.map { option ->
        PanelOption(
            label = option.label,
            selected = filter.watched == option,
            onClick = { onFilterChange(filter.copy(watched = option)) }
        )
    }
    GridFilterSection.FAVORITES -> listOf(
        PanelOption(
            label = "Favorites only",
            selected = filter.favoritesOnly,
            onClick = { onFilterChange(filter.copy(favoritesOnly = !filter.favoritesOnly)) }
        )
    )
    GridFilterSection.GENRES -> facets.genres.map { genre ->
        PanelOption(
            label = genre.name.orEmpty(),
            selected = genre.id in filter.genreIds,
            onClick = { onFilterChange(filter.copy(genreIds = filter.genreIds.toggle(genre.id))) }
        )
    }
    GridFilterSection.STUDIOS -> facets.studios.map { studio ->
        PanelOption(
            label = studio.name.orEmpty(),
            selected = studio.id in filter.studioIds,
            onClick = { onFilterChange(filter.copy(studioIds = filter.studioIds.toggle(studio.id))) }
        )
    }
    GridFilterSection.COMMUNITY_RATING -> buildList {
        add(
            PanelOption(
                label = "Any",
                selected = filter.minCommunityRating == null,
                onClick = { onFilterChange(filter.copy(minCommunityRating = null)) }
            )
        )
        CommunityRatingSteps.forEach { step ->
            add(
                PanelOption(
                    label = if (step == 10) "10" else "$step+",
                    selected = filter.minCommunityRating == step,
                    leadingIcon = Icons.Filled.Star,
                    leadingTint = RatingStarGold,
                    onClick = { onFilterChange(filter.copy(minCommunityRating = step)) }
                )
            )
        }
    }
    GridFilterSection.PARENTAL -> facets.parentalRatings.map { rating ->
        PanelOption(
            label = rating,
            selected = rating in filter.parentalRatings,
            onClick = {
                onFilterChange(
                    filter.copy(parentalRatings = filter.parentalRatings.toggle(rating))
                )
            }
        )
    }
    GridFilterSection.RESOLUTION -> ResolutionFilter.entries.map { option ->
        PanelOption(
            label = option.label,
            selected = filter.resolution == option,
            onClick = { onFilterChange(filter.copy(resolution = option)) }
        )
    }
    GridFilterSection.DECADE -> facets.decades.map { decade ->
        PanelOption(
            label = "${decade}s",
            selected = decade in filter.decades,
            onClick = { onFilterChange(filter.copy(decades = filter.decades.toggle(decade))) }
        )
    }
}

private val CommunityRatingSteps = listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
private val RatingStarGold = Color(0xFFE0C05C)

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private data class FilterPanelRowModel(
    val label: String,
    val leadingIcon: ImageVector?,
    val leadingTint: Color?,
    val trailingIcon: ImageVector?,
    val selected: Boolean,
    val activeDot: Boolean = false,
    val chevron: Boolean = false,
    val activateOnRight: Boolean = false,
    val focusRequester: FocusRequester? = null,
    val onClick: () -> Unit
)

@Composable
private fun FilterPanelRow(
    row: FilterPanelRowModel,
    focusRequester: FocusRequester?
) {
    PicnicListRow(
        focusRequester = focusRequester,
        metrics = GridRowMetrics,
        keys = PanelRowKeys(blockLeft = false, activateOnRight = row.activateOnRight),
        onActivate = row.onClick
    ) { focused ->
        if (row.leadingIcon != null) {
            Icon(
                row.leadingIcon,
                contentDescription = null,
                tint = row.leadingTint ?: if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = row.label,
            color = rowPrimaryColor(focused),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(iterations = if (focused) 3 else 0)
        )
        if (row.activeDot) {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(PicnicColors.Accent)
            )
        }
        when {
            row.trailingIcon != null -> Icon(
                row.trailingIcon,
                contentDescription = null,
                tint = rowTrailingColor(focused),
                modifier = Modifier.size(16.dp)
            )
            row.selected -> Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = rowTrailingColor(focused),
                modifier = Modifier.size(16.dp)
            )
            row.chevron -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ClearFiltersRow(
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    Column(Modifier.padding(horizontal = PanelContentInset)) {
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Spacer(Modifier.height(6.dp))
        PicnicListRow(
            focusRequester = focusRequester,
            metrics = GridRowMetrics,
            keys = PanelRowKeys(blockLeft = false),
            onActivate = onClick
        ) { focused ->
            Icon(
                Icons.Filled.RestartAlt,
                contentDescription = null,
                tint = if (focused) Color.Black.copy(alpha = 0.72f) else PicnicColors.Accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Clear filters",
                color = rowPrimaryColor(focused),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun GridEmptyFilteredState(
    filterActive: Boolean,
    focusRequester: FocusRequester,
    downEntryFocus: FocusRequester?,
    upExitFocus: FocusRequester?,
    clearing: Boolean,
    onClearFilters: () -> Unit
) {
    CenteredMessage(
        message = if (filterActive) "No titles match these filters" else "Nothing here yet"
    ) {
        if (filterActive) {
            ActionButton(
                label = "Clear filters",
                onActivate = onClearFilters,
                focusRequester = focusRequester,
                busy = clearing,
                modifier = Modifier
                    .then(downEntryFocus?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .focusProperties { up = upExitFocus ?: FocusRequester.Default }
            )
        }
    }
}
