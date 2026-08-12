@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.player.osd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleBackground
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.settings.display
import app.picnic.player.ui.settings.insetDisplay

internal val PlayerSettingsPanelWidth = 380.dp

private val PanelGlassFill = Color(0xC0181E24)
private val PanelCornerRadius = 20.dp
private val ContentInset = 16.dp
private val RowInnerPadding = 14.dp
private val RowCornerRadius = 10.dp

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
        Page.SUBTITLE_APPEARANCE -> subtitleAppearanceRows(
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
                    .padding(horizontal = ContentInset)
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

private fun subtitleAppearanceRows(
    appearance: SubtitleAppearance,
    onSize: (Boolean) -> Unit,
    onColour: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onBackgroundFill: (Boolean) -> Unit,
    onArea: (Boolean) -> Unit,
    onInset: (Boolean) -> Unit
): List<PanelRow> = listOf(
    PanelRow.Step(
        key = RowKey.SUB_SIZE,
        label = "Size",
        value = appearance.size.display(),
        onLeft = { onSize(false) },
        onRight = { onSize(true) }
    ),
    PanelRow.Step(
        key = RowKey.SUB_COLOR,
        label = "Color",
        value = appearance.colour.display(),
        onLeft = { onColour(false) },
        onRight = { onColour(true) }
    ),
    PanelRow.Step(
        key = RowKey.SUB_BACKGROUND,
        label = "Background",
        value = appearance.background.display(),
        onLeft = { onBackground(false) },
        onRight = { onBackground(true) }
    ),
    // The fill stays listed while the background is off — removing a focused row disposes
    // the focused node.
    PanelRow.Step(
        key = RowKey.SUB_BG_FILL,
        label = "Background fill",
        value = appearance.backgroundFill.display(),
        enabled = appearance.background != SubtitleBackground.OFF,
        onLeft = { onBackgroundFill(false) },
        onRight = { onBackgroundFill(true) }
    ),
    PanelRow.Step(
        key = RowKey.SUB_AREA,
        label = "Subtitle area",
        value = appearance.area.display(),
        onLeft = { onArea(false) },
        onRight = { onArea(true) }
    ),
    PanelRow.Step(
        key = RowKey.SUB_INSET,
        label = "Subtitle offset",
        value = appearance.insetDisplay(),
        onLeft = { onInset(false) },
        onRight = { onInset(true) }
    )
)

@Composable
private fun PanelHeader(title: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentInset)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = RowInnerPadding)
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * The single row used by every page. Left/Right step a [PanelRow.Step] in place; Center
 * activates a [PanelRow.Nav] or [PanelRow.Select]; Back is handled by the panel.
 */
@Composable
private fun PanelRowItem(
    row: PanelRow,
    focusRequester: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean,
    onClose: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val stepper = row as? PanelRow.Step
    val enabled = stepper?.enabled ?: true
    val base = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Row(
        modifier = base
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCornerRadius))
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties {
                left = FocusRequester.Cancel
                if (stepper != null) right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (stepper != null && enabled) stepper.onLeft()
                        stepper != null
                    }
                    Key.DirectionRight -> {
                        if (stepper != null && enabled) stepper.onRight()
                        stepper != null
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        when (row) {
                            is PanelRow.Nav -> row.onClick()
                            is PanelRow.Select -> row.onClick()
                            is PanelRow.Step -> Unit
                        }
                        row !is PanelRow.Step
                    }
                    Key.Back -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = RowInnerPadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f),
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
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = when {
            !row.chevron -> Color.Transparent
            focused -> Color.Black
            else -> Color.White.copy(alpha = 0.6f)
        }
    )
}

@Composable
private fun RowScope.SelectContent(row: PanelRow.Select, focused: Boolean) {
    Text(
        row.primary,
        color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f),
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
    if (row.selected) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "Selected",
            tint = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun RowScope.StepContent(row: PanelRow.Step, focused: Boolean) {
    val labelColor = when {
        !row.enabled && focused -> Color.Black.copy(alpha = 0.4f)
        !row.enabled -> Color.White.copy(alpha = 0.38f)
        focused -> Color.Black
        else -> Color.White.copy(alpha = 0.92f)
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
