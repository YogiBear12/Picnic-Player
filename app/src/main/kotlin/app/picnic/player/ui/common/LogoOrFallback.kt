package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
fun LogoOrFallback(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    label: String? = null,
    fallback: @Composable () -> Unit
) {
    var failed by remember(url) { mutableStateOf(false) }
    if (url == null || failed) {
        fallback()
    } else {
        ArtworkImage(
            url = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            alignment = alignment,
            label = label,
            onSettled = { failed = it },
            modifier = modifier
        )
    }
}
