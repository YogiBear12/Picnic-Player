@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.player.osd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleAppearanceSetting
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelFadeLength
import app.picnic.player.ui.common.PanelFloatingHeight
import app.picnic.player.ui.common.PanelHeader
import app.picnic.player.ui.common.PanelRowKeys
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.RowCheck
import app.picnic.player.ui.common.RowChevron
import app.picnic.player.ui.common.panelGlass
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.rowPrimaryColor
import app.picnic.player.ui.common.verticalFadingEdges
import app.picnic.player.ui.player.PlayerUiState
import app.picnic.player.ui.settings.subtitleAppearanceRows

private val Speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val Boosts = AudioBoost.entries
private val NightModes = NightMode.entries

private enum class Page { MAIN, QUALITY, SPEED, AUDIO, SLEEP, SUBTITLE_APPEARANCE }

private enum class RowKey {
    QUALITY,
    SUBTITLE_APPEARANCE,
    SUBTITLE_DELAY,
    AUDIO,
    SPEED,
    SLEEP,
    PLAYBACK_INFO,
    PIP,
    REPORT_ISSUE,
    BOOST,
    NIGHT_MODE
}

private fun Page.rowKey(): RowKey? = when (this) {
    Page.QUALITY -> RowKey.QUALITY
    Page.SUBTITLE_APPEARANCE -> RowKey.SUBTITLE_APPEARANCE
    Page.AUDIO -> RowKey.AUDIO
    Page.SPEED -> RowKey.SPEED
    Page.SLEEP -> RowKey.SLEEP
    Page.MAIN -> null
}

private fun Page.title(): String = when (this) {
    Page.MAIN -> "Settings"
    Page.QUALITY -> "Quality"
    Page.SPEED -> "Playback speed"
    Page.AUDIO -> "Audio"
    Page.SLEEP -> "Sleep timer"
    Page.SUBTITLE_APPEARANCE -> "Subtitle appearance"
}

private sealed interface PanelRow {
    val key: Any

    data class Nav(
        override val key: Any,
        val label: String,
        val value: String,
        val chevron: Boolean = true,
        val onClick: () -> Unit
    ) : PanelRow

    data class Select(
        override val key: Any,
        val primary: String,
        val secondary: String? = null,
        val selected: Boolean,
        val onClick: () -> Unit
    ) : PanelRow

    data class Step(
        override val key: Any,
        val label: String,
        val value: String,
        val enabled: Boolean = true,
        val onLeft: () -> Unit,
        val onRight: () -> Unit
    ) : PanelRow
}

@Immutable
data class PlayerSettingsActions(
    val onSelectQuality: (QualityOption) -> Unit,
    val onSpeed: (Float) -> Unit,
    val onAudioBoost: (AudioBoost) -> Unit,
    val onNightMode: (NightMode) -> Unit,
    val onSleep: (SleepMode) -> Unit,
    val onToggleStatsForNerds: () -> Unit,
    val onSubtitleAppearanceStep: (SubtitleAppearanceSetting, Boolean) -> Unit
)

