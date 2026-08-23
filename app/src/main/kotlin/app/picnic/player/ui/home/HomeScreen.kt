package app.picnic.player.ui.home

import androidx.compose.runtime.Composable
import androidx.tv.material3.DrawerState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import app.picnic.player.ui.browse.BrowseDest
import app.picnic.player.ui.browse.BrowseShellHost
import org.jellyfin.sdk.model.api.BaseItemDto

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onSeerrItem: (app.picnic.player.data.seerr.SeerrCatalogItem, String?, String?) -> Unit,
    onGenre: (BaseItemDto) -> Unit,
    onLibraryGenre: (BaseItemDto, BrowseDest.Library) -> Unit,
    onCollection: (BaseItemDto) -> Unit,
    onPlaylist: (BaseItemDto) -> Unit,
    onPerson: (BaseItemDto) -> Unit,
    onSessionExpired: (String) -> Unit,
    onServerUnreachable: (String, String) -> Unit,
    onSettings: () -> Unit,
    onSwapUser: () -> Unit,
    drawerState: DrawerState
) = BrowseShellHost(
    onItem = onItem,
    onSeerrItem = onSeerrItem,
    onGenre = onGenre,
    onLibraryGenre = onLibraryGenre,
    onCollection = onCollection,
    onPlaylist = onPlaylist,
    onPerson = onPerson,
    onSessionExpired = onSessionExpired,
    onServerUnreachable = onServerUnreachable,
    onSettings = onSettings,
    onSwapUser = onSwapUser,
    drawerState = drawerState
)
