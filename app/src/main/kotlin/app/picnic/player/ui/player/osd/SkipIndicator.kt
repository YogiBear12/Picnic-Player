package app.picnic.player.ui.player.osd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.math.abs

private val IndicatorGlassFill = Color(0xC0181E24)

data class SkipIndicatorState(
    val accumMs: Long,
    val visible: Boolean
)

@Composable
fun SkipIndicator(
    state: SkipIndicatorState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val sign = when {
            state.accumMs > 0 -> "+"
            state.accumMs < 0 -> "-"
            else -> ""
        }
        val seconds = (abs(state.accumMs) / 1000).toInt()
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(IndicatorGlassFill)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (state.accumMs < 0) Icons.Filled.FastRewind else Icons.Filled.FastForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "$sign${seconds}s",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
