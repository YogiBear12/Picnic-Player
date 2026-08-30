package app.picnic.player.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import app.picnic.player.data.seerr.SeerrCatalogItem
import org.jellyfin.sdk.model.api.BaseItemDto

interface ContextMenuHandler {
    fun show(item: BaseItemDto, fromContinueWatching: Boolean = false)
}

val LocalContextMenuHandler = staticCompositionLocalOf<ContextMenuHandler> {
    error("No ContextMenuHandler provided")
}

val LocalSeerrCardMenu = staticCompositionLocalOf<(SeerrCatalogItem) -> Unit> {
    error("No Seerr card menu handler provided")
}

val LocalAddToPlaylist = staticCompositionLocalOf<(BaseItemDto) -> Unit> {
    error("No AddToPlaylist handler provided")
}
