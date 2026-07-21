package app.picnic.player.ui.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.ui.ambient.BackdropHostLayer
import app.picnic.player.ui.ambient.LocalBackdropController
import app.picnic.player.ui.browse.NavRailViewModel
import app.picnic.player.ui.collection.CollectionScreen
import app.picnic.player.ui.common.ContextMenuHandler
import app.picnic.player.ui.common.GlobalContextMenuDialog
import app.picnic.player.ui.common.GlobalContextMenuViewModel
import app.picnic.player.ui.common.LocalAddToPlaylist
import app.picnic.player.ui.common.LocalContextMenuHandler
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
import app.picnic.player.ui.settings.SettingsScreen
import app.picnic.player.ui.startup.StartupScreen
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

// Material 3 Motion Tokens for TV
private const val EnterDurationMs = 400
private const val ExitDurationMs = 300
private val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f) // Emphasized Decelerate
private val ExitEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f) // Emphasized Accelerate

/**
 * App navigation on Navigation 3: the back stack is snapshot state
 * owned here, [NavDisplay] renders the top [NavKey]. Startup resolves the bootstrap phase
 * and replaces itself once; the onboarding wizard (server → login) and the return-user
 * pickers (servers / profiles) feed into the browse shell. Detail launches the player.
 *
 * The nav drawer lives ONLY inside the browse shell ([BrowseKey] → BrowseShellHost), where
 * Home / Search / libraries are tabs — the standard living-room TV pattern. Every other
 * destination (Detail, Collection, Genre, Episodes, Settings, Player, onboarding) is
 * full-screen and simply fades over the shell via the crossfade specs.
 *
 * [NavDisplay] disposes the shell entry while a full-screen destination sits on top, so the
 * shell (and its single drawer instance) re-enters composition on Back. Its open/closed
 * [DrawerState] is therefore hoisted HERE — above that disposal boundary — so it survives
 * back-nav in its last state (resting Closed) instead of the drawer reconstructing a default
 * state and replaying its open→close animation each time (#106).
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PicnicNavHost(
    railViewModel: NavRailViewModel = hiltViewModel(),
    navViewModel: AppNavigationViewModel = hiltViewModel()
) {
    val backStack = navViewModel.backStack
    val backdropController = LocalBackdropController.current
    val rail = railViewModel.rail

    // Drawer open/closed state, hoisted above the disposable BrowseKey entry (see class doc,
    // #106). rememberSaveable also carries it across process death.
    val drawerState = rememberSaveable(saver = DrawerState.Saver) { DrawerState(DrawerValue.Closed) }

    fun resetStackTo(key: NavKey) {
        navViewModel.resetTo(key)
    }

    fun goProfilePicker(serverId: String) {
        backdropController.clear()
        rail.clear()
        resetStackTo(ProfilePickerKey(serverId))
    }

    // Sign-out / session-expiry tears the whole stack down — drop the backdrop and rail.
    fun goStartup() {
        backdropController.clear()
        rail.clear()
        resetStackTo(StartupKey)
    }

    // Episodes have no detail screen — clicking an episode card anywhere opens its series'
    // season/episode listing, focused on that episode. Everything else (movies, series) → detail.
    fun navItem(item: BaseItemDto, bg: String?, amb: String?) {
        val seriesId = item.seriesId
        if (item.type == BaseItemKind.EPISODE && seriesId != null) {
            navViewModel.push(
                EpisodesKey(
                    seriesId = seriesId.toString(),
                    ambUrl = amb,
                    focusEpisodeId = item.id.toString(),
                    seasonId = item.seasonId?.toString()
                )
            )
        } else {
            navViewModel.push(DetailKey(item.id.toString(), bg, amb))
        }
    }

    val railSession by rail.session.collectAsStateWithLifecycle()

    // The drawer's user-swap action lives in the shell; it needs the nav host to route to the
    // profile picker once the session is dropped.
    val onSwapUser: () -> Unit = {
        railViewModel.softLogout()
        railSession?.server?.id?.let { goProfilePicker(it) }
    }

    var contextMenuItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var addToPlaylistItem by remember { mutableStateOf<BaseItemDto?>(null) }
    val contextMenuViewModel: GlobalContextMenuViewModel = hiltViewModel()

    val contextMenuHandler = remember {
        object : ContextMenuHandler {
            override fun show(item: BaseItemDto) {
                contextMenuItem = item
            }
        }
    }

    val addToPlaylistHandler: (BaseItemDto) -> Unit = { item -> addToPlaylistItem = item }

    CompositionLocalProvider(
        LocalContextMenuHandler provides contextMenuHandler,
        LocalAddToPlaylist provides addToPlaylistHandler
    ) {
        Box(Modifier.fillMaxSize()) {
            // App-level backdrop (artwork + ambient wash) shared across screens; drawn once here
            // so Home → Detail with the same artwork redraws nothing (see BackdropController).
            BackdropHostLayer(Modifier.fillMaxSize())

            NavDisplay(
                backStack = backStack,
                onBack = { navViewModel.pop() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                // Uniform crossfade: a pushed full-screen destination fades over the browse shell
                // (which stays composed beneath), and Back fades it back out. All three specs
                // (forward / back / predictive-back) share it so entry and exit are symmetric.
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
                            resetStackTo(dest)
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
                                // Avoid stacking duplicate Login keys if resolve fires twice.
                                if (navViewModel.backStack.lastOrNull() != LoginKey) {
                                    navViewModel.push(LoginKey)
                                }
                            }
                        )
                    }
                    entry<LoginKey> {
                        LoginScreen(
                            onLoggedIn = {
                                resetStackTo(BrowseKey)
                                navViewModel.consumePendingDeepLink()?.let { key ->
                                    navViewModel.push(key)
                                }
                            },
                            onBack = { navViewModel.pop() }
                        )
                    }
                    entry<ServerPickerKey> {
                        ServerPickerScreen(
                            onServerSelected = { serverId ->
                                navViewModel.push(ProfilePickerKey(serverId))
                            },
                            onAddServer = { navViewModel.push(ServerEntryKey) },
                            onNoServersLeft = {
                                navViewModel.replaceTop(ServerEntryKey)
                            }
                        )
                    }
                    entry<ProfilePickerKey> { key ->
                        ProfilePickerScreen(
                            serverId = key.serverId,
                            onProfileReady = {
                                resetStackTo(BrowseKey)
                                navViewModel.consumePendingDeepLink()?.let { key ->
                                    navViewModel.push(key)
                                }
                            },
                            onNeedsLogin = { navViewModel.push(LoginKey) },
                            onChangeServer = { navViewModel.push(ServerPickerKey) }
                        )
                    }
                    entry<BrowseKey> {
                        HomeScreen(
                            onItem = { item, bg, amb -> navItem(item, bg, amb) },
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
                                    CollectionKey(collection.id.toString(), collection.name)
                                )
                            },
                            onPlaylist = { playlist ->
                                navViewModel.push(
                                    PlaylistKey(playlist.id.toString(), playlist.name)
                                )
                            },
                            onSessionExpired = { serverId -> goProfilePicker(serverId) },
                            onSettings = { navViewModel.push(SettingsKey) },
                            onSwapUser = onSwapUser,
                            drawerState = drawerState
                        )
                    }
                    entry<GenreKey> { key ->
                        GenreScreen(
                            genreId = key.genreId,
                            genreName = key.name,
                            onItem = { item, bg, amb -> navItem(item, bg, amb) },
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
                            }
                        )
                    }
                    entry<DetailKey> { key ->
                        DetailScreen(
                            itemId = key.itemId,
                            bgUrl = key.bgUrl,
                            ambUrl = key.ambUrl,
                            onPlay = { id, ticks, sourceId -> navViewModel.push(PlayerKey(id, ticks, sourceId)) },
                            onItem = { item, newBg, newAmb -> navItem(item, newBg, newAmb) },
                            onEpisodes = { id, ambUrl, seasonId, focusId -> navViewModel.push(EpisodesKey(id, ambUrl, focusId, seasonId)) },
                            onCollection = { collection ->
                                navViewModel.push(
                                    CollectionKey(collection.id.toString(), collection.name)
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
                                // Replace Seerr Detail so Back skips the request screen
                                // after available-redirect / Play bridge (#43).
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
                            onExit = { navViewModel.pop() },
                            onPlayNext = { nextId ->
                                // Swap in a fresh player for the next item; the finished
                                // episode's player leaves the stack so Back skips it.
                                navViewModel.replaceTop(PlayerKey(nextId))
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
                            collectionName = key.name,
                            onItem = { item, bg, amb -> navItem(item, bg, amb) },
                            onBack = { navViewModel.pop() },
                            onSessionExpired = { serverId -> goProfilePicker(serverId) }
                        )
                    }
                    entry<PlaylistKey> { key ->
                        PlaylistScreen(
                            playlistId = key.playlistId,
                            playlistName = key.name,
                            onPlay = { id, ticks -> navViewModel.push(PlayerKey(id, ticks)) },
                            onGoToSeries = { seriesId -> navViewModel.push(DetailKey(seriesId)) },
                            onAddToPlaylist = { item -> addToPlaylistItem = item },
                            onBack = { navViewModel.pop() }
                        )
                    }
                    entry<PersonKey> { key ->
                        PersonScreen(
                            jellyfinPersonId = key.jellyfinPersonId,
                            tmdbId = key.tmdbId,
                            onItem = { item, newBg, newAmb -> navItem(item, newBg, newAmb) },
                            onSeerrItem = { item, bg, amb ->
                                navViewModel.push(resolveSeerrNavKey(item, bg, amb))
                            },
                            onBack = { navViewModel.pop() }
                        )
                    }
                }
            )

            if (contextMenuItem != null) {
                val item = contextMenuItem!!
                GlobalContextMenuDialog(
                    item = item,
                    onDismiss = { contextMenuItem = null },
                    onPlay = { id, ticks ->
                        contextMenuItem = null
                        navViewModel.push(PlayerKey(id, ticks))
                    },
                    onMarkWatched = { contextMenuViewModel.toggleWatched(item, it) },
                    onToggleFavorite = { contextMenuViewModel.toggleFavorite(item, it) },
                    onGoToSeries = if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
                        { seriesId ->
                            contextMenuItem = null
                            // Carry the episode's backdrop (its parent = the series artwork) into
                            // Detail: this is the one nav path with no card-supplied bg/amb, and a
                            // freshly-fetched series item does not always carry a backdrop tag to
                            // derive from — leaving Detail with an ambient wash but no image.
                            val nav = railSession?.let { JellyfinImages.navImages(it, item) }
                            navViewModel.push(DetailKey(seriesId, nav?.bgUrl, nav?.ambUrl))
                        }
                    } else {
                        null
                    },
                    onAddToPlaylist = {
                        contextMenuItem = null
                        addToPlaylistItem = item
                    }
                )
            }

            addToPlaylistItem?.let { item ->
                AddToPlaylistDialog(item = item, onDismiss = { addToPlaylistItem = null })
            }
        }
    }
}