@Composable
fun PlayerSettingsPanel(
    state: PlayerUiState,
    subtitleAppearance: SubtitleAppearance,
    pipSupported: Boolean,
    actions: PlayerSettingsActions,
    focusSubtitleDelay: Boolean,
    onFocusSubtitleDelayConsumed: () -> Unit,
    onAdjustSubtitleDelay: () -> Unit,
    onEnterPip: () -> Unit,
    onReportIssue: (() -> Unit)?,
    active: Boolean,
    onClose: () -> Unit
) {
    var page by remember { mutableStateOf(Page.MAIN) }
    var focusKey by remember {
        mutableStateOf(if (focusSubtitleDelay) RowKey.SUBTITLE_DELAY else null)
    }
    val firstFocus = remember { FocusRequester() }

    fun returnToMain() {
        focusKey = page.rowKey()
        page = Page.MAIN
    }

    fun back() {
        if (page == Page.MAIN) onClose() else returnToMain()
    }
    BackHandler(enabled = active) { back() }
    LaunchedEffect(page) { firstFocus.requestFocusWhenAttached() }
    LaunchedEffect(Unit) { if (focusSubtitleDelay) onFocusSubtitleDelayConsumed() }

    val qualitySummary = qualitySummary(
        state.playMethod,
        state.streamRung,
        serverTranscode(
            state.playMethod,
            state.streamRung,
            state.transcodingInfo?.height,
            state.transcodingInfo?.bitrate
        )
    )

    val rows = when (page) {
        Page.MAIN -> mainRows(
            qualityOptions = state.qualityOptions,
            qualitySummary = qualitySummary,
            subtitleDelayMs = state.subtitleDelayMs,
            audioBoost = state.audioBoost,
            nightMode = state.nightMode,
            playbackSpeed = state.playbackSpeed,
            sleep = state.sleep,
            isEpisode = state.isEpisode,
            showStatsForNerds = state.showStatsForNerds,
            pipSupported = pipSupported,
            onNavigate = { page = it },
            onAdjustSubtitleDelay = onAdjustSubtitleDelay,
            onToggleStatsForNerds = actions.onToggleStatsForNerds,
            onEnterPip = {
                onEnterPip()
                back()
            },
            onReportIssue = onReportIssue
        )
        Page.QUALITY -> qualityRows(state.qualityOptions, state.activeQuality) {
            actions.onSelectQuality(it)
            returnToMain()
        }
        Page.SPEED -> speedRows(state.playbackSpeed) {
            actions.onSpeed(it)
            returnToMain()
        }
        Page.SLEEP -> sleepRows(state.sleep, state.isEpisode, state.nextUp != null) {
            actions.onSleep(it)
            returnToMain()
        }
        Page.AUDIO -> audioRows(state.audioBoost, state.nightMode, actions.onAudioBoost, actions.onNightMode)
        Page.SUBTITLE_APPEARANCE -> subtitleAppearancePanelRows(subtitleAppearance, actions.onSubtitleAppearanceStep)
    }

    val focusIndex = when (page) {
        Page.MAIN -> rows.indexOfFirst { it.key == focusKey }
        else -> rows.indexOfFirst { it is PanelRow.Select && it.selected }
    }.takeIf { it >= 0 } ?: 0

    Row(
        Modifier
            .fillMaxSize()
            .focusProperties { canFocus = active }
            .focusGroup()
    ) {
        Spacer(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusProperties { canFocus = false }
        )
        Column(
            Modifier
                .align(Alignment.CenterVertically)
                .padding(end = SidePanelEdgeInset)
                .width(PanelWidth.FloatingWide)
                .height(PanelFloatingHeight)
                .panelGlass()
                .padding(vertical = 20.dp)
                .focusGroup()
        ) {
            PanelHeader(title = page.title())
            val mainScroll = rememberScrollState()
            val subPageScroll = key(page) { rememberScrollState() }
            val rowScroll = if (page == Page.MAIN) mainScroll else subPageScroll
            Column(
                Modifier
                    .weight(1f)
                    .verticalFadingEdges(
                        topFade = rowScroll.canScrollBackward,
                        bottomFade = rowScroll.canScrollForward,
                        length = PanelFadeLength
                    )
                    .verticalScroll(rowScroll)
                    .padding(horizontal = PanelContentInset)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                rows.forEachIndexed { i, row ->
                    PanelRowItem(
                        row = row,
                        focusRequester = if (i == focusIndex) firstFocus else null,
                        blockUp = i == 0,
                        blockDown = i == rows.lastIndex,
                        onClose = ::back
                    )
                }
            }
        }
    }
}

private fun mainRows(
    qualityOptions: List<QualityOption>,
    qualitySummary: String,
    subtitleDelayMs: Long,
    audioBoost: AudioBoost,
    nightMode: NightMode,
    playbackSpeed: Float,
    sleep: SleepTimerState,
    isEpisode: Boolean,
    showStatsForNerds: Boolean,
    pipSupported: Boolean,
    onNavigate: (Page) -> Unit,
    onAdjustSubtitleDelay: () -> Unit,
    onToggleStatsForNerds: () -> Unit,
    onEnterPip: () -> Unit,
    onReportIssue: (() -> Unit)?
): List<PanelRow> = buildList {
    if (qualityOptions.isNotEmpty()) {
        add(PanelRow.Nav(RowKey.QUALITY, "Quality", qualitySummary) { onNavigate(Page.QUALITY) })
    }
    add(
        PanelRow.Nav(RowKey.SUBTITLE_APPEARANCE, "Subtitle appearance", "") {
            onNavigate(Page.SUBTITLE_APPEARANCE)
        }
    )
    add(
        PanelRow.Nav(
            RowKey.SUBTITLE_DELAY,
            "Subtitle delay",
            formatDelay(subtitleDelayMs),
            chevron = false,
            onClick = onAdjustSubtitleDelay
        )
    )
    add(
        PanelRow.Nav(
            RowKey.AUDIO,
            "Audio effects",
            if (audioBoost == AudioBoost.OFF && nightMode == NightMode.OFF) "Off" else "On"
        ) { onNavigate(Page.AUDIO) }
    )
    add(PanelRow.Nav(RowKey.SPEED, "Playback speed", formatSpeed(playbackSpeed)) { onNavigate(Page.SPEED) })
    add(PanelRow.Nav(RowKey.SLEEP, "Sleep timer", sleepSummary(sleep, isEpisode)) { onNavigate(Page.SLEEP) })
    add(
        PanelRow.Nav(
            RowKey.PLAYBACK_INFO,
            "Playback info",
            if (showStatsForNerds) "On" else "Off",
            chevron = false,
            onClick = onToggleStatsForNerds
        )
    )
    if (pipSupported) {
        add(
            PanelRow.Nav(RowKey.PIP, "Enter Picture-in-Picture", "", chevron = false, onClick = onEnterPip)
        )
    }
    if (onReportIssue != null) {
        add(
            PanelRow.Nav(RowKey.REPORT_ISSUE, "Report an issue", "", chevron = false, onClick = onReportIssue)
        )
    }
}

