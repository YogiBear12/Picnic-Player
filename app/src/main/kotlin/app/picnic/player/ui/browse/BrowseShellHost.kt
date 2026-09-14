@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package app.picnic.player.ui.browse

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.DrawerState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.LocalCapBadgeCount
import app.picnic.player.ui.ambient.LocalColouredFocus
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.RefreshOnResume
import app.picnic.player.ui.discover.DiscoverPane
import app.picnic.player.ui.discover.DiscoverViewModel
import app.picnic.player.ui.home.HomeBrowsePane
import app.picnic.player.ui.home.HomeViewModel
import app.picnic.player.ui.library.ForYouViewModel
import app.picnic.player.ui.library.LibraryPane
import app.picnic.player.ui.library.LibraryPaneViewModel
import app.picnic.player.ui.library.LibraryTab
import app.picnic.player.ui.library.forYouVmKey
import app.picnic.player.ui.library.libraryPaneVmKey
import app.picnic.player.ui.playlist.PlaylistsPane
import app.picnic.player.ui.search.SearchPane
import app.picnic.player.ui.search.SearchViewModel
import app.picnic.player.ui.theme.PicnicColors
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun BrowseShellHost(
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onSeerrItem: (SeerrCatalogItem, String?, String?) -> Unit,
    onGenre: (BaseItemDto) -> Unit,
    onLibraryGenre: (BaseItemDto, BrowseDest.Library) -> Unit,
    onCollection: (BaseItemDto) -> Unit,
    onPlaylist: (BaseItemDto) -> Unit,
    onPerson: (BaseItemDto) -> Unit,
    onSessionExpired: (String) -> Unit,
    onServerUnreachable: (String, String) -> Unit,
    onSettings: () -> Unit,
    onSwapUser: () -> Unit,
    drawerState: DrawerState,
    drawerDim: State<Float>,
    homeViewModel: HomeViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    discoverViewModel: DiscoverViewModel = hiltViewModel(),
    railViewModel: NavRailViewModel = hiltViewModel()
) {
    val rail = railViewModel.rail
    val selectedKey by rail.selectedKey.collectAsStateWithLifecycle()
    val navChromeFocused by rail.chromeFocused.collectAsStateWithLifecycle()
    var paneFocusRequest by remember { mutableStateOf(PaneFocusRequest.WhenIdle) }
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val discoverState by discoverViewModel.state.collectAsStateWithLifecycle()
    val colouredFocus by homeViewModel.colouredFocus.collectAsStateWithLifecycle()
    val capBadgeCount by homeViewModel.capBadgeCount.collectAsStateWithLifecycle()
    val alternateNavigation by homeViewModel.alternateNavigation.collectAsStateWithLifecycle()

    val session = homeState.session

    val destinations by rail.drawerDestinations.collectAsStateWithLifecycle()
    val drawerPage by rail.drawerPage.collectAsStateWithLifecycle()
    val moreVisible by rail.moreVisible.collectAsStateWithLifecycle()
    val layout by rail.layout.collectAsStateWithLifecycle()
    val reorderKey by rail.reorderKey.collectAsStateWithLifecycle()
    val selected = rail.destinationFor(selectedKey) ?: BrowseDest.Home

    val pendingCommit by rail.pendingCommit.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCommit) {
        if (pendingCommit && rail.consumeCommit()) {
            paneFocusRequest = PaneFocusRequest.Commit
        }
    }

    RefreshOnResume { homeViewModel.refresh() }

    var lastSelectedKey by remember { mutableStateOf(selectedKey) }
    LaunchedEffect(selectedKey) {
        if (lastSelectedKey == BrowseDest.Search.key && selectedKey != BrowseDest.Search.key) {
            searchViewModel.clearSearch()
        }
        lastSelectedKey = selectedKey
    }

    LaunchedEffect(homeState.sessionExpiredServerId) {
        homeState.sessionExpiredServerId?.let {
            homeViewModel.consumeSessionExpired()
            onSessionExpired(it)
        }
    }

    LaunchedEffect(homeState.serverUnreachable) {
        homeState.serverUnreachable?.let { (serverId, message) ->
            homeViewModel.consumeServerUnreachable()
            onServerUnreachable(serverId, message)
        }
    }

    val activity = LocalActivity.current

    BackHandler {
        when {
            !navChromeFocused ->
                runCatching { rail.requesterFor(selectedKey).requestFocus() }
            reorderKey != null ->
                rail.exitReorder()
            drawerPage == NavDrawerPage.More ->
                rail.openPrimaryPage()
            selectedKey != BrowseDest.Home.key ->
                rail.select(BrowseDest.Home)
            else ->
                activity?.finish()
        }
    }

    if (session == null && homeState.loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = PicnicColors.Accent)
        }
        return
    }

    if (session == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(homeState.error ?: "No session")
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalColouredFocus provides colouredFocus,
            LocalCapBadgeCount provides capBadgeCount
        ) {
            val metrics = browseLayoutMetrics(maxWidth, maxHeight)
            val onHome = selectedKey == BrowseDest.Home.key
            val onDiscover = selectedKey == BrowseDest.Discover.key
            val homeFocus = rememberHomeBrowseFocus(
                rowCount = homeState.rows.size,
                focusedRowIndex = homeState.focusedRowIndex,
                scrollEnabled = onHome
            )
            val discoverFocus = rememberHomeBrowseFocus(
                rowCount = discoverState.rows.size,
                focusedRowIndex = discoverState.focusedRowIndex,
                scrollEnabled = onDiscover
            )

            val contentFocusOnRight: () -> FocusRequester = {
                when (selectedKey) {
                    BrowseDest.Home.key ->
                        homeFocus.rowCardFocus.getOrNull(homeState.focusedRowIndex)
                            ?: homeFocus.rowFocusRequesters.getOrNull(homeState.focusedRowIndex)
                            ?: FocusRequester.Default
                    BrowseDest.Discover.key ->
                        discoverFocus.rowCardFocus.getOrNull(discoverState.focusedRowIndex)
                            ?: discoverFocus.rowFocusRequesters.getOrNull(discoverState.focusedRowIndex)
                            ?: FocusRequester.Default
                    else -> FocusRequester.Default
                }
            }

            val images = LocalImageUrls.current
            val homeFocused = homeViewModel.focusedItem(homeState)
            val discoverFocused = discoverViewModel.focusedItem(discoverState)
            val seerr = discoverState.seerr
            val selectedLibrary = selected as? BrowseDest.Library
            val forYouFocused: BaseItemDto? = if (selectedLibrary != null) {
                val paneViewModel: LibraryPaneViewModel =
                    hiltViewModel(key = libraryPaneVmKey(selectedLibrary.key))
                val forYouViewModel: ForYouViewModel =
                    hiltViewModel(key = forYouVmKey(selectedLibrary.key))
                val paneState by paneViewModel.state.collectAsStateWithLifecycle()
                val forYouState by forYouViewModel.state.collectAsStateWithLifecycle()
                if (paneState.selectedTab == LibraryTab.FOR_YOU) {
                    forYouViewModel.focusedItem(forYouState)
                } else {
                    null
                }
            } else {
                null
            }
            PublishBackdrop(
                when {
                    onHome ->
                        homeFocused
                            ?.let { images.navImages(it) }
                            .let { BackdropSpec(backdropUrl = it?.bgUrl, ambientUrl = it?.ambUrl) }
                    onDiscover ->
                        discoverFocused
                            ?.let { SeerrImages.navImages(seerr.serverUrl, it, seerr.cacheImages) }
                            .let { BackdropSpec(backdropUrl = it?.bgUrl, ambientUrl = it?.ambUrl) }
                    forYouFocused != null ->
                        images.navImages(forYouFocused)
                            .let { BackdropSpec(backdropUrl = it.bgUrl, ambientUrl = it.ambUrl) }
                    else -> null
                }
            )

            val onPaneSeeded = { paneFocusRequest = PaneFocusRequest.None }
            val paneInset = BrowsePaneStartInset
            val railRequesters = remember(destinations, drawerPage) {
                destinations.associate { it.key to rail.requesterFor(it.key) }
            }
            val updateViewModel: app.picnic.player.ui.settings.UpdateViewModel = hiltViewModel()
            val updateBadge by updateViewModel.updateAvailable.collectAsStateWithLifecycle()
            val avatarViewModel: app.picnic.player.ui.common.UserAvatarViewModel = hiltViewModel()
            val avatarUrl by avatarViewModel.url.collectAsStateWithLifecycle()
            NavShell(
                alternate = alternateNavigation,
                session = session,
                avatarUrl = avatarUrl,
                destinations = destinations,
                selectedKey = selectedKey,
                selectedDest = selected,
                itemFocusRequesters = railRequesters,
                contentFocusOnRight = contentFocusOnRight,
                drawerState = drawerState,
                drawerDim = drawerDim,
                drawerPage = drawerPage,
                moreVisible = moreVisible,
                layout = layout,
                reorderKey = reorderKey,
                onSelect = { dest -> rail.select(dest) },
                onOpenMore = { rail.openMorePage() },
                onBackFromMore = { rail.openPrimaryPage() },
                onSwapUser = onSwapUser,
                onSettings = onSettings,
                onChromeFocusedChange = rail::setChromeFocused,
                onPin = railViewModel::pin,
                onUnpin = railViewModel::unpin,
                onEnterReorder = { dest ->
                    rail.enterReorder(dest.key)
                    runCatching { rail.requesterFor(dest.key).requestFocus() }
                },
                onExitReorder = rail::exitReorder,
                onMoveReorder = railViewModel::moveReorder,
                settingsBadge = updateBadge
            ) {
                BrowseShellScaffold {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(260)) },
                        label = "browseTabContent"
                    ) { dest ->
                        val seedPaneFocus = dest.key == selectedKey &&
                            when (paneFocusRequest) {
                                PaneFocusRequest.Commit -> true
                                PaneFocusRequest.WhenIdle -> !navChromeFocused
                                PaneFocusRequest.None -> false
                            }
                        when (dest) {
                            BrowseDest.Search -> SearchPane(
                                state = searchState,
                                viewModel = searchViewModel,
                                metrics = metrics,
                                horizontalInset = paneInset,
                                seedContentFocus = seedPaneFocus,
                                onContentFocusSeeded = onPaneSeeded,
                                onItem = onItem,
                                onSeerrItem = onSeerrItem,
                                onGenre = onGenre,
                                onCollection = onCollection,
                                onPerson = onPerson
                            )
                            BrowseDest.Home -> HomeBrowsePane(
                                state = homeState,
                                viewModel = homeViewModel,
                                metrics = metrics,
                                horizontalInset = paneInset,
                                focus = homeFocus,
                                seedContentFocus = seedPaneFocus,
                                onContentFocusSeeded = onPaneSeeded,
                                onItem = onItem
                            )
                            BrowseDest.Discover -> DiscoverPane(
                                state = discoverState,
                                viewModel = discoverViewModel,
                                metrics = metrics,
                                horizontalInset = paneInset,
                                focus = discoverFocus,
                                seedContentFocus = seedPaneFocus,
                                onContentFocusSeeded = onPaneSeeded,
                                onSeerrItem = onSeerrItem
                            )
                            BrowseDest.Playlists -> PlaylistsPane(
                                metrics = metrics,
                                horizontalInset = paneInset,
                                seedContentFocus = seedPaneFocus,
                                onContentFocusSeeded = onPaneSeeded,
                                onPlaylist = onPlaylist
                            )
                            is BrowseDest.Library -> LibraryPane(
                                dest = dest,
                                metrics = metrics,
                                horizontalInset = paneInset,
                                seedContentFocus = seedPaneFocus,
                                onContentFocusSeeded = onPaneSeeded,
                                onItem = onItem,
                                onGenre = { genre -> onLibraryGenre(genre, dest) },
                                onCollection = onCollection,
                                onSessionExpired = onSessionExpired
                            )
                        }
                    }
                }
            }
        }
    }
}
