package app.picnic.player.ui.player.osd

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import app.picnic.player.data.settings.OsdStyle
import app.picnic.player.data.settings.SeekMode
import app.picnic.player.ui.player.PlayerUiState
import app.picnic.player.ui.player.TrickplayFrame
import app.picnic.player.ui.player.TrickplayPreview

@Composable
fun PlayerOsd(
    style: OsdStyle,
    state: PlayerUiState,
    seekMode: SeekMode,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSettings: () -> Unit,
    onChapters: () -> Unit,
    skipForwardSeconds: Int,
    skipBackwardSeconds: Int,
    onDismiss: () -> Unit,
    onInteract: () -> Unit,
    onScrubPreviewChange: (TrickplayPreview?) -> Unit,
    onScrubBarBottomInset: (Dp) -> Unit,
    trickplayFor: (Long) -> TrickplayFrame?,
    audioFocusRequester: FocusRequester,
    subtitleFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    scrubberFocusRequester: FocusRequester,
    osdSkipFocusRequester: FocusRequester,
    showSkipInOsd: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true
) {
    when (style) {
        OsdStyle.MODERN -> ModernOsd(
            state = state,
            seekMode = seekMode,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            onAudio = onAudio,
            onSubtitles = onSubtitles,
            onSettings = onSettings,
            onChapters = onChapters,
            skipForwardSeconds = skipForwardSeconds,
            skipBackwardSeconds = skipBackwardSeconds,
            onDismiss = onDismiss,
            onInteract = onInteract,
            onScrubPreviewChange = onScrubPreviewChange,
            onScrubBarBottomInset = onScrubBarBottomInset,
            trickplayFor = trickplayFor,
            audioFocusRequester = audioFocusRequester,
            subtitleFocusRequester = subtitleFocusRequester,
            settingsFocusRequester = settingsFocusRequester,
            scrubberFocusRequester = scrubberFocusRequester,
            osdSkipFocusRequester = osdSkipFocusRequester,
            showSkipInOsd = showSkipInOsd,
            onSkip = onSkip,
            focusEnabled = focusEnabled,
            modifier = modifier
        )
    }
}
