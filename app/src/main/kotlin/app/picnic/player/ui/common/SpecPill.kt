package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.util.Locale

internal val SpecPillHeight = 20.dp
internal val SpecPillBg = Color.White.copy(alpha = 0.09f)
internal val SpecPillLine = Color.White.copy(alpha = 0.16f)
internal val SpecPillInk = Color.White.copy(alpha = 0.88f)
internal val SpecPillFocusedBg = Color.Black.copy(alpha = 0.07f)
internal val SpecPillFocusedLine = Color.Black.copy(alpha = 0.22f)
internal val SpecPillFocusedInk = Color.Black.copy(alpha = 0.78f)

@Composable
internal fun SpecPill(
    text: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = SpecPillBg,
    borderColor: Color = SpecPillLine,
    contentColor: Color = SpecPillInk,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .height(SpecPillHeight)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        leadingIcon?.invoke()
        if (text != null) {
            Text(
                text = text.uppercase(Locale.ROOT),
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
