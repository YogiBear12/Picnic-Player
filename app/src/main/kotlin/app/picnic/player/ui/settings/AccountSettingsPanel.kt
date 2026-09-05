@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import app.picnic.player.data.seerr.canViewSeerrIssues
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.DetailMediaRow
import app.picnic.player.ui.browse.episodeCardSubtitle
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.RowFocusState
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellSlot
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private val BadgeShape = RoundedCornerShape(10.dp)
private val BadgeFill = Color.White.copy(alpha = 0.07f)
private val SkeletonFill = Color.White.copy(alpha = 0.14f)
private val RowPeekInset = 40.dp
private val RowGap = 20.dp
private val RowToRowGap = 12.dp
private const val FavoritesTitle = "Your favorites"
private const val RequestsTitle = "Your requests"

private object PinnedColumn : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private enum class AccountBlock { FAVORITES, REQUESTS, ACTIONS }

private data class AccountCardFocus(
    val requester: FocusRequester?,
    val up: () -> FocusRequester,
    val down: () -> FocusRequester,
    val left: FocusRequester?,
    val right: FocusRequester?,
    val onFocused: () -> Unit
)

internal fun favoriteTitle(item: BaseItemDto): String? = if (item.type == BaseItemKind.EPISODE) item.seriesName else null

internal fun favoriteSubtitle(item: BaseItemDto): String? = when (item.type) {
    BaseItemKind.BOX_SET -> "Collection"
    BaseItemKind.PLAYLIST -> "Playlist"
    BaseItemKind.EPISODE -> episodeCardSubtitle(item)
    else -> null
}

private fun Modifier.outsetRow(start: Dp) = layout { measurable, constraints ->
    val extra = start.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-start.roundToPx(), 0) }
}

private fun Modifier.blockTop(block: AccountBlock, tops: MutableMap<AccountBlock, Float>) = onGloballyPositioned { tops[block] = it.positionInParent().y }

