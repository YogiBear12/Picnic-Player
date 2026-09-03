package app.picnic.player.ui.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.ui.player.osd.PlayerSettingsActions
import app.picnic.player.ui.player.osd.PlayerSettingsPanel
import app.picnic.player.ui.player.osd.PlayerSettingsPanelWidth
import app.picnic.player.ui.player.osd.SidePanel

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
    onReportIssue: (() -> Unit)?,
    onClose: () -> Unit
) {
    val actions = remember(viewModel) {
        PlayerSettingsActions(
            onSelectQuality = viewModel::selectQuality,
            onSpeed = viewModel::setSpeed,
            onAudioBoost = viewModel::setAudioBoost,
            onNightMode = viewModel::setNightMode,
            onSleep = viewModel::setSleep,
            onToggleStatsForNerds = viewModel::toggleStatsForNerds,
            onSubtitleAppearanceStep = { setting, forward ->
                viewModel.stepSubtitleAppearance(setting, forward)
            }
        )
    }
    SidePanel(
        visible = visible,
        width = PlayerSettingsPanelWidth,
        modifier = Modifier.zIndex(3f)
    ) { active ->
        PlayerSettingsPanel(
            state = state,
            subtitleAppearance = subtitleAppearance,
            pipSupported = viewModel.pictureInPictureSupported,
            actions = actions,
            focusSubtitleDelay = focusSubtitleDelay,
            onFocusSubtitleDelayConsumed = onFocusSubtitleDelayConsumed,
            onAdjustSubtitleDelay = onAdjustSubtitleDelay,
            onEnterPip = onEnterPip,
            onReportIssue = onReportIssue,
            active = active,
            onClose = onClose
        )
    }
}
