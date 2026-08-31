@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.media.WatchStats
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.SeerrRequestDisplay
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.RowFocusState
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellSlot
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private val BadgeShape = RoundedCornerShape(10.dp)
private val BadgeFill = Color.White.copy(alpha = 0.07f)
private val RowPeekInset = 40.dp
private val PaneEndInset = 24.dp

private object PinnedColumn : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
private val RowEndInset = 24.dp
private val RowGap = 20.dp
private val PanelBottomInset = 36.dp

private const val ROW_FAVORITES = 0
private const val ROW_REQUESTS = 1

private fun favoriteSubtitle(item: BaseItemDto): String? = when (item.type) {
    BaseItemKind.BOX_SET -> "Collection"
    BaseItemKind.PLAYLIST -> "Playlist"
    else -> null
}

private fun Modifier.outsetRow(start: Dp, end: Dp) = layout { measurable, constraints ->
    val extra = start.roundToPx() + end.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-start.roundToPx(), 0) }
}

@Composable
internal fun AccountSettingsPanel(
    viewModel: SettingsViewModel,
    onOpenItem: (BaseItemDto, String?, String?) -> Unit,
    onOpenSeerrDetail: ((SeerrMediaRequest) -> Unit)?,
    focus: SettingsPanelFocus,
    modifier: Modifier = Modifier
) {
    val username by viewModel.activeUsername.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.activeUserImageUrl.collectAsStateWithLifecycle()
    val stats by viewModel.watchStats.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val requests by viewModel.myRequests.collectAsStateWithLifecycle()
    val seerr by viewModel.seerrState.collectAsStateWithLifecycle()
    val linked = seerr.linkState == SeerrLinkState.Linked

    val favoritesFocus = rememberRowFocusState()
    val requestsFocus = rememberRowFocusState()
    var lastRow by rememberSaveable { mutableIntStateOf(ROW_FAVORITES) }

    val configuration = LocalConfiguration.current
    val cardStyle = remember(configuration.screenHeightDp) {
        val height = (configuration.screenHeightDp * (118f / 540f)).dp
        BrowseCardStyle(width = height * (2f / 3f), height = height, landscape = false)
    }
    val cardSpacing = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * (14f / 960f)).dp
    }

    LaunchedEffect(linked) {
        if (linked) viewModel.refreshMyRequests()
    }

    val showRequests = linked && requests.isNotEmpty()

    LaunchedEffect(favorites.size, requests.size) {
        favoritesFocus.resolveAgainst(favorites)
        requestsFocus.resolveAgainst(requests)
        when {
            lastRow == ROW_REQUESTS && showRequests -> requestsFocus.restoreFocus()
            lastRow == ROW_FAVORITES &&
                favorites.isNotEmpty() &&
                favoritesFocus.focusedKey != null -> favoritesFocus.restoreFocus()
        }
    }

    val horizontalRowSpec = LocalBringIntoViewSpec.current
    val scrollState = rememberScrollState()

    LaunchedEffect(lastRow, showRequests, scrollState.maxValue) {
        val target = if (lastRow == ROW_REQUESTS && showRequests) scrollState.maxValue else 0
        if (scrollState.value != target) scrollState.animateScrollTo(target)
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides PinnedColumn) {
        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .onFocusChanged { focus.onFocusChanged(it.hasFocus) },
            verticalArrangement = Arrangement.spacedBy(RowGap)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileAvatar(name = username, imageUrl = avatarUrl)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        username,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PicnicColors.OnDark
                    )
                    WatchCountBadges(stats = stats)
                }
            }

            if (favorites.isNotEmpty()) {
                FavoritesRow(
                    favorites = favorites,
                    rowFocus = favoritesFocus,
                    style = cardStyle,
                    cardSpacing = cardSpacing,
                    enterFr = focus.enterFr,
                    leftFocus = focus.leftFocus,
                    downFocus = if (showRequests) {
                        { requestsFocus.requesterAt(requestsFocus.focusedIndex) }
                    } else {
                        null
                    },
                    onRowFocused = { requester ->
                        lastRow = ROW_FAVORITES
                        focus.onRowFocused(requester)
                    },
                    onOpenItem = onOpenItem,
                    horizontalRowSpec = horizontalRowSpec
                )
            }

            if (showRequests) {
                RequestsRow(
                    requests = requests,
                    rowFocus = requestsFocus,
                    style = cardStyle,
                    cardSpacing = cardSpacing,
                    enterFr = if (favorites.isEmpty()) focus.enterFr else null,
                    leftFocus = focus.leftFocus,
                    upFocus = if (favorites.isNotEmpty()) {
                        { favoritesFocus.requesterAt(favoritesFocus.focusedIndex) }
                    } else {
                        null
                    },
                    seerrBaseUrl = seerr.serverUrl,
                    cacheImages = seerr.cacheImages,
                    onRowFocused = { requester ->
                        lastRow = ROW_REQUESTS
                        focus.onRowFocused(requester)
                    },
                    onOpenSeerrDetail = onOpenSeerrDetail,
                    horizontalRowSpec = horizontalRowSpec
                )
            }
            Spacer(Modifier.height(PanelBottomInset))
        }
    }
}

