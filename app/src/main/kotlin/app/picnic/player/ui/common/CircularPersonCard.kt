package app.picnic.player.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * Circular person card shared by the Jellyfin Cast & Crew row and the Seerr Cast row.
 * Focus chrome is the standard plain white ring (no artwork-derived accent); a person
 * without artwork shows a placeholder head instead of a blank circle.
 *
 * The cell height is fixed (like the grid's gridCellHeight) with headroom above the
 * circle for the focus scale + glow (mirrors BrowseCardStyle.topInset), so a focused
 * card expands into its own slot and neighbouring rows never move. The label gap
 * widens on focus exactly like the library grid's cards.
 *
 * [modifier] is applied to the circle Surface — the card's own focus node — so callers'
 * focusRequester/focusProperties land where D-pad searches actually consult them.
 */
@Composable
fun CircularPersonCard(
    imageUrl: String?,
    name: String?,
    subtitle: String?,
    imageSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val topInset = imageSize * (0.1f * 0.25f) + 1.dp + 6.dp
    var focused by remember { mutableStateOf(false) }
    val labelGap by animateDpAsState(
        if (focused) 14.dp else 6.dp,
        label = "personCardLabelGap"
    )
    Column(
        modifier = Modifier
            .width(imageSize + 8.dp)
            .height(topInset + imageSize + 6.dp + 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(topInset))
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(CardFocusBorderWidth, Color.White),
                    shape = CircleShape
                )
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = rememberCardFocusGlow(Color.White, focused)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            modifier = modifier
                .onFocusChanged { focused = it.isFocused }
                .size(imageSize)
        ) {
            if (imageUrl == null || imageFailed) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = name,
                        tint = Color.LightGray,
                        modifier = Modifier.size(imageSize / 2)
                    )
                }
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    onState = { if (it is AsyncImagePainter.State.Error) imageFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                )
            }
        }
        Spacer(Modifier.height(labelGap))
        Text(
            name.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
