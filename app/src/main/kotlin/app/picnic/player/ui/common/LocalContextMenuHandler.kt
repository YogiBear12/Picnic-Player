package app.picnic.player.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import org.jellyfin.sdk.model.api.BaseItemDto

interface ContextMenuHandler {
    fun show(item: BaseItemDto)
}

val LocalContextMenuHandler = staticCompositionLocalOf<ContextMenuHandler> {
    error("No ContextMenuHandler provided")
}