@Composable
private fun WatchCountBadges(stats: WatchStats?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WatchCountBadge(stats?.movies, "Movies")
        WatchCountBadge(stats?.shows, "Shows")
        WatchCountBadge(stats?.episodes, "Episodes")
    }
}

@Composable
private fun WatchCountBadge(count: Int?, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(BadgeShape)
            .background(BadgeFill)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            count?.toString() ?: "—",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = PicnicColors.OnDark
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = PicnicColors.OnDarkMuted
        )
    }
}

@Composable
private fun AccountRowHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = PicnicColors.OnDark
    )
}

@Composable
private fun AccountCardRow(
    rowFocus: RowFocusState,
    cardSpacing: Dp,
    horizontalRowSpec: BringIntoViewSpec,
    content: LazyListScope.() -> Unit
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalRowSpec) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(cardSpacing),
            contentPadding = PaddingValues(start = RowPeekInset, end = RowEndInset),
            modifier = rowFocus.rowModifier().outsetRow(start = RowPeekInset, end = PaneEndInset),
            content = content
        )
    }
}

@Composable
private fun FavoritesRow(
    favorites: List<BaseItemDto>,
    rowFocus: RowFocusState,
    style: BrowseCardStyle,
    cardSpacing: Dp,
    enterFr: FocusRequester,
    leftFocus: FocusRequester,
    downFocus: (() -> FocusRequester)?,
    onRowFocused: (FocusRequester) -> Unit,
    onOpenItem: (BaseItemDto, String?, String?) -> Unit,
    horizontalRowSpec: BringIntoViewSpec
) {
    val images = LocalImageUrls.current
    val contextMenu = LocalContextMenuHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AccountRowHeading("Your favorites")
        AccountCardRow(rowFocus, cardSpacing, horizontalRowSpec) {
            itemsIndexed(favorites, key = { _, item -> item.id }) { index, item ->
                Box(Modifier.gridCellSlot(style), contentAlignment = Alignment.TopCenter) {
                    MediaGridCard(
                        item = item,
                        style = style,
                        subtitleOverride = favoriteSubtitle(item),
                        showStatus = false,
                        focusRequester = rowFocus.requesterAt(index),
                        upFocus = { FocusRequester.Cancel },
                        downFocus = downFocus ?: { FocusRequester.Cancel },
                        leftFocus = if (index == 0) leftFocus else null,
                        onClick = {
                            val nav = images.navImages(item)
                            onOpenItem(item, nav.bgUrl, nav.ambUrl)
                        },
                        onLongClick = { contextMenu.show(item) },
                        onFocused = {
                            rowFocus.onItemFocused(index, item.id.toString())
                            onRowFocused(rowFocus.requesterAt(index))
                        },
                        modifier = Modifier
                            .padding(top = style.topInset)
                            .then(if (index == 0) Modifier.focusRequester(enterFr) else Modifier)
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestsRow(
    requests: List<SeerrRequestDisplay>,
    rowFocus: RowFocusState,
    style: BrowseCardStyle,
    cardSpacing: Dp,
    enterFr: FocusRequester?,
    leftFocus: FocusRequester,
    upFocus: (() -> FocusRequester)?,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    onRowFocused: (FocusRequester) -> Unit,
    onOpenSeerrDetail: ((SeerrMediaRequest) -> Unit)?,
    horizontalRowSpec: BringIntoViewSpec
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AccountRowHeading("Your requests")
        AccountCardRow(rowFocus, cardSpacing, horizontalRowSpec) {
            itemsIndexed(requests, key = { _, row -> row.request.id }) { index, row ->
                Box(Modifier.gridCellSlot(style), contentAlignment = Alignment.TopCenter) {
                    AccountRequestCard(
                        row = row,
                        seerrBaseUrl = seerrBaseUrl,
                        cacheImages = cacheImages,
                        style = style,
                        focusRequester = rowFocus.requesterAt(index),
                        upFocus = upFocus ?: { FocusRequester.Cancel },
                        downFocus = { FocusRequester.Cancel },
                        leftFocus = if (index == 0) leftFocus else null,
                        onClick = { onOpenSeerrDetail?.invoke(row.request) },
                        onFocused = {
                            rowFocus.onItemFocused(index, row.request.id.toString())
                            onRowFocused(rowFocus.requesterAt(index))
                        },
                        modifier = Modifier
                            .padding(top = style.topInset)
                            .then(if (index == 0 && enterFr != null) Modifier.focusRequester(enterFr) else Modifier)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(name: String, imageUrl: String?) {
    var avatarFailed by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(rememberIdentityBrush(name.ifBlank { "?" })),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl == null || avatarFailed) {
            Text(
                name.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = PicnicColors.OnDark
            )
        }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onState = { avatarFailed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