@Composable
internal fun AccountSettingsPanel(
    viewModel: SettingsViewModel,
    onSignedOut: () -> Unit,
    onOpenItem: (BaseItemDto, String?, String?) -> Unit,
    onOpenSeerrDetail: ((SeerrMediaRequest) -> Unit)?,
    onOpenIssues: () -> Unit,
    restoreRow: SubPageRow?,
    onRestored: () -> Unit,
    focus: SettingsPanelFocus,
    modifier: Modifier = Modifier
) {
    val username by viewModel.activeUsername.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.activeUserImageUrl.collectAsStateWithLifecycle()
    val stats by viewModel.watchStats.collectAsStateWithLifecycle()
    val loadedFavorites by viewModel.favorites.collectAsStateWithLifecycle()
    val loadedRequests by viewModel.myRequests.collectAsStateWithLifecycle()
    val openIssueCount by viewModel.openIssueCount.collectAsStateWithLifecycle()
    val seerr by viewModel.seerrState.collectAsStateWithLifecycle()
    val connecting by viewModel.seerrConnecting.collectAsStateWithLifecycle()
    val connectError by viewModel.seerrConnectError.collectAsStateWithLifecycle()
    val linked = seerr.linkState == SeerrLinkState.Linked
    val favoritesLoading by viewModel.favoritesLoading.collectAsStateWithLifecycle()
    val requestsLoading by viewModel.requestsLoading.collectAsStateWithLifecycle()
    val favorites = loadedFavorites.orEmpty()
    val requests = loadedRequests.orEmpty()
    var showConnectDialog by remember { mutableStateOf(false) }
    val seerrButtonFr = remember { FocusRequester() }
    val issuesButtonFr = remember { FocusRequester() }
    var lastActionFocus by remember { mutableStateOf(seerrButtonFr) }

    val favoritesFocus = rememberRowFocusState()
    val requestsFocus = rememberRowFocusState()
    val favoriteKeys = remember(favorites) { favorites.map { it.id.toString() } }
    val requestKeys = remember(requests) { requests.map { it.request.id.toString() } }
    var lastBlock by rememberSaveable { mutableStateOf(AccountBlock.FAVORITES) }
    val blockTops = remember { mutableStateMapOf<AccountBlock, Float>() }

    val configuration = LocalConfiguration.current
    val cardStyle = remember(configuration.screenHeightDp) {
        val height = (configuration.screenHeightDp * (118f / 540f)).dp
        BrowseCardStyle(width = height * (2f / 3f), height = height, landscape = false)
    }
    val cardSpacing = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * (14f / 960f)).dp
    }

    LaunchedEffect(Unit) { viewModel.onAccountOpened() }

    val canViewIssues = linked && canViewSeerrIssues(seerr.user)

    LaunchedEffect(restoreRow, canViewIssues) {
        if (restoreRow == SubPageRow.ISSUES && canViewIssues) {
            lastBlock = AccountBlock.ACTIONS
            issuesButtonFr.requestFocusWhenAttached(maxFrames = 20)
            onRestored()
        }
    }

    LaunchedEffect(linked) {
        if (!linked) return@LaunchedEffect
        if (showConnectDialog) {
            showConnectDialog = false
            seerrButtonFr.requestFocusWhenAttached(maxFrames = 20)
        }
        coroutineScope {
            launch { viewModel.refreshMyRequests() }
            launch { viewModel.refreshIssueCounts() }
        }
    }

    val showRequests = linked && requests.isNotEmpty()
    val requestsGap = if (favorites.isEmpty() && !favoritesLoading) RowGap else RowToRowGap

    val focusedItemVanished = when (lastBlock) {
        AccountBlock.FAVORITES -> favoritesFocus.focusedKey?.let { it !in favoriteKeys } == true
        AccountBlock.REQUESTS -> requestsFocus.focusedKey?.let { it !in requestKeys } == true
        AccountBlock.ACTIONS -> false
    }

    focus.onHoldSelection(focusedItemVanished)

    LaunchedEffect(favoriteKeys, requestKeys) {
        try {
            val favoritesLost = lastBlock == AccountBlock.FAVORITES &&
                favoritesFocus.focusedKey != null &&
                favoriteKeys.isEmpty()
            val requestsLost = lastBlock == AccountBlock.REQUESTS &&
                requestsFocus.focusedKey != null &&
                (!showRequests || requestKeys.isEmpty())
            when {
                favoritesLost && showRequests -> {
                    lastBlock = AccountBlock.REQUESTS
                    requestsFocus.restorePinned(requestKeys)
                }
                favoritesLost || requestsLost -> {
                    lastBlock = AccountBlock.ACTIONS
                    seerrButtonFr.requestFocusWhenAttached(maxFrames = 20)
                }
                lastBlock == AccountBlock.REQUESTS && showRequests -> requestsFocus.restorePinned(requestKeys)
                lastBlock == AccountBlock.FAVORITES && favoritesFocus.focusedKey != null ->
                    favoritesFocus.restorePinned(favoriteKeys)
                else -> Unit
            }
        } finally {
            focus.onHoldSelection(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose { focus.onHoldSelection(false) }
    }

    val horizontalRowSpec = LocalBringIntoViewSpec.current
    val scrollState = rememberScrollState()

    val firstBlockTop = blockTops.values.minOrNull() ?: 0f
    val scrollTarget = ((blockTops[lastBlock] ?: firstBlockTop) - firstBlockTop)
        .roundToInt()
        .coerceIn(0, scrollState.maxValue)

    LaunchedEffect(scrollTarget) {
        if (scrollState.value != scrollTarget) scrollState.animateScrollTo(scrollTarget)
    }

    val images = LocalImageUrls.current
    val contextMenu = LocalContextMenuHandler.current

    CompositionLocalProvider(LocalBringIntoViewSpec provides PinnedColumn) {
        BoxWithConstraints(modifier) {
            val contentMinHeight = maxHeight - SettingsTopInset - SettingsBottomInset
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(top = SettingsTopInset, bottom = SettingsBottomInset)
                    .onFocusChanged { focus.onFocusChanged(it.hasFocus) }
            ) {
                Column(
                    modifier = Modifier.heightIn(min = contentMinHeight),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
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

                        AccountRowSkeleton(
                            title = FavoritesTitle,
                            visible = favoritesLoading,
                            exit = if (favorites.isEmpty()) CollapseSkeleton else ExitTransition.None,
                            topGap = RowGap,
                            style = cardStyle,
                            cardSpacing = cardSpacing
                        )

                        if (favorites.isNotEmpty()) {
                            Spacer(Modifier.height(RowGap))
                            AccountRow(
                                title = FavoritesTitle,
                                items = favorites,
                                keys = favoriteKeys,
                                rowState = favoritesFocus,
                                style = cardStyle,
                                cardSpacing = cardSpacing,
                                horizontalRowSpec = horizontalRowSpec,
                                enterFr = focus.enterFr,
                                leftFocus = focus.leftFocus,
                                upFocus = null,
                                downFocus = if (showRequests) {
                                    { requestsFocus.pinFocus }
                                } else {
                                    { lastActionFocus }
                                },
                                onRowFocused = { requester ->
                                    lastBlock = AccountBlock.FAVORITES
                                    focus.onRowFocused(requester)
                                },
                                itemKey = { it.id },
                                modifier = Modifier.blockTop(AccountBlock.FAVORITES, blockTops)
                            ) { item, cardFocus, cardModifier ->
                                MediaGridCard(
                                    item = item,
                                    style = cardStyle,
                                    titleOverride = favoriteTitle(item),
                                    subtitleOverride = favoriteSubtitle(item),
                                    showStatus = false,
                                    focusRequester = cardFocus.requester,
                                    upFocus = cardFocus.up,
                                    downFocus = cardFocus.down,
                                    leftFocus = cardFocus.left,
                                    rightFocus = cardFocus.right,
                                    onClick = {
                                        val nav = images.navImages(item)
                                        onOpenItem(item, nav.bgUrl, nav.ambUrl)
                                    },
                                    onLongClick = { contextMenu.show(item) },
                                    onFocused = cardFocus.onFocused,
                                    modifier = cardModifier
                                )
                            }
                        }

                        AccountRowSkeleton(
                            title = RequestsTitle,
                            visible = requestsLoading,
                            exit = if (requests.isEmpty()) CollapseSkeleton else ExitTransition.None,
                            topGap = requestsGap,
                            style = cardStyle,
                            cardSpacing = cardSpacing
                        )

                        if (showRequests) {
                            Spacer(Modifier.height(requestsGap))
                            AccountRow(
                                title = RequestsTitle,
                                items = requests,
                                keys = requestKeys,
                                rowState = requestsFocus,
                                style = cardStyle,
                                cardSpacing = cardSpacing,
                                horizontalRowSpec = horizontalRowSpec,
                                enterFr = if (favorites.isEmpty()) focus.enterFr else null,
                                leftFocus = focus.leftFocus,
                                upFocus = if (favorites.isNotEmpty()) {
                                    { favoritesFocus.pinFocus }
                                } else {
                                    null
                                },
                                downFocus = { lastActionFocus },
                                onRowFocused = { requester ->
                                    lastBlock = AccountBlock.REQUESTS
                                    focus.onRowFocused(requester)
                                },
                                itemKey = { it.request.id },
                                modifier = Modifier.blockTop(AccountBlock.REQUESTS, blockTops)
                            ) { row, cardFocus, cardModifier ->
                                AccountRequestCard(
                                    row = row,
                                    seerrBaseUrl = seerr.serverUrl,
                                    cacheImages = seerr.cacheImages,
                                    style = cardStyle,
                                    focusRequester = cardFocus.requester,
                                    upFocus = cardFocus.up,
                                    downFocus = cardFocus.down,
                                    leftFocus = cardFocus.left,
                                    rightFocus = cardFocus.right,
                                    onClick = { onOpenSeerrDetail?.invoke(row.request) },
                                    onFocused = cardFocus.onFocused,
                                    modifier = cardModifier
                                )
                            }
                        }
                    }

                    AccountActionsRow(
                        linked = linked,
                        issuesFr = issuesButtonFr,
                        issuesLabel = when {
                            !canViewIssues -> null
                            openIssueCount == null -> "Issues"
                            else -> "Issues · $openIssueCount open"
                        },
                        onIssues = onOpenIssues,
                        seerrButtonFr = seerrButtonFr,
                        enterFr = if (favorites.isEmpty() && !showRequests) focus.enterFr else null,
                        leftFocus = focus.leftFocus,
                        upFocus = when {
                            showRequests -> {
                                { requestsFocus.pinFocus }
                            }
                            favorites.isNotEmpty() -> {
                                { favoritesFocus.pinFocus }
                            }
                            else -> null
                        },
                        onFocused = { requester ->
                            lastBlock = AccountBlock.ACTIONS
                            lastActionFocus = requester
                            focus.onRowFocused(requester)
                        },
                        onSeerr = { if (linked) viewModel.disconnectSeerr() else showConnectDialog = true },
                        onSignOut = { viewModel.signOut(onSignedOut) },
                        modifier = Modifier
                            .blockTop(AccountBlock.ACTIONS, blockTops)
                            .padding(top = RowGap)
                    )
                }
            }
        }
    }

    if (showConnectDialog) {
        SeerrConnectDialog(
            initialUrl = seerr.serverUrl.orEmpty(),
            connecting = connecting,
            error = connectError,
            onConnect = viewModel::connectSeerr,
            onDismiss = { showConnectDialog = false }
        )
    }
}

