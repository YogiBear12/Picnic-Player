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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.util.Locale

internal val SpecPillHeight = 20.dp
private val SpecPillBg = Color.White.copy(alpha = 0.09f)
private val SpecPillLine = Color.White.copy(alpha = 0.16f)
internal val SpecPillInk = Color.White.copy(alpha = 0.88f)

private val TrackChipBg = Color.White.copy(alpha = 0.07f)
private val TrackChipLine = Color.White.copy(alpha = 0.12f)
private val TrackChipInk = Color.White.copy(alpha = 0.62f)
private val TrackChipFocusedBg = Color.Black.copy(alpha = 0.06f)
private val TrackChipFocusedLine = Color.Black.copy(alpha = 0.16f)
private val TrackChipFocusedInk = Color.Black.copy(alpha = 0.55f)

@Composable
internal fun SpecPill(
    text: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = SpecPillBg,
    borderColor: Color = SpecPillLine,
    contentColor: Color = SpecPillInk,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    PillSurface(
        text = text,
        modifier = modifier,
        containerColor = containerColor,
        borderColor = borderColor,
        contentColor = contentColor,
        height = SpecPillHeight,
        corner = 6.dp,
        horizontalPadding = 7.dp,
        fontSize = TextUnit.Unspecified,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        leadingIcon = leadingIcon
    )
}

/** Format detail on a track row is secondary to the language, so it reads quieter than a spec pill. */
@Composable
internal fun TrackChip(
    text: String,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    PillSurface(
        text = text,
        modifier = modifier,
        containerColor = if (focused) TrackChipFocusedBg else TrackChipBg,
        borderColor = if (focused) TrackChipFocusedLine else TrackChipLine,
        contentColor = if (focused) TrackChipFocusedInk else TrackChipInk,
        height = 15.dp,
        corner = 4.dp,
        horizontalPadding = 5.dp,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
        leadingIcon = null
    )
}

@Composable
private fun PillSurface(
    text: String?,
    modifier: Modifier,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    height: Dp,
    corner: Dp,
    horizontalPadding: Dp,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    letterSpacing: TextUnit,
    leadingIcon: (@Composable () -> Unit)?
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(corner))
            .height(height)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        leadingIcon?.invoke()
        if (text != null) {
            Text(
                text = text.uppercase(Locale.ROOT),
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontSize = fontSize,
                fontWeight = fontWeight,
                letterSpacing = letterSpacing,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