private fun qualityRows(
    options: List<QualityOption>,
    selected: QualityOption?,
    onSelect: (QualityOption) -> Unit
): List<PanelRow> = options.map { option ->
    val rung = (option as? QualityOption.Transcode)?.rung
    PanelRow.Select(
        key = rung ?: "original",
        primary = rung?.label ?: "Original",
        secondary = rung?.let { "${it.bitrateLabel} · ${it.sizeHint}" },
        selected = option == selected
    ) { onSelect(option) }
}

private fun speedRows(current: Float, onSelect: (Float) -> Unit): List<PanelRow> = Speeds.map { speed ->
    PanelRow.Select(
        key = speed,
        primary = formatSpeed(speed),
        selected = kotlin.math.abs(speed - current) < 0.001f
    ) { onSelect(speed) }
}

private fun sleepRows(
    sleep: SleepTimerState,
    isEpisode: Boolean,
    hasNextUp: Boolean,
    onSelect: (SleepMode) -> Unit
): List<PanelRow> = SleepMode.entries
    .filter { it != SleepMode.END_OF_EPISODE || hasNextUp }
    .map { mode ->
        PanelRow.Select(
            key = mode,
            primary = sleepModeLabel(mode, isEpisode),
            selected = mode == sleep.mode
        ) { onSelect(mode) }
    }

private fun audioRows(
    audioBoost: AudioBoost,
    nightMode: NightMode,
    onAudioBoost: (AudioBoost) -> Unit,
    onNightMode: (NightMode) -> Unit
): List<PanelRow> = listOf(
    PanelRow.Step(
        key = RowKey.BOOST,
        label = "Boost",
        value = audioBoostLabel(audioBoost),
        onLeft = { Boosts.getOrNull(audioBoost.ordinal - 1)?.let(onAudioBoost) },
        onRight = { Boosts.getOrNull(audioBoost.ordinal + 1)?.let(onAudioBoost) }
    ),
    PanelRow.Step(
        key = RowKey.NIGHT_MODE,
        label = "Night mode",
        value = nightModeLabel(nightMode),
        onLeft = { NightModes.getOrNull(nightMode.ordinal - 1)?.let(onNightMode) },
        onRight = { NightModes.getOrNull(nightMode.ordinal + 1)?.let(onNightMode) }
    )
)

private fun subtitleAppearancePanelRows(
    appearance: SubtitleAppearance,
    onStep: (SubtitleAppearanceSetting, Boolean) -> Unit
): List<PanelRow> = subtitleAppearanceRows(appearance).map { row ->
    PanelRow.Step(
        key = row.setting,
        label = row.label,
        value = row.value,
        enabled = row.enabled,
        onLeft = { onStep(row.setting, false) },
        onRight = { onStep(row.setting, true) }
    )
}

@Composable
private fun PanelRowItem(
    row: PanelRow,
    focusRequester: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean,
    onClose: () -> Unit
) {
    val stepper = row as? PanelRow.Step
    PicnicListRow(
        focusRequester = focusRequester,
        keys = PanelRowKeys(blockUp = blockUp, blockDown = blockDown),
        onActivate = if (stepper == null) {
            {
                when (row) {
                    is PanelRow.Nav -> row.onClick()
                    is PanelRow.Select -> row.onClick()
                    is PanelRow.Step -> Unit
                }
            }
        } else {
            null
        },
        onStep = stepper?.let { step -> { forward -> if (step.enabled) if (forward) step.onRight() else step.onLeft() } },
        onClose = onClose
    ) { focused ->
        when (row) {
            is PanelRow.Nav -> NavContent(row, focused)
            is PanelRow.Select -> SelectContent(row, focused)
            is PanelRow.Step -> StepContent(row, focused)
        }
    }
}

@Composable
private fun RowScope.NavContent(row: PanelRow.Nav, focused: Boolean) {
    Text(
        row.label,
        color = rowPrimaryColor(focused),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        modifier = Modifier.weight(1f)
    )
    Text(
        row.value,
        color = if (focused) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1
    )
    RowChevron(focused, visible = row.chevron)
}

@Composable
private fun RowScope.SelectContent(row: PanelRow.Select, focused: Boolean) {
    Text(
        row.primary,
        color = rowPrimaryColor(focused),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        modifier = Modifier.weight(1f)
    )
    row.secondary?.let {
        Text(
            it,
            color = if (focused) Color.Black.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
    RowCheck(focused, visible = row.selected)
}

@Composable
private fun RowScope.StepContent(row: PanelRow.Step, focused: Boolean) {
    val labelColor = when {
        !row.enabled && focused -> Color.Black.copy(alpha = 0.4f)
        !row.enabled -> Color.White.copy(alpha = 0.38f)
        else -> rowPrimaryColor(focused)
    }
    Text(
        row.label,
        color = labelColor,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        modifier = Modifier.weight(1f)
    )
    Icon(Icons.Filled.ChevronLeft, contentDescription = "Decrease", tint = labelColor)
    Text(
        row.value,
        color = labelColor,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
    Icon(Icons.Filled.ChevronRight, contentDescription = "Increase", tint = labelColor)
}
