package app.picnic.player.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaUrl

/**
 * Trailer picker: local trailers play in the app's player, remote (YouTube) trailers
 * launch externally — in the configured YouTube app when set, else the system default.
 */
@Composable
internal fun TrailersDialog(
    localTrailers: List<BaseItemDto>,
    remoteTrailers: List<MediaUrl>,
    trailerYouTubePackage: String?,
    onPlayLocal: (itemId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(
                containerColor = PicnicColors.Surface,
                contentColor = Color.White
            ),
            modifier = Modifier.width(400.dp).padding(32.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Select Trailer",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(localTrailers) { trailer ->
                        ListItem(
                            selected = false,
                            onClick = {
                                onDismiss()
                                onPlayLocal(trailer.id.toString())
                            },
                            headlineContent = { Text(trailer.name ?: "Local Trailer") }
                        )
                    }

                    items(remoteTrailers) { trailer ->
                        ListItem(
                            selected = false,
                            onClick = {
                                onDismiss()
                                trailer.url?.let {
                                    context.launchRemoteTrailer(it, trailerYouTubePackage)
                                }
                            },
                            headlineContent = { Text(trailer.name ?: "YouTube Trailer") }
                        )
                    }
                }
            }
        }
    }
}

/** The configured YouTube app may be uninstalled since it was chosen — fall back to any handler. */
internal fun Context.launchRemoteTrailer(url: String, youtubePackage: String?) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    youtubePackage?.let { intent.setPackage(it) }
    try {
        startActivity(intent)
    } catch (_: Exception) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
