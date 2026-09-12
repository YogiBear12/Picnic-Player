@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package app.picnic.player.ui.person

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.FilmographyRow
import app.picnic.player.data.seerr.FilmographyTab
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.data.seerr.catalogKey
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.DetailContentStartInset
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.common.CenteredMessage
import app.picnic.player.ui.common.ContentCacheWindow
import app.picnic.player.ui.common.GlassRow
import app.picnic.player.ui.common.LoadFailedState
import app.picnic.player.ui.common.LocalSeerrCardMenu
import app.picnic.player.ui.common.Pill
import app.picnic.player.ui.common.PillTab
import app.picnic.player.ui.common.PillTabRow
import app.picnic.player.ui.common.rememberKeyedFocusRequesters
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.requestFocusWhenVisible
import app.picnic.player.ui.theme.PicnicColors

private val YearColumnWidth = 52.dp
private val TabListSpacing = 20.dp
private val RowSpacing = 12.dp

private val FilmographyTab.label: String
    get() = when (this) {
        FilmographyTab.Actor -> "Actor"
        FilmographyTab.Director -> "Director"
        FilmographyTab.Writer -> "Writer"
        FilmographyTab.Producer -> "Producer"
        FilmographyTab.Composer -> "Composer"
        FilmographyTab.Appearances -> "Appearances"
        FilmographyTab.AdditionalCredits -> "Additional Credits"
    }

@Composable
fun FilmographyScreen(
    tmdbId: Int,
    personName: String,
    knownForDepartment: String?,
    onItem: (SeerrCatalogItem, String?, String?) -> Unit,
    viewModel: FilmographyViewModel = hiltViewModel<FilmographyViewModel, FilmographyViewModel.Factory>(
        creationCallback = { factory -> factory.create(tmdbId, knownForDepartment) }
    )
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    PublishBackdrop(null)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = browseLayoutMetrics(maxWidth, maxHeight)
    val retryFr = remember { FocusRequester() }
    val tabRequesters = rememberKeyedFocusRequesters()

    var savedTab by rememberSaveable { mutableStateOf<FilmographyTab?>(null) }
    var restoreRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    val tabs = state.tabs
    val selected = tabs.firstOrNull { it.tab == savedTab } ?: tabs.firstOrNull()
    val selectedTab = selected?.tab
    val activeTabFr = selectedTab?.let { tabRequesters[it] }
    val listState = rememberLazyListState(cacheWindow = ContentCacheWindow)
    val restoreRowFr = remember { FocusRequester() }
    var listHasFocus by remember { mutableStateOf(false) }
    var lastScrolledTab by remember { mutableStateOf<FilmographyTab?>(null) }

    LaunchedEffect(selectedTab) {
        if (lastScrolledTab != null && lastScrolledTab != selectedTab) {
            listState.scrollToItem(0)
        }
        lastScrolledTab = selectedTab
    }

    LaunchedEffect(state.loading, state.error, selectedTab, restoreRowKey) {
        if (state.loading) return@LaunchedEffect
        when {
            state.error != null -> retryFr.requestFocusWhenAttached()
            selected != null && selectedTab != null && restoreRowKey != null -> {
                if (listHasFocus) return@LaunchedEffect
                val key = restoreRowKey ?: return@LaunchedEffect
                val index = selected.rows.indexOfFirst { it.item.catalogKey == key }
                val restored = index >= 0 &&
                    restoreRowFr.requestFocusWhenVisible(listState, index) { listHasFocus }
                restoreRowKey = null
                seeded = true
                if (!restored) {
                    tabRequesters[selectedTab].requestFocusWhenAttached(maxFrames = 20)
                }
            }
            !seeded && selectedTab != null -> {
                tabRequesters[selectedTab].requestFocusWhenAttached()
                seeded = true
            }
        }
    }

    val seerrCardMenu = LocalSeerrCardMenu.current
    val ambientPrewarmer = LocalAmbientPrewarmer.current

    fun openItem(item: SeerrCatalogItem) {
        restoreRowKey = item.catalogKey
        val nav = SeerrImages.navImages(state.seerrBaseUrl, item, state.seerrCacheImages)
        ambientPrewarmer.warm(nav.ambUrl)
        onItem(item, nav.bgUrl, nav.ambUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = DetailContentStartInset,
                end = metrics.hInset,
                top = 48.dp
            )
    ) {
        Text(
            "$personName Filmography",
            style = MaterialTheme.typography.headlineMedium,
            color = PicnicColors.OnDark,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(TabListSpacing))
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
            state.error != null -> LoadFailedState(
                message = state.error.orEmpty(),
                retryFocus = retryFr,
                onRetry = viewModel::retry
            )
            tabs.isEmpty() -> CenteredMessage(message = "No filmography")
            selected != null && selectedTab != null -> {
                PillTabRow(
                    selectedIndex = tabs.indexOfFirst { it.tab == selectedTab },
                    activeTabFocus = activeTabFr
                ) {
                    tabs.forEach { tab ->
                        PillTab(
                            selected = tab.tab == selectedTab,
                            focusRequester = tabRequesters[tab.tab],
                            onSelect = { savedTab = tab.tab }
                        ) {
                            Text(
                                "${tab.tab.label} (${tab.rows.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(TabListSpacing))
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = metrics.bottomInset),
                    verticalArrangement = Arrangement.spacedBy(RowSpacing),
                    modifier = Modifier.onFocusChanged { listHasFocus = it.hasFocus }
                ) {
                    itemsIndexed(selected.rows, key = { _, row -> row.item.catalogKey }) { index, row ->
                        FilmographyCreditRow(
                            row = row,
                            blockDown = index == selected.rows.lastIndex,
                            upTarget = if (index == 0) activeTabFr else null,
                            focusRequester = restoreRowFr.takeIf { row.item.catalogKey == restoreRowKey },
                            onActivate = { openItem(row.item) },
                            onLongPress = { seerrCardMenu(row.item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmographyCreditRow(
    row: FilmographyRow,
    blockDown: Boolean,
    upTarget: FocusRequester?,
    focusRequester: FocusRequester?,
    onActivate: () -> Unit,
    onLongPress: () -> Unit
) {
    GlassRow(
        onClick = onActivate,
        onLongClick = onLongPress,
        modifier = Modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusProperties {
                right = FocusRequester.Cancel
                if (upTarget != null) up = upTarget
                if (blockDown) down = FocusRequester.Cancel
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                row.year.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = PicnicColors.OnDarkMuted,
                textAlign = TextAlign.Start,
                maxLines = 1,
                modifier = Modifier.width(YearColumnWidth)
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = PicnicColors.OnDark)) {
                        append(row.title)
                    }
                    if (row.roleAs != null) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = PicnicColors.OnDarkMuted)) {
                            append(" as ")
                            append(row.roleAs)
                        }
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (row.inLibrary) {
                Pill(
                    text = "In Library",
                    color = PicnicColors.Success,
                    fill = PicnicColors.Success.copy(alpha = 0.16f),
                    horizontal = 10.dp,
                    vertical = 4.dp
                )
            }
        }
    }
}
