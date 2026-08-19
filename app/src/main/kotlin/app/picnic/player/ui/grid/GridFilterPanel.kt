@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import app.picnic.player.ui.common.PanelHeader
import app.picnic.player.ui.common.PanelRowKeys
import app.picnic.player.ui.common.PanelRowMetrics
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.rowPrimaryColor
import app.picnic.player.ui.common.rowTrailingColor
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PanelDimScrim = Color(0x66000000)
private val PanelGlassFill = Color(0xF2181E24)
private val PanelCornerRadius = 20.dp
private val PanelEdgeInset = 24.dp
private val ContentInset = 12.dp
private val GridRowMetrics = PanelRowMetrics(innerPadding = 12.dp, cornerRadius = 8.dp)
private val PanelWidth = 300.dp
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
        var panelHasFocus by remember { mutableStateOf(false) }

        LaunchedEffect(openSection, shown, facets) {
            if (!shown) return@LaunchedEffect
            val target = when (val section = openSection) {
                null -> {
                    val returnTo = lastOpenedSection
                    if (returnTo != null) {
                        runCatching {
                            topListState.scrollToItem(sections.indexOf(returnTo).coerceAtLeast(0))
                        }
                    }
                    sectionRowFocus[returnTo ?: sections.firstOrNull()] ?: return@LaunchedEffect
                }
                else -> {
                    lastOpenedSection = section
                    firstValueFocus
                }
            }
            repeat(20) {
                runCatching { target.requestFocus() }
                if (panelHasFocus) return@LaunchedEffect
                delay(32)
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
                        .width(PanelWidth)
                        .fillMaxHeight()
                        .padding(top = PanelEdgeInset, bottom = PanelEdgeInset, end = PanelEdgeInset)
                        .clip(RoundedCornerShape(PanelCornerRadius))
                        .background(PanelGlassFill)
                        .padding(vertical = 16.dp)
                        .onFocusChanged { panelHasFocus = it.hasFocus }
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusGroup()
                ) {
                    when (val section = openSection) {
                        null -> {
                            PanelHeader("Sort & filter")
                            LazyColumn(
                                state = topListState,
                                modifier = Modifier.weight(1f).focusGroup(),
                                contentPadding = PaddingValues(horizontal = ContentInset),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                sections.forEach { entry ->
                                    item {
                                        SectionRow(
                                            section = entry,
                                            valueLabel = entry.label,
                                            active = entry in activeFilterSections(filter, sort),
                                            focusRequester = sectionRowFocus[entry],
                                            onClick = { openSection = entry }
                                        )
                                    }
                                }
                                item {
                                    ClearFiltersRow(
                                        focusRequester = clearRowFocus,
                                        onClick = onResetAll
                                    )
                                }
                            }
                        }
                        else -> {
                            PanelHeader(section.label, icon = section.icon)
                            val options = buildSectionOptions(
                                section,
                                filter,
                                sort,
                                facets,
                                onFilterChange = onFilterChange,
                                onSortChange = onSortChange
                            )
                            if (options.isEmpty()) {
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
                                    modifier = Modifier.weight(1f).focusGroup(),
                                    contentPadding = PaddingValues(horizontal = ContentInset),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    options.forEachIndexed { index, option ->
                                        item {
                                            FilterRow(
                                                label = option.label,
                                                selected = option.selected,
                                                trailingIcon = option.trailingIcon,
                                                leadingIcon = option.leadingIcon,
                                                leadingTint = option.leadingTint,
                                                focusRequester = if (index == 0) firstValueFocus else null,
                                                onClick = option.onClick
                                            )
                                        }
                                    }
                                }
                            }
                        }
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

/** Descending minimums: the server has no maximum-rating parameter, so "below N" is not expressible. */
private val CommunityRatingSteps = listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
private val RatingStarGold = Color(0xFFE0C05C)

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun SectionRow(
    section: GridFilterSection,
    valueLabel: String,
    active: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    PicnicListRow(
        focusRequester = focusRequester,
        metrics = GridRowMetrics,
        keys = PanelRowKeys(blockLeft = false, activateOnRight = true),
        onActivate = onClick
    ) { focused ->
        Icon(
            section.icon,
            contentDescription = null,
            tint = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = valueLabel,
            color = rowPrimaryColor(focused),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (active) {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(PicnicColors.Accent)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ClearFiltersRow(
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    Column {
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
private fun FilterRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    trailingIcon: ImageVector? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null
) {
    PicnicListRow(
        focusRequester = focusRequester,
        metrics = GridRowMetrics,
        keys = PanelRowKeys(blockLeft = false),
        onActivate = onClick
    ) { focused ->
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = leadingTint ?: if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            color = rowPrimaryColor(focused),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (trailingIcon != null) {
            Icon(
                trailingIcon,
                contentDescription = null,
                tint = rowTrailingColor(focused),
                modifier = Modifier.size(16.dp)
            )
        } else if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = rowTrailingColor(focused),
                modifier = Modifier.size(16.dp)
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
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (filterActive) "No titles match these filters" else "Nothing here yet",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.titleMedium
        )
        if (filterActive) {
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(GridRowMetrics.cornerRadius))
                    .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .focusRequester(focusRequester)
                    .then(downEntryFocus?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .focusProperties { up = upExitFocus ?: FocusRequester.Default }
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .onKeyEvent { event ->
                        if (
                            !clearing &&
                            event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter)
                        ) {
                            onClearFilters()
                            true
                        } else {
                            false
                        }
                    }
            ) {
                if (clearing) {
                    CircularProgressIndicator(
                        color = if (focused) Color.Black else Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = "Clear filters",
                        color = if (focused) Color.Black else Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}
