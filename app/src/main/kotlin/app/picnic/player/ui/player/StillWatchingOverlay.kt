package app.picnic.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.common.GlassRowIdleFill
import app.picnic.player.ui.common.GlassRowShape
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors

private const val ScrimAlpha = 0.92f

private const val TextLuminanceScale = 0.6f

private fun Color.dimmed(): Color = Color(red * TextLuminanceScale, green * TextLuminanceScale, blue * TextLuminanceScale, alpha)

private val TitleColor = PicnicColors.OnDark.dimmed()

private val FocusedFill = Color.White.dimmed()

private val TextInset = 96.dp

@Composable
fun StillWatchingOverlay(
    title: String,
    onKeepWatching: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusWhenAttached() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = ScrimAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = TextInset)
        ) {
            Text(
                text = if (title.isBlank()) {
                    "Are you still watching?"
                } else {
                    "Are you still watching “$title”?"
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium,
                color = TitleColor,
                textAlign = TextAlign.Center
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 40.dp)
                    .width(IntrinsicSize.Max)
            ) {
                StillWatchingButton(
                    label = "Continue watching",
                    onActivate = onKeepWatching,
                    modifier = Modifier.focusRequester(focus)
                )
                StillWatchingButton(
                    label = "Back",
                    onActivate = onExit
                )
            }
        }
    }
}

@Composable
private fun StillWatchingButton(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onActivate,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = GlassRowShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GlassRowIdleFill,
            focusedContainerColor = FocusedFill,
            pressedContainerColor = FocusedFill,
            contentColor = TitleColor,
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
