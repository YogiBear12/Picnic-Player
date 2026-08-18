package app.picnic.player.ui.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.ui.player.osd.PlayerSettingsPanel
import app.picnic.player.ui.player.osd.PlayerSettingsPanelWidth
import app.picnic.player.ui.player.osd.SidePanel
import app.picnic.player.ui.player.osd.TrackPanel
import app.picnic.player.ui.player.osd.TrackPanelWidth
import app.picnic.player.ui.player.osd.qualitySummary
import app.picnic.player.ui.player.osd.serverTranscode

@Composable
fun BoxScope.PlayerPanels(
    viewModel: PlayerViewModel,
    chrome: PlayerChrome,
    state: PlayerUiState,
    settings: PlaybackSettings,
    pipState: PipController,
    onClosePanel: () -> Unit
) {
    SidePanel(
        visible = chrome.panel == Panel.AUDIO,
        width = TrackPanelWidth,
        modifier = Modifier.zIndex(3f)
    ) { active ->
        TrackPanel(
            title = "Audio",
            options = state.audioTracks,
            allowOff = false,
            onSelect = { id ->
                id?.let(viewModel::selectAudio)
                onClosePanel()
            },
            active = active,
            onClose = onClosePanel
        )
    }
    SidePanel(
        visible = chrome.panel == Panel.SUBTITLE,
        width = TrackPanelWidth,
        modifier = Modifier.zIndex(3f)
    ) { active ->
        TrackPanel(
            title = "Subtitles",
            options = state.subtitleTracks,
            allowOff = true,
            onSelect = { id ->
                viewModel.selectSubtitle(id)
                onClosePanel()
            },
            active = active,
            onClose = onClosePanel
        )
    }
    SidePanel(
        visible = chrome.panel == Panel.SETTINGS,
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
            subtitleAppearance = settings.subtitleAppearance,
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
            onAdjustSubtitleDelay = { chrome.enterSubtitleAdjust() },
            onSpeed = { viewModel.setSpeed(it) },
            onAudioBoost = { viewModel.setAudioBoost(it) },
            onNightMode = { viewModel.setNightMode(it) },
            onSleep = { viewModel.setSleep(it) },
            onToggleStatsForNerds = { viewModel.toggleStatsForNerds() },
            onEnterPip = pipState::enterPip,
            pipSupported = viewModel.pictureInPictureSupported,
            focusSubtitleDelay = chrome.returningFromSubtitleAdjust,
            onFocusSubtitleDelayConsumed = chrome::consumeSubtitleAdjustReturn,
            active = active,
            onClose = onClosePanel
        )
    }
}
