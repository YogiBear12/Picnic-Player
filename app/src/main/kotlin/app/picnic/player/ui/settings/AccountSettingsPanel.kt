@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.media.WatchStats
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.SeerrRequestDisplay
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.DialogTextField
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellSlot
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlin.math.roundToInt
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
private const val ROW_ACTIONS = 2

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
    onSignedOut: () -> Unit,
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
    val connecting by viewModel.seerrConnecting.collectAsStateWithLifecycle()
    val connectError by viewModel.seerrConnectError.collectAsStateWithLifecycle()
    val linked = seerr.linkState == SeerrLinkState.Linked
    var showConnectDialog by remember { mutableStateOf(false) }
    val seerrButtonFr = remember { FocusRequester() }
    var lastActionFocus by remember { mutableStateOf(seerrButtonFr) }

    val favoritesFocus = rememberAccountRowFocus()
    val requestsFocus = rememberAccountRowFocus()
    val favoriteKeys = remember(favorites) { favorites.map { it.id.toString() } }
    val requestKeys = remember(requests) { requests.map { it.request.id.toString() } }
    var lastRow by rememberSaveable { mutableIntStateOf(ROW_FAVORITES) }
    val blockTops = remember { mutableStateMapOf<Int, Float>() }

    val configuration = LocalConfiguration.current
    val cardStyle = remember(configuration.screenHeightDp) {
        val height = (configuration.screenHeightDp * (118f / 540f)).dp
        BrowseCardStyle(width = height * (2f / 3f), height = height, landscape = false)
    }
    val cardSpacing = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * (14f / 960f)).dp
    }

    LaunchedEffect(Unit) { viewModel.refreshAccount() }

    LaunchedEffect(linked) {
        if (linked) viewModel.refreshMyRequests()
        if (linked && showConnectDialog) {
            showConnectDialog = false
            seerrButtonFr.requestFocusWhenAttached(maxFrames = 20)
        }
    }

    val showRequests = linked && requests.isNotEmpty()

    val focusedItemVanished = when (lastRow) {
        ROW_FAVORITES -> favoritesFocus.focusedKey?.let { it !in favoriteKeys } == true
        ROW_REQUESTS -> requestsFocus.focusedKey?.let { it !in requestKeys } == true
        else -> false
    }

    focus.onHoldSelection(focusedItemVanished)

    LaunchedEffect(favoriteKeys, requestKeys) {
        try {
            val favoritesLost = lastRow == ROW_FAVORITES &&
                favoritesFocus.focusedKey != null &&
                favoriteKeys.isEmpty()
            val requestsLost = lastRow == ROW_REQUESTS &&
                requestsFocus.focusedKey != null &&
                (!showRequests || requestKeys.isEmpty())
            when {
                favoritesLost && showRequests -> {
                    lastRow = ROW_REQUESTS
                    requestsFocus.restore(requestKeys)
                }
                favoritesLost || requestsLost -> {
                    lastRow = ROW_ACTIONS
                    seerrButtonFr.requestFocusWhenAttached(maxFrames = 20)
                }
                lastRow == ROW_REQUESTS && showRequests -> requestsFocus.restore(requestKeys)
                lastRow == ROW_FAVORITES && favoritesFocus.focusedKey != null ->
                    favoritesFocus.restore(favoriteKeys)
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
    val scrollTarget = ((blockTops[lastRow] ?: firstBlockTop) - firstBlockTop)
        .roundToInt()
        .coerceIn(0, scrollState.maxValue)

    LaunchedEffect(scrollTarget) {
        if (scrollState.value != scrollTarget) scrollState.animateScrollTo(scrollTarget)
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
                    keys = favoriteKeys,
                    style = cardStyle,
                    cardSpacing = cardSpacing,
                    enterFr = focus.enterFr,
                    leftFocus = focus.leftFocus,
                    downFocus = if (showRequests) {
                        { requestsFocus.cardFocus }
                    } else {
                        { lastActionFocus }
                    },
                    onRowFocused = { requester ->
                        lastRow = ROW_FAVORITES
                        focus.onRowFocused(requester)
                    },
                    onOpenItem = onOpenItem,
                    horizontalRowSpec = horizontalRowSpec,
                    modifier = Modifier.blockTop(ROW_FAVORITES, blockTops)
                )
            }

            if (showRequests) {
                RequestsRow(
                    requests = requests,
                    rowFocus = requestsFocus,
                    keys = requestKeys,
                    style = cardStyle,
                    cardSpacing = cardSpacing,
                    enterFr = if (favorites.isEmpty()) focus.enterFr else null,
                    leftFocus = focus.leftFocus,
                    upFocus = if (favorites.isNotEmpty()) {
                        { favoritesFocus.cardFocus }
                    } else {
                        null
                    },
                    downFocus = { lastActionFocus },
                    seerrBaseUrl = seerr.serverUrl,
                    cacheImages = seerr.cacheImages,
                    onRowFocused = { requester ->
                        lastRow = ROW_REQUESTS
                        focus.onRowFocused(requester)
                    },
                    onOpenSeerrDetail = onOpenSeerrDetail,
                    horizontalRowSpec = horizontalRowSpec,
                    modifier = Modifier.blockTop(ROW_REQUESTS, blockTops)
                )
            }

            AccountActionsRow(
                linked = linked,
                seerrButtonFr = seerrButtonFr,
                enterFr = if (favorites.isEmpty() && !showRequests) focus.enterFr else null,
                leftFocus = focus.leftFocus,
                upFocus = when {
                    showRequests -> {
                        { requestsFocus.cardFocus }
                    }
                    favorites.isNotEmpty() -> {
                        { favoritesFocus.cardFocus }
                    }
                    else -> null
                },
                onFocused = { requester ->
                    lastRow = ROW_ACTIONS
                    lastActionFocus = requester
                    focus.onRowFocused(requester)
                },
                onSeerr = { if (linked) viewModel.disconnectSeerr() else showConnectDialog = true },
                onSignOut = { viewModel.signOut(onSignedOut) },
                modifier = Modifier.blockTop(ROW_ACTIONS, blockTops)
            )
            Spacer(Modifier.height(PanelBottomInset))
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

private fun Modifier.blockTop(id: Int, tops: MutableMap<Int, Float>) = onGloballyPositioned { tops[id] = it.positionInParent().y }

@Composable
private fun AccountActionsRow(
    linked: Boolean,
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
        ActionButton(
            label = "Sign out of Jellyfin",
            onActivate = onSignOut,
            focusRequester = signOutFr,
            modifier = Modifier
                .focusProperties {
                    left = seerrButtonFr
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
    rowFocus: AccountRowFocus,
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
    rowFocus: AccountRowFocus,
    keys: List<String>,
    style: BrowseCardStyle,
    cardSpacing: Dp,
    enterFr: FocusRequester,
    leftFocus: FocusRequester,
    downFocus: () -> FocusRequester,
    onRowFocused: (FocusRequester) -> Unit,
    onOpenItem: (BaseItemDto, String?, String?) -> Unit,
    horizontalRowSpec: BringIntoViewSpec,
    modifier: Modifier = Modifier
) {
    val images = LocalImageUrls.current
    val contextMenu = LocalContextMenuHandler.current
    val focusIndex = rowFocus.focusIndex(keys)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        AccountRowHeading("Your favorites")
        AccountCardRow(rowFocus, cardSpacing, horizontalRowSpec) {
            itemsIndexed(favorites, key = { _, item -> item.id }) { index, item ->
                Box(Modifier.gridCellSlot(style), contentAlignment = Alignment.TopCenter) {
                    MediaGridCard(
                        item = item,
                        style = style,
                        subtitleOverride = favoriteSubtitle(item),
                        showStatus = false,
                        focusRequester = if (index == focusIndex) rowFocus.cardFocus else null,
                        upFocus = { FocusRequester.Cancel },
                        downFocus = downFocus,
                        leftFocus = if (index == 0) leftFocus else null,
                        rightFocus = if (index == favorites.lastIndex) FocusRequester.Cancel else null,
                        onClick = {
                            val nav = images.navImages(item)
                            onOpenItem(item, nav.bgUrl, nav.ambUrl)
                        },
                        onLongClick = { contextMenu.show(item) },
                        onFocused = {
                            rowFocus.onItemFocused(index, keys[index])
                            onRowFocused(rowFocus.cardFocus)
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
    rowFocus: AccountRowFocus,
    keys: List<String>,
    style: BrowseCardStyle,
    cardSpacing: Dp,
    enterFr: FocusRequester?,
    leftFocus: FocusRequester,
    upFocus: (() -> FocusRequester)?,
    downFocus: () -> FocusRequester,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    onRowFocused: (FocusRequester) -> Unit,
    onOpenSeerrDetail: ((SeerrMediaRequest) -> Unit)?,
    horizontalRowSpec: BringIntoViewSpec,
    modifier: Modifier = Modifier
) {
    val focusIndex = rowFocus.focusIndex(keys)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        AccountRowHeading("Your requests")
        AccountCardRow(rowFocus, cardSpacing, horizontalRowSpec) {
            itemsIndexed(requests, key = { _, row -> row.request.id }) { index, row ->
                Box(Modifier.gridCellSlot(style), contentAlignment = Alignment.TopCenter) {
                    AccountRequestCard(
                        row = row,
                        seerrBaseUrl = seerrBaseUrl,
                        cacheImages = cacheImages,
                        style = style,
                        focusRequester = if (index == focusIndex) rowFocus.cardFocus else null,
                        upFocus = upFocus ?: { FocusRequester.Cancel },
                        downFocus = downFocus,
                        leftFocus = if (index == 0) leftFocus else null,
                        rightFocus = if (index == requests.lastIndex) FocusRequester.Cancel else null,
                        onClick = { onOpenSeerrDetail?.invoke(row.request) },
                        onFocused = {
                            rowFocus.onItemFocused(index, keys[index])
                            onRowFocused(rowFocus.cardFocus)
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

private val DialogGlassFill = Color(0xEA181E24)

@Composable
private fun SeerrConnectDialog(
    initialUrl: String,
    connecting: Boolean,
    error: String?,
    onConnect: (url: String, password: String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var password by remember { mutableStateOf("") }
    val urlFieldFr = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .width(440.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(DialogGlassFill)
                    .padding(28.dp)
            ) {
                Text(
                    "Connect to Seerr",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                DialogTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://requests.example.com",
                    label = "Seerr URL",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.focusRequester(urlFieldFr)
                )
                DialogTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    label = "Jellyfin password",
                    password = true
                )
                if (error != null) {
                    Text(error, color = PicnicColors.Error, style = MaterialTheme.typography.bodyMedium)
                }
                ActionButton(
                    label = "Connect",
                    onActivate = { onConnect(url, password) },
                    enabled = url.isNotBlank() && password.isNotBlank(),
                    busy = connecting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        urlFieldFr.requestFocusWhenAttached(maxFrames = 20)
    }
}
