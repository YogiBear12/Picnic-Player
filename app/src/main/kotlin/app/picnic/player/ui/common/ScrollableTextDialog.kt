package app.picnic.player.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.launch

/** D-pad scroll step, in dp — roughly a few lines per press. */
private val ScrollStep = 96.dp

/**
 * The app's plain long-text dialog: summary/overview expansion, person biography,
 * and short info messages all share this chrome. Long text scrolls: on TV, D-pad
 * up/down drive the scroll directly (a plain focusable scroll container does not —
 * focus search consumes the key before the scroll can react), and Back dismisses.
 */
@Composable
fun ScrollableTextDialog(
    text: String,
    onDismiss: () -> Unit,
    width: Dp = PanelWidth.Reading,
    textAlign: TextAlign? = null,
    maxTextHeight: Dp? = null
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollFocus = remember { FocusRequester() }
    val stepPx = with(LocalDensity.current) { ScrollStep.toPx() }

    PicnicDialog(onDismiss = onDismiss) {
        Box(modifier = Modifier.panelSurface(width = width)) {
            Text(
                text = text,
                color = PicnicColors.OnDarkMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanelContentInset + PanelRowInnerPadding)
                    .then(if (maxTextHeight != null) Modifier.heightIn(max = maxTextHeight) else Modifier)
                    .verticalScroll(scrollState)
                    .focusRequester(scrollFocus)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val delta = when (event.key) {
                            Key.DirectionDown -> stepPx
                            Key.DirectionUp -> -stepPx
                            else -> return@onKeyEvent false
                        }
                        scope.launch { scrollState.animateScrollBy(delta) }
                        true
                    }
                    .focusable()
            )
        }
    }
    // The Dialog window can take a few frames to accept focus; retry generously so the
    // scroll container reliably ends up focused (D-pad scrolling depends on it).
    LaunchedEffect(Unit) { scrollFocus.requestFocusWhenAttached(maxFrames = 30) }
}
