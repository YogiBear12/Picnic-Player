package app.picnic.player.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.socket.ServerNotice
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

private const val NOTICE_VISIBLE_MS = 5_000L

/** Semi-transparent dark "glass" fill shared by the app's dialogs (SeasonRequestDialog,
 *  OverflowMenuDialog); the notice matches so it reads as one of them. */
private val NoticeGlassFill = PicnicColors.GlassFill
private val NoticeCornerRadius = 12.dp

/**
 * App-level host for transient server notices pushed over the socket: remote
 * `DisplayMessage`/`SendString` text and server-lifecycle events. Overlaid above the whole nav
 * host so a notice shows regardless of the current screen (browse or player). Auto-dismisses;
 * a newer notice replaces the current one. Non-focusable so it never steals D-pad focus on TV.
 */
@Composable
fun ServerNoticeHost(
    notices: SharedFlow<ServerNotice>,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf<ServerNotice?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        notices.collect { current = it }
    }
    androidx.compose.runtime.LaunchedEffect(current) {
        if (current != null) {
            delay(NOTICE_VISIBLE_MS)
            current = null
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            val notice = current
            Column(
                modifier = Modifier
                    .padding(top = 48.dp)
                    .widthIn(max = 640.dp)
                    .shadow(8.dp, RoundedCornerShape(NoticeCornerRadius))
                    .clip(RoundedCornerShape(NoticeCornerRadius))
                    .background(NoticeGlassFill)
                    .padding(PaddingValues(horizontal = 24.dp, vertical = 16.dp))
            ) {
                notice?.header?.takeIf { it.isNotBlank() }?.let { header ->
                    Text(
                        text = header,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = notice?.text.orEmpty(),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
