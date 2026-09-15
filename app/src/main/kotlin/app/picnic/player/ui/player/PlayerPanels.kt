package app.picnic.player.ui.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.player.osd.SidePanel
import app.picnic.player.ui.player.osd.TrackPanel

@Composable
fun BoxScope.TrackSidePanel(
    visible: Boolean,
    title: String,
    options: List<TrackOption>,
    allowOff: Boolean,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit
) {
    SidePanel(
        visible = visible,
        width = PanelWidth.FloatingWide,
        modifier = Modifier.zIndex(3f)
    ) { active ->
        TrackPanel(
            title = title,
            options = options,
            allowOff = allowOff,
            onSelect = onSelect,
            active = active,
            onClose = onClose
        )
    }
}
