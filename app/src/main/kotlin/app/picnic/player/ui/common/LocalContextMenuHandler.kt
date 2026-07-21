package app.picnic.player.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import org.jellyfin.sdk.model.api.BaseItemDto

interface ContextMenuHandler {
    fun show(item: BaseItemDto)
}

val LocalContextMenuHandler = staticCompositionLocalOf<ContextMenuHandler> {
    error("No ContextMenuHandler provided")
}

/** Opens the shared "Add to playlist" picker for [BaseItemDto]. Provided at the nav-host level so
 *  any screen or bespoke menu can trigger the one dialog without wiring its own. */
val LocalAddToPlaylist = staticCompositionLocalOf<(BaseItemDto) -> Unit> {
    error("No AddToPlaylist handler provided")
}
