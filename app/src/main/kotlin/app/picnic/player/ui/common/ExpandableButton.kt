package app.picnic.player.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors

private val ExpandableButtonPadding = 12.dp
private val ExpandableButtonExpandedEndPadding = 16.dp
private val ExpandableButtonIconSize = 20.dp
internal val ExpandableButtonCollapsedWidth = ExpandableButtonIconSize + ExpandableButtonPadding * 2

@Composable
fun ExpandableButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = onClick,
        modifier = modifier.height(ActionButtonHeight),
        contentPadding = if (isFocused) {
            PaddingValues(start = ExpandableButtonPadding, end = ExpandableButtonExpandedEndPadding)
        } else {
            PaddingValues(horizontal = ExpandableButtonPadding)
        },
        colors = ButtonDefaults.colors(
            containerColor = PicnicColors.GlassFillLight
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ExpandableButtonIconSize)
        )
        AnimatedVisibility(isFocused) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}
