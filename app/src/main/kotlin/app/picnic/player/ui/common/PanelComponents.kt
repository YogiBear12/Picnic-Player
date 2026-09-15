package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors

internal val PanelContentInset = 10.dp
internal val PanelRowInnerPadding = 14.dp
internal val PanelRowCornerRadius = PanelCornerRadius - PanelContentInset

@Immutable
internal data class PanelRowMetrics(
    val innerPadding: Dp = PanelRowInnerPadding,
    val verticalPadding: Dp = 9.dp,
    val cornerRadius: Dp = PanelRowCornerRadius
) {
    companion object {
        val Default = PanelRowMetrics()
    }
}

@Immutable
internal data class PanelRowKeys(
    val activateOnRight: Boolean = false,
    val blockLeft: Boolean = true,
    val blockRight: Boolean = false,
    val blockUp: Boolean = false,
    val blockDown: Boolean = false
) {
    companion object {
        val Default = PanelRowKeys()
        val Free = PanelRowKeys(blockLeft = false)
    }
}

@Composable
internal fun PanelHeader(title: String, icon: ImageVector? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PanelContentInset)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = PanelRowInnerPadding)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = PicnicColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PanelDividerColor)
        )
        Spacer(Modifier.height(12.dp))
    }
}

internal fun rowPrimaryColor(focused: Boolean): Color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f)

internal fun rowTrailingColor(focused: Boolean): Color = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.55f)

@Composable
internal fun RowScope.RowCheck(focused: Boolean, visible: Boolean = true) {
    Icon(
        Icons.Filled.Check,
        contentDescription = if (visible) "Selected" else null,
        tint = if (visible) rowTrailingColor(focused) else Color.Transparent
    )
}

@Composable
internal fun RowScope.RowChevron(focused: Boolean, visible: Boolean = true) {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = when {
            !visible -> Color.Transparent
            focused -> Color.Black
            else -> Color.White.copy(alpha = 0.6f)
        }
    )
}

@Composable
internal fun PicnicListRow(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    metrics: PanelRowMetrics = PanelRowMetrics.Default,
    keys: PanelRowKeys = PanelRowKeys.Default,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    onActivate: (() -> Unit)? = null,
    onLongActivate: (() -> Unit)? = null,
    onStep: ((forward: Boolean) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable RowScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    Surface(
        onClick = { onActivate?.invoke() },
        onLongClick = onLongActivate,
        modifier = base
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusProperties {
                if (keys.blockLeft || onStep != null) left = FocusRequester.Cancel
                if (keys.blockRight || onStep != null) right = FocusRequester.Cancel
                if (keys.blockUp) up = FocusRequester.Cancel
                if (keys.blockDown) down = FocusRequester.Cancel
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onStep?.invoke(false)
                        onStep != null
                    }
                    Key.DirectionRight -> {
                        if (onStep != null) {
                            onStep(true)
                            true
                        } else if (keys.activateOnRight && onActivate != null) {
                            onActivate()
                            true
                        } else {
                            false
                        }
                    }
                    Key.Back -> {
                        onClose?.invoke()
                        onClose != null
                    }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(metrics.cornerRadius)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White
        ),
        border = flatSurfaceBorder()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = metrics.innerPadding,
                vertical = metrics.verticalPadding
            ),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(focused)
        }
    }
}
