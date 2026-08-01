@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.player.osd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.player.ChapterMark

private val PanelGlassFill = Color(0xC0181E24)

/**
 * Floating chapters card shown on D-pad Down, taking the OSD's place in the bottom slot. The
 * slide animations belong to the caller, not this composable.
 */
@Composable
fun ChaptersPanel(
    chapters: List<ChapterMark>,
    positionMs: Long,
    trickplayFor: (Long) -> TrickplayFrame?,
    onSelect: (Long) -> Unit,
    active: Boolean,
    onClose: () -> Unit
) {
    BackHandler(enabled = active) { onClose() }
    val firstFocus = remember { FocusRequester() }
    // The panel animates in, so the row is not attached for the first frames.
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenAttached() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(bottom = 28.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PanelGlassFill)
                .padding(vertical = 8.dp)
                .focusGroup()
                // Up/Down escape back to the OSD (the row only navigates horizontally).
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            onClose()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            ChapterRow(
                chapters = chapters,
                positionMs = positionMs,
                trickplayFor = trickplayFor,
                onSelect = {
                    onSelect(it)
                    onClose()
                },
                firstFocus = firstFocus
            )
        }
    }
}
