@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.player.osd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelHeader
import app.picnic.player.ui.common.PanelRowKeys
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.RowCheck
import app.picnic.player.ui.common.RowChevron
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.rowPrimaryColor
import app.picnic.player.ui.settings.SubtitleAppearanceSetting
import app.picnic.player.ui.settings.subtitleAppearanceRows

internal val PlayerSettingsPanelWidth = 380.dp

private val PanelGlassFill = Color(0xC0181E24)
private val PanelCornerRadius = 20.dp

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
    BOOST,
    NIGHT_MODE,
    SUB_SIZE,
    SUB_COLOR,
    SUB_BACKGROUND,
    SUB_BG_FILL,
    SUB_AREA,
    SUB_INSET
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

/**
 * One row of any panel page. Pages are lists of these — data, not layout — so navigating
 * changes what a row shows rather than replacing the row nodes and their focus machinery.
 */
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

@Composable
fun PlayerSettingsPanel(
    subtitleDelayMs: Long,
    subtitleAppearance: SubtitleAppearance,
    onSubtitleSize: (Boolean) -> Unit,
    onSubtitleColour: (Boolean) -> Unit,
    onSubtitleBackground: (Boolean) -> Unit,
    onSubtitleBackgroundFill: (Boolean) -> Unit,
    onSubtitleArea: (Boolean) -> Unit,
    onSubtitleInset: (Boolean) -> Unit,
    qualityOptions: List<QualityOption>,
    selectedQuality: QualityOption?,
    qualitySummary: String,
    onSelectQuality: (QualityOption) -> Unit,
    playbackSpeed: Float,
    audioBoost: AudioBoost,
    nightMode: NightMode,
    sleep: SleepTimerState,
    showStatsForNerds: Boolean,
    onAdjustSubtitleDelay: () -> Unit,
    onSpeed: (Float) -> Unit,
    onAudioBoost: (AudioBoost) -> Unit,
    onNightMode: (NightMode) -> Unit,
    onSleep: (SleepMode) -> Unit,
    onToggleStatsForNerds: () -> Unit,
    onEnterPip: () -> Unit,
    pipSupported: Boolean,
    focusSubtitleDelay: Boolean,
    onFocusSubtitleDelayConsumed: () -> Unit,
    active: Boolean,
    onClose: () -> Unit
) {
    var page by remember { mutableStateOf(Page.MAIN) }
    // The list row to focus on the way back — from a sub-page, or from the delay HUD, which
    // disposes this panel entirely while it is up.
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
    // The panel slides in, so its rows are not attached for the first frames.
    LaunchedEffect(page) { firstFocus.requestFocusWhenAttached() }
    LaunchedEffect(Unit) { if (focusSubtitleDelay) onFocusSubtitleDelayConsumed() }

    val rows = when (page) {
        Page.MAIN -> mainRows(
            qualityOptions = qualityOptions,
            qualitySummary = qualitySummary,
            subtitleDelayMs = subtitleDelayMs,
            audioBoost = audioBoost,
            nightMode = nightMode,
            playbackSpeed = playbackSpeed,
            sleep = sleep,
            showStatsForNerds = showStatsForNerds,
            pipSupported = pipSupported,
            onNavigate = { page = it },
            onAdjustSubtitleDelay = onAdjustSubtitleDelay,
            onToggleStatsForNerds = onToggleStatsForNerds,
            onEnterPip = {
                onEnterPip()
                back()
            }
        )
        Page.QUALITY -> qualityRows(qualityOptions, selectedQuality) {
            onSelectQuality(it)
            returnToMain()
        }
        Page.SPEED -> speedRows(playbackSpeed) {
            onSpeed(it)
            returnToMain()
        }
        Page.SLEEP -> sleepRows(sleep) {
            onSleep(it)
            returnToMain()
        }
        Page.AUDIO -> audioRows(audioBoost, nightMode, onAudioBoost, onNightMode)
        Page.SUBTITLE_APPEARANCE -> subtitleAppearancePanelRows(
            subtitleAppearance,
            onSubtitleSize,
            onSubtitleColour,
            onSubtitleBackground,
            onSubtitleBackgroundFill,
            onSubtitleArea,
            onSubtitleInset
        )
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
                .width(PlayerSettingsPanelWidth)
                .fillMaxHeight()
                .padding(top = SidePanelEdgeInset, bottom = SidePanelEdgeInset, end = SidePanelEdgeInset)
                .clip(RoundedCornerShape(PanelCornerRadius))
                .background(PanelGlassFill)
                .padding(vertical = 20.dp)
                .focusGroup()
        ) {
            PanelHeader(title = page.title())
            Column(
                Modifier
                    .padding(horizontal = PanelContentInset)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Deliberately not keyed: positional identity lets Compose update the existing
                // row nodes across a page change instead of rebuilding them.
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
    showStatsForNerds: Boolean,
    pipSupported: Boolean,
    onNavigate: (Page) -> Unit,
    onAdjustSubtitleDelay: () -> Unit,
    onToggleStatsForNerds: () -> Unit,
    onEnterPip: () -> Unit
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
            "Audio",
            "Boost ${audioBoostLabel(audioBoost)} · Night ${nightModeLabel(nightMode)}"
        ) { onNavigate(Page.AUDIO) }
    )
    add(PanelRow.Nav(RowKey.SPEED, "Playback speed", formatSpeed(playbackSpeed)) { onNavigate(Page.SPEED) })
    add(PanelRow.Nav(RowKey.SLEEP, "Sleep timer", sleepSummary(sleep)) { onNavigate(Page.SLEEP) })
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

private fun sleepRows(sleep: SleepTimerState, onSelect: (SleepMode) -> Unit): List<PanelRow> = SleepMode.entries.map { mode ->
    PanelRow.Select(
        key = mode,
        primary = sleepModeLabel(mode),
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
    onSize: (Boolean) -> Unit,
    onColour: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onBackgroundFill: (Boolean) -> Unit,
    onArea: (Boolean) -> Unit,
    onInset: (Boolean) -> Unit
): List<PanelRow> = subtitleAppearanceRows(appearance).map { row ->
    val key = when (row.setting) {
        SubtitleAppearanceSetting.SIZE -> RowKey.SUB_SIZE
        SubtitleAppearanceSetting.COLOR -> RowKey.SUB_COLOR
        SubtitleAppearanceSetting.BACKGROUND -> RowKey.SUB_BACKGROUND
        SubtitleAppearanceSetting.BACKGROUND_FILL -> RowKey.SUB_BG_FILL
        SubtitleAppearanceSetting.AREA -> RowKey.SUB_AREA
        SubtitleAppearanceSetting.INSET -> RowKey.SUB_INSET
    }
    val step = when (row.setting) {
        SubtitleAppearanceSetting.SIZE -> onSize
        SubtitleAppearanceSetting.COLOR -> onColour
        SubtitleAppearanceSetting.BACKGROUND -> onBackground
        SubtitleAppearanceSetting.BACKGROUND_FILL -> onBackgroundFill
        SubtitleAppearanceSetting.AREA -> onArea
        SubtitleAppearanceSetting.INSET -> onInset
    }
    PanelRow.Step(
        key = key,
        label = row.label,
        value = row.value,
        enabled = row.enabled,
        onLeft = { step(false) },
        onRight = { step(true) }
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
    if (row.selected) RowCheck(focused)
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