private val CollapseSkeleton = fadeOut() + shrinkVertically()

@Composable
private fun AccountRowSkeleton(
    title: String,
    visible: Boolean,
    exit: ExitTransition,
    topGap: Dp,
    style: BrowseCardStyle,
    cardSpacing: Dp
) {
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = exit) {
        val pulse = rememberInfiniteTransition(label = "skeleton")
        val alpha = pulse.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeletonAlpha"
        )
        Column {
            Spacer(Modifier.height(topGap))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            BoxWithConstraints {
                val slot = style.width + cardSpacing
                val cards = if (slot > 0.dp) (maxWidth / slot).toInt() + 1 else 1
                Row(
                    horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                    modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
                ) {
                    repeat(cards) {
                        Column(
                            Modifier.gridCellSlot(style).padding(top = style.topInset),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SkeletonBar(width = style.width, height = style.height, corner = 6.dp)
                            SkeletonBar(width = style.width * 0.8f, height = 10.dp, corner = 3.dp)
                            SkeletonBar(width = style.width * 0.45f, height = 10.dp, corner = 3.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp, corner: Dp) {
    Box(
        Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(corner))
            .background(SkeletonFill)
    )
}

@Composable
private fun <T> AccountRow(
    title: String,
    items: List<T>,
    keys: List<String>,
    rowState: RowFocusState,
    style: BrowseCardStyle,
    cardSpacing: Dp,
    horizontalRowSpec: BringIntoViewSpec,
    enterFr: FocusRequester?,
    leftFocus: FocusRequester,
    upFocus: (() -> FocusRequester)?,
    downFocus: () -> FocusRequester,
    onRowFocused: (FocusRequester) -> Unit,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    card: @Composable (item: T, focus: AccountCardFocus, modifier: Modifier) -> Unit
) {
    val focusIndex = rowState.indexIn(keys)
    DetailMediaRow(
        title = title,
        items = items,
        endInset = SettingsSideInset,
        cardSpacing = cardSpacing,
        horizontalRowSpec = horizontalRowSpec,
        rowFocus = rowState.pinnedRowModifier().outsetRow(start = RowPeekInset),
        modifier = modifier,
        startInset = RowPeekInset,
        headingInset = 0.dp,
        titleStyle = MaterialTheme.typography.titleSmall,
        titleFontWeight = FontWeight.SemiBold,
        key = { _, item -> itemKey(item) }
    ) { index, item ->
        Box(Modifier.gridCellSlot(style), contentAlignment = Alignment.TopCenter) {
            card(
                item,
                AccountCardFocus(
                    requester = if (index == focusIndex) rowState.pinFocus else null,
                    up = upFocus ?: { FocusRequester.Cancel },
                    down = downFocus,
                    left = if (index == 0) leftFocus else null,
                    right = if (index == items.lastIndex) FocusRequester.Cancel else null,
                    onFocused = {
                        rowState.onItemFocused(index, keys[index])
                        onRowFocused(rowState.pinFocus)
                    }
                ),
                Modifier
                    .padding(top = style.topInset)
                    .then(if (index == 0 && enterFr != null) Modifier.focusRequester(enterFr) else Modifier)
            )
        }
    }
}

@Composable
private fun AccountActionsRow(
    linked: Boolean,
    issuesFr: FocusRequester,
    issuesLabel: String?,
    onIssues: () -> Unit,
    seerrButtonFr: FocusRequester,
    enterFr: FocusRequester?,
    leftFocus: FocusRequester,
    upFocus: (() -> FocusRequester)?,
    onFocused: (FocusRequester) -> Unit,
    onSeerr: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val signOutFr = remember { FocusRequester() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        ActionButton(
            label = if (linked) "Disconnect from Seerr" else "Connect to Seerr",
            onActivate = onSeerr,
            focusRequester = seerrButtonFr,
            modifier = Modifier
                .then(if (enterFr != null) Modifier.focusRequester(enterFr) else Modifier)
                .focusProperties {
                    left = leftFocus
                    down = FocusRequester.Cancel
                    up = upFocus?.invoke() ?: FocusRequester.Cancel
                }
                .onFocusChanged { if (it.isFocused) onFocused(seerrButtonFr) }
        )
        if (issuesLabel != null) {
            ActionButton(
                label = issuesLabel,
                onActivate = onIssues,
                focusRequester = issuesFr,
                modifier = Modifier
                    .focusProperties {
                        left = seerrButtonFr
                        down = FocusRequester.Cancel
                        up = upFocus?.invoke() ?: FocusRequester.Cancel
                    }
                    .onFocusChanged { if (it.isFocused) onFocused(issuesFr) }
            )
        }
        ActionButton(
            label = "Sign out of Jellyfin",
            onActivate = onSignOut,
            focusRequester = signOutFr,
            modifier = Modifier
                .focusProperties {
                    left = if (issuesLabel != null) issuesFr else seerrButtonFr
                    right = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    up = upFocus?.invoke() ?: FocusRequester.Cancel
                }
                .onFocusChanged { if (it.isFocused) onFocused(signOutFr) }
        )
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
