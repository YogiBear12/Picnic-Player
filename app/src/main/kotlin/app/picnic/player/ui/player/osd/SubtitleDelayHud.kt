package app.picnic.player.ui.player.osd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val HudGlassFill = Color(0xC0181E24)

/**
 * Centered glass HUD shown while the user adjusts subtitle delay with the D-pad. Intentionally
 * compact and away from the bottom so subtitles remain visible underneath — the key handling +
 * stepping live in PlayerScreen; this is presentation only.
 */
@Composable
fun SubtitleDelayHud(
    delayMs: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HudGlassFill)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Subtitle delay",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.7f)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
            Text(
                text = formatDelay(delayMs),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
        }
    }
}
