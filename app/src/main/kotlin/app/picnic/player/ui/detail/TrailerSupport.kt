package app.picnic.player.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelHeader
import app.picnic.player.ui.common.PanelRowKeys
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.rowPrimaryColor
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaUrl

/**
 * Trailer picker: local trailers play in the app's player, remote (YouTube) trailers
 * launch externally — in the configured YouTube app when set, else the system default.
 */
private val TrailerListMaxHeight = 320.dp

@Composable
internal fun TrailersDialog(
    localTrailers: List<BaseItemDto>,
    remoteTrailers: List<MediaUrl>,
    trailerYouTubePackage: String?,
    onPlayLocal: (itemId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val seedFocus = remember { FocusRequester() }
    val rowCount = localTrailers.size + remoteTrailers.size

    PicnicDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .panelSurface(width = PanelWidth.Panel)
                .focusGroup()
        ) {
            PanelHeader("Select trailer")
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = TrailerListMaxHeight)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = PanelContentInset),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(localTrailers) { index, trailer ->
                    TrailerRow(
                        label = trailer.name ?: "Local trailer",
                        focusRequester = if (index == 0) seedFocus else null,
                        blockUp = index == 0,
                        blockDown = index == rowCount - 1,
                        onClose = onDismiss,
                        onActivate = {
                            onDismiss()
                            onPlayLocal(trailer.id.toString())
                        }
                    )
                }

                itemsIndexed(remoteTrailers) { index, trailer ->
                    val position = localTrailers.size + index
                    TrailerRow(
                        label = trailer.name ?: "YouTube trailer",
                        focusRequester = if (position == 0) seedFocus else null,
                        blockUp = position == 0,
                        blockDown = position == rowCount - 1,
                        onClose = onDismiss,
                        onActivate = {
                            onDismiss()
                            trailer.url?.let {
                                context.launchRemoteTrailer(it, trailerYouTubePackage)
                            }
                        }
                    )
                }
            }
        }
    }
    LaunchedEffect(rowCount) {
        if (rowCount > 0) seedFocus.requestFocusWhenAttached(maxFrames = 20)
    }
}

@Composable
private fun TrailerRow(
    label: String,
    focusRequester: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean,
    onClose: () -> Unit,
    onActivate: () -> Unit
) {
    PicnicListRow(
        focusRequester = focusRequester,
        keys = PanelRowKeys(blockRight = true, blockUp = blockUp, blockDown = blockDown),
        onActivate = onActivate,
        onClose = onClose
    ) { focused ->
        Text(
            text = label,
            color = rowPrimaryColor(focused),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
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
