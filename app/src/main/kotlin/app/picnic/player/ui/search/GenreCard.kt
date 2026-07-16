@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import kotlin.math.abs

/**
 * 16:9 genre tile for the search tab's browse grid: a deterministic two-stop
 * gradient (hashed from the genre name, stable across sessions) with the name
 * bottom-left — the familiar TV "browse by genre" tile pattern. Focus chrome is the
 * standard plain white ring.
 */
@Composable
internal fun GenreCard(
    name: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val gradient = remember(name) { genreGradient(name) }
    var focused by remember { mutableStateOf(false) }
    val focusedGlow = rememberCardFocusGlow(Color.White, focused)

    var cardModifier = modifier
        .aspectRatio(16f / 9f)
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
    if (focusRequester != null) cardModifier = cardModifier.focusRequester(focusRequester)

    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(CardFocusBorderWidth, Color.White), shape = shape)
        ),
        glow = CardDefaults.glow(focusedGlow = focusedGlow),
        modifier = cardModifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(gradient.first, gradient.second))
                )
        ) {
            Text(
                text = name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/** Saturated-to-deep gradient pairs; hue picked by name hash so a genre keeps its colour. */
private val GenreGradients = listOf(
    Color(0xFF7C5CE0) to Color(0xFF37256E), // violet
    Color(0xFFE05C8A) to Color(0xFF6E2540), // rose
    Color(0xFF5C9CE0) to Color(0xFF254A6E), // azure
    Color(0xFF5CE0B8) to Color(0xFF256E55), // mint
    Color(0xFFE09C5C) to Color(0xFF6E4A25), // amber
    Color(0xFFE05C5C) to Color(0xFF6E2525), // crimson
    Color(0xFF9CE05C) to Color(0xFF4A6E25), // lime
    Color(0xFF5CE0E0) to Color(0xFF256E6E), // teal
    Color(0xFFB85CE0) to Color(0xFF55256E), // orchid
    Color(0xFFE0D05C) to Color(0xFF6E6425), // gold
    Color(0xFF5C6CE0) to Color(0xFF25306E), // indigo
    Color(0xFFE07C5C) to Color(0xFF6E3725) // coral
)

internal fun genreGradient(name: String): Pair<Color, Color> = GenreGradients[abs(name.lowercase().hashCode()) % GenreGradients.size]
