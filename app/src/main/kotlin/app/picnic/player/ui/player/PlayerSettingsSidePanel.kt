package app.picnic.player.ui.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.ui.player.osd.PlayerSettingsPanel
import app.picnic.player.ui.player.osd.PlayerSettingsPanelWidth
import app.picnic.player.ui.player.osd.SidePanel
import app.picnic.player.ui.player.osd.qualitySummary
import app.picnic.player.ui.player.osd.serverTranscode

@Composable
fun BoxScope.PlayerSettingsSidePanel(
    visible: Boolean,
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    subtitleAppearance: SubtitleAppearance,
    focusSubtitleDelay: Boolean,
    onFocusSubtitleDelayConsumed: () -> Unit,
    onAdjustSubtitleDelay: () -> Unit,
    onEnterPip: () -> Unit,
    onClose: () -> Unit
) {
    SidePanel(
        visible = visible,
        width = PlayerSettingsPanelWidth,
        modifier = Modifier.zIndex(3f)
    ) { active ->
        val serverTranscode = serverTranscode(
            state.playMethod,
            state.streamRung,
            state.transcodingInfo?.height,
            state.transcodingInfo?.bitrate
        )
        PlayerSettingsPanel(
            subtitleDelayMs = state.subtitleDelayMs,
            subtitleAppearance = subtitleAppearance,
            onSubtitleSize = { viewModel.cycleSubtitleSize(it) },
            onSubtitleColour = { viewModel.cycleSubtitleColour(it) },
            onSubtitleBackground = { viewModel.cycleSubtitleBackground(it) },
            onSubtitleBackgroundFill = { viewModel.cycleSubtitleBackgroundFill(it) },
            onSubtitleArea = { viewModel.cycleSubtitleArea(it) },
            onSubtitleInset = { viewModel.stepSubtitleInset(it) },
            qualityOptions = state.qualityOptions,
            selectedQuality = state.activeQuality,
            qualitySummary = qualitySummary(state.playMethod, state.streamRung, serverTranscode),
            onSelectQuality = { viewModel.selectQuality(it) },
            playbackSpeed = state.playbackSpeed,
            audioBoost = state.audioBoost,
            nightMode = state.nightMode,
            sleep = state.sleep,
            showStatsForNerds = state.showStatsForNerds,
            onAdjustSubtitleDelay = onAdjustSubtitleDelay,
            onSpeed = { viewModel.setSpeed(it) },
            onAudioBoost = { viewModel.setAudioBoost(it) },
            onNightMode = { viewModel.setNightMode(it) },
            onSleep = { viewModel.setSleep(it) },
            onToggleStatsForNerds = { viewModel.toggleStatsForNerds() },
            onEnterPip = onEnterPip,
            pipSupported = viewModel.pictureInPictureSupported,
            focusSubtitleDelay = focusSubtitleDelay,
            onFocusSubtitleDelayConsumed = onFocusSubtitleDelayConsumed,
            active = active,
            onClose = onClose
        )
    }
}
