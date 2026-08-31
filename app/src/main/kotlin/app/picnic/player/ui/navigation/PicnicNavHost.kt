package app.picnic.player.ui.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.picnic.player.data.media.ItemQueue
import app.picnic.player.data.media.QueueKind
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.ui.ambient.BackdropHostLayer
import app.picnic.player.ui.ambient.LocalBackdropController
import app.picnic.player.ui.browse.NavRailViewModel
import app.picnic.player.ui.collection.CollectionScreen
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuHandler
import app.picnic.player.ui.common.GlobalContextMenuDialog
import app.picnic.player.ui.common.GlobalContextMenuViewModel
import app.picnic.player.ui.common.ImageUrlsViewModel
import app.picnic.player.ui.common.LocalAddToPlaylist
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.LocalSeerrCardMenu
import app.picnic.player.ui.detail.DetailScreen
import app.picnic.player.ui.detail.SeriesEpisodesScreen
import app.picnic.player.ui.genre.GenreScreen
import app.picnic.player.ui.home.HomeScreen
import app.picnic.player.ui.onboarding.LoginScreen
import app.picnic.player.ui.onboarding.ProfilePickerScreen
import app.picnic.player.ui.onboarding.ServerEntryScreen
import app.picnic.player.ui.onboarding.ServerPickerScreen
import app.picnic.player.ui.person.PersonScreen
import app.picnic.player.ui.player.PlayerScreen
import app.picnic.player.ui.playlist.AddToPlaylistDialog
import app.picnic.player.ui.playlist.PlaylistScreen
import app.picnic.player.ui.seerr.SeerrCardContextMenu
import app.picnic.player.ui.settings.SettingsScreen
import app.picnic.player.ui.startup.StartupScreen
import app.picnic.player.ui.theme.TvBrowseMotion
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private const val EnterDurationMs = 400
private const val ExitDurationMs = 300
private val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val ExitEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PicnicNavHost(
    railViewModel: NavRailViewModel = hiltViewModel(),
    navViewModel: AppNavigationViewModel = hiltViewModel(),
    imageUrlsViewModel: ImageUrlsViewModel = hiltViewModel()
) {
    val backStack = navViewModel.backStack
    val backdropController = LocalBackdropController.current
    val rail = railViewModel.rail

    val drawerState = rememberSaveable(saver = DrawerState.Saver) { DrawerState(DrawerValue.Closed) }
    val drawerDim = animateFloatAsState(
        targetValue = if (drawerState.currentValue == DrawerValue.Open) {
            TvBrowseMotion.DIM_ALPHA
        } else {
            1f
        },
        animationSpec = tween(TvBrowseMotion.DIM_FADE_MS),
        label = "drawerDim"
    )

    fun resetShellTo(key: NavKey) {
        backdropController.clear()
        rail.clear()
        navViewModel.resetTo(key)
    }

    fun goProfilePicker(serverId: String) = resetShellTo(ProfilePickerKey(serverId))

    fun goServerPicker(serverId: String, errorText: String) = resetShellTo(ServerPickerKey(serverId, errorText))

    fun goStartup() = resetShellTo(StartupKey)

    val railSession by rail.session.collectAsStateWithLifecycle()

    val onSwapUser: () -> Unit = {
        railViewModel.softLogout()
        railSession?.server?.id?.let { goProfilePicker(it) }
    }

    var contextMenuItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var contextMenuFromContinueWatching by remember { mutableStateOf(false) }
    var seerrMenuItem by remember { mutableStateOf<SeerrCatalogItem?>(null) }
    var addToPlaylistItem by remember { mutableStateOf<BaseItemDto?>(null) }
    val contextMenuViewModel: GlobalContextMenuViewModel = hiltViewModel()

    val contextMenuHandler = remember {
        object : ContextMenuHandler {
            override fun show(item: BaseItemDto, fromContinueWatching: Boolean) {
                contextMenuItem = item
                contextMenuFromContinueWatching = fromContinueWatching
            }
        }
    }

    val seerrCardMenu: (SeerrCatalogItem) -> Unit = { item -> seerrMenuItem = item }

    val addToPlaylistHandler: (BaseItemDto) -> Unit = { item -> addToPlaylistItem = item }

    val imageUrls by imageUrlsViewModel.imageUrls.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalContextMenuHandler provides contextMenuHandler,
        LocalSeerrCardMenu provides seerrCardMenu,
        LocalAddToPlaylist provides addToPlaylistHandler,
        LocalImageUrls provides imageUrls
    ) {
        Box(Modifier.fillMaxSize()) {
            BackdropHostLayer(drawerDim, Modifier.fillMaxSize())

            NavDisplay(
                backStack = backStack,
                onBack = { navViewModel.pop() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                transitionSpec = {
                    val isPop = initialState.key !in navViewModel.backStack

                    val enterTween = tween<Float>(EnterDurationMs, easing = EnterEasing)
                    val exitTween = tween<Float>(ExitDurationMs, easing = ExitEasing)

                    if (targetState.key is PlayerKey) {
                        fadeIn(enterTween) togetherWith ExitTransition.None
                    } else if (isPop) {
                        (fadeIn(enterTween) togetherWith fadeOut(exitTween)).apply {
                            targetContentZIndex = -1f
                        }
                    } else {
                        fadeIn(enterTween) togetherWith fadeOut(exitTween)
                    }
                },
                popTransitionSpec = {
                    (fadeIn(tween(EnterDurationMs, easing = EnterEasing)) togetherWith fadeOut(tween(ExitDurationMs, easing = ExitEasing))).apply {
                        targetContentZIndex = -1f
                    }
                },
                predictivePopTransitionSpec = {
                    (fadeIn(tween(EnterDurationMs, easing = EnterEasing)) togetherWith fadeOut(tween(ExitDurationMs, easing = ExitEasing))).apply {
                        targetContentZIndex = -1f
                    }
                },
                entryProvider = entryProvider {
                    entry<StartupKey> {
                        StartupScreen(onResolved = { dest ->
                            navViewModel.resetTo(dest)
                            if (dest == BrowseKey) {
                                navViewModel.consumePendingDeepLink()?.let { key ->
                                    navViewModel.push(key)
                                }
                            }
                        })
                    }
                    entry<ServerEntryKey> {
                        ServerEntryScreen(
                            onServerResolved = {
                                if (navViewModel.backStack.lastOrNull() != LoginKey) {
                                    navViewModel.push(LoginKey)
                                }
                            }
                        )
                    }
                    entry<LoginKey> {
                        LoginScreen(
                            onLoggedIn = {
                                navViewModel.resetTo(BrowseKey)
                                navViewModel.consumePendingDeepLink()?.let { key ->
                                    navViewModel.push(key)
                                }
                            },
                            onBack = { navViewModel.pop() }
                        )
                    }
                    entry<ServerPickerKey> { key ->
                        ServerPickerScreen(
                            onServerSelected = { serverId ->
                                navViewModel.push(ProfilePickerKey(serverId))
                            },
                            onAddServer = { navViewModel.push(ServerEntryKey) },
                            onNoServersLeft = {
                                navViewModel.replaceTop(ServerEntryKey)
                            },
                            unreachableServerId = key.unreachableServerId,
                            errorText = key.errorText
                        )
                    }
                    entry<ProfilePickerKey> { key ->
                        ProfilePickerScreen(
                            serverId = key.serverId,
                            onProfileReady = {
                                navViewModel.resetTo(BrowseKey)
                                navViewModel.consumePendingDeepLink()?.let { key ->
                                    navViewModel.push(key)
                                }
                            },
                            onNeedsLogin = { navViewModel.push(LoginKey) },
                            onChangeServer = { navViewModel.push(ServerPickerKey()) }
                        )
                    }
                    entry<BrowseKey> {
                        HomeScreen(
                            onItem = navViewModel::openItem,
                            onSeerrItem = { item, bg, amb ->
                                navViewModel.push(resolveSeerrNavKey(item, bg, amb))
                            },
                            onGenre = { genre ->
                                navViewModel.push(GenreKey(genre.id.toString(), genre.name))
                            },
                            onLibraryGenre = { genre, library ->
                                navViewModel.push(
                                    GenreKey(
                                        genreId = genre.id.toString(),
                                        name = genre.name,
                                        libraryId = library.id.toString(),
                                        libraryName = library.title
                                    )
                                )
                            },
                            onCollection = { collection ->
                                navViewModel.push(
                                    CollectionKey(collection.id.toString())
                                )
                            },
                            onPlaylist = { playlist ->
                                navViewModel.push(
                                    PlaylistKey(playlist.id.toString(), playlist.name)
                                )
                            },
                            onPerson = { person ->
                                navViewModel.push(PersonKey(jellyfinPersonId = person.id.toString()))
                            },
                            onSessionExpired = { serverId -> goProfilePicker(serverId) },
                            onServerUnreachable = { serverId, msg -> goServerPicker(serverId, msg) },
                            onSettings = { navViewModel.push(SettingsKey) },
                            onSwapUser = onSwapUser,
                            drawerState = drawerState,
                            drawerDim = drawerDim
                        )
                    }
                    entry<GenreKey> { key ->
                        GenreScreen(
                            genreId = key.genreId,
                            genreName = key.name,
                            onItem = navViewModel::openItem,
                            onBack = { navViewModel.pop() },
                            onSessionExpired = { serverId -> goProfilePicker(serverId) },
                            libraryId = key.libraryId,
                            libraryName = key.libraryName
                        )
                    }
                    entry<SettingsKey> {
                        SettingsScreen(
                            onBack = { navViewModel.pop() },
                            onSignedOut = { goStartup() },
                            onOpenSeerrDetail = { request ->
                                resolveSeerrNavKey(request)?.let { navViewModel.push(it) }
                            },
                            onOpenItem = navViewModel::openItem
                        )
                    }
                    entry<DetailKey> { key ->
                        DetailScreen(
                            itemId = key.itemId,
                            bgUrl = key.bgUrl,
                            ambUrl = key.ambUrl,
                            onPlay = { id, ticks, sourceId -> navViewModel.push(PlayerKey(id, ticks, sourceId)) },
                            onItem = navViewModel::openItem,
                            onEpisodes = { id, ambUrl, seasonId, focusId -> navViewModel.push(EpisodesKey(id, ambUrl, focusId, seasonId)) },
                            onCollection = { collection ->
                                navViewModel.push(
                                    CollectionKey(collection.id.toString())
                                )
                            },
                            onPersonClick = { key -> navViewModel.push(key) },
                            onBack = { navViewModel.pop() }
                        )
                    }
                    entry<SeerrDetailKey> { key ->
                        val mediaType = when (key.mediaType.lowercase()) {
                            "tv" -> app.picnic.player.data.seerr.SeerrMediaType.TV
                            else -> app.picnic.player.data.seerr.SeerrMediaType.MOVIE
                        }
                        app.picnic.player.ui.seerr.SeerrDetailScreen(
                            tmdbId = key.tmdbId,
                            mediaType = mediaType,
                            bgUrl = key.bgUrl,
                            ambUrl = key.ambUrl,
                            onPlayLibraryItem = { jellyfinId ->
                                navViewModel.replaceTop(DetailKey(jellyfinId, key.bgUrl, key.ambUrl))
                            },
                            onRecommendedItem = { item, bg, amb ->
                                navViewModel.push(resolveSeerrNavKey(item, bg, amb))
                            },
                            onPersonClick = { tmdbPersonId ->
                                navViewModel.push(PersonKey(tmdbId = tmdbPersonId))
                            },
                            onBack = { navViewModel.pop() }
                        )
                    }
                    entry<PlayerKey> { key ->
                        PlayerScreen(
                            itemId = key.itemId,
                            startTicks = key.startTicks?.takeIf { it > 0L },
                            mediaSourceId = key.mediaSourceId,
                            queue = key.queue,
                            onExit = { navViewModel.pop() },
                            onPlayNext = { nextId ->
                                navViewModel.replaceTop(PlayerKey(nextId, queue = key.queue?.advanced()))
                            }
                        )
                    }
                    entry<EpisodesKey> { key ->
                        SeriesEpisodesScreen(
                            seriesId = key.seriesId,
                            ambUrl = key.ambUrl,
                            onPlay = { id, ticks -> navViewModel.push(PlayerKey(id, ticks)) },
                            onBack = { navViewModel.pop() },
                            initialSeasonId = key.seasonId,
                            initialFocusEpisodeId = key.focusEpisodeId,
                            onGoToSeries = { seriesId -> navViewModel.push(DetailKey(seriesId)) }
                        )
                    }
                    entry<CollectionKey> { key ->
                        CollectionScreen(
                            collectionId = key.itemId,
                            onPlay = { id ->
                                navViewModel.push(
                                    PlayerKey(id, queue = ItemQueue(key.itemId, QueueKind.COLLECTION))
                                )
                            },
                            onShuffle = { id, seed ->
                                navViewModel.push(
                                    PlayerKey(id, queue = ItemQueue(key.itemId, QueueKind.COLLECTION, shuffleSeed = seed))
                                )
                            },
                            onItem = navViewModel::openItem,
                            onBack = { navViewModel.pop() }
                        )
                    }
                    entry<PlaylistKey> { key ->
                        PlaylistScreen(
                            playlistId = key.playlistId,
                            playlistName = key.name,
                            onPlay = { id, ticks, position ->
                                navViewModel.push(
                                    PlayerKey(id, ticks, queue = ItemQueue(key.playlistId, QueueKind.PLAYLIST, position = position))
                                )
                            },
                            onShuffle = { id, seed ->
                                navViewModel.push(
                                    PlayerKey(id, queue = ItemQueue(key.playlistId, QueueKind.PLAYLIST, shuffleSeed = seed))
                                )
                            },
                            onGoToSeries = { seriesId -> navViewModel.push(DetailKey(seriesId)) },
                            onAddToPlaylist = { item -> addToPlaylistItem = item },
                            onBack = { navViewModel.pop() }
                        )
                    }
                    entry<PersonKey> { key ->
                        PersonScreen(
                            jellyfinPersonId = key.jellyfinPersonId,
                            tmdbId = key.tmdbId,
                            onItem = navViewModel::openItem,
                            onSeerrItem = { item, bg, amb ->
                                navViewModel.push(resolveSeerrNavKey(item, bg, amb))
                            },
                            onBack = { navViewModel.pop() }
                        )
                    }
                }
            )

            seerrMenuItem?.let { item ->
                SeerrCardContextMenu(
                    item = item,
                    onLibraryItem = { libraryItem ->
                        seerrMenuItem = null
                        contextMenuItem = libraryItem
                    },
                    onDismiss = { seerrMenuItem = null }
                )
            }

            contextMenuItem?.let { item ->
                GlobalContextMenuDialog(
                    item = item,
                    onDismiss = { contextMenuItem = null },
                    onPlay = { id, ticks ->
                        contextMenuItem = null
                        navViewModel.push(PlayerKey(id, ticks))
                    },
                    onMarkWatched = { played -> contextMenuViewModel.setWatched(item, played) },
                    onToggleFavorite = { favorite -> contextMenuViewModel.setFavorite(item, favorite) },
                    onGoToSeries = if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
                        { seriesId ->
                            contextMenuItem = null
                            val nav = imageUrls.navImages(item)
                            navViewModel.push(DetailKey(seriesId, nav.bgUrl, nav.ambUrl))
                        }
                    } else {
                        null
                    },
                    onAddToPlaylist = {
                        contextMenuItem = null
                        addToPlaylistItem = item
                    },
                    extraActions = if (contextMenuFromContinueWatching) {
                        listOf(
                            ContextMenuAction("Remove from Continue watching", Icons.Default.Close) {
                                contextMenuViewModel.hideFromContinueWatching(item)
                            }
                        )
                    } else {
                        emptyList()
                    }
                )
            }

            addToPlaylistItem?.let { item ->
                AddToPlaylistDialog(item = item, onDismiss = { addToPlaylistItem = null })
            }
        }
    }
}
