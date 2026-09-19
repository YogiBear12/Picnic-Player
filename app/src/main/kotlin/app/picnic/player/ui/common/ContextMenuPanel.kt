package app.picnic.player.ui.common

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text

data class ContextMenuAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Stable
class ContextMenuFocus internal constructor() {
    internal var lastIndex by mutableIntStateOf(-1)
}

@Composable
fun rememberContextMenuFocus(): ContextMenuFocus = remember { ContextMenuFocus() }

private val MenuMaxHeight = 460.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContextMenuPanel(
    actions: List<ContextMenuAction>,
    focus: ContextMenuFocus? = null
) {
    val seedIndex = focus?.lastIndex
        ?.takeIf { actions.getOrNull(it)?.enabled == true }
        ?: actions.indexOfFirst { it.enabled }
    val seed = rememberFocusSeed(seedIndex, enabled = seedIndex >= 0)

    Column(
        verticalArrangement = Arrangement.spacedBy(PanelRowSpacing),
        modifier = Modifier
            .panelSurface(width = PanelWidth.Panel)
            .heightIn(max = MenuMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .focusGroup()
    ) {
        actions.forEachIndexed { index, action ->
            ListItem(
                selected = false,
                enabled = action.enabled,
                onClick = {
                    focus?.lastIndex = index
                    action.onClick()
                },
                headlineContent = { Text(action.label, color = Color.White) },
                leadingContent = {
                    Icon(action.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                shape = ListItemDefaults.shape(shape = RoundedCornerShape(PanelRowCornerRadius)),
                scale = ListItemDefaults.scale(focusedScale = 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == seedIndex) Modifier.focusSeed(seed) else Modifier)
            )
        }
    }
}
