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
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState

private val PanelDimScrim = Color(0x52000000)
private val PanelGlassFill = Color(0xC0181E24)
private val PanelCornerRadius = 20.dp
private val PanelEdgeInset = 28.dp
private val ContentInset = 16.dp
private val RowInnerPadding = 14.dp
private val RowCornerRadius = 10.dp

private val Speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val SleepModes = SleepMode.entries
private val Boosts = AudioBoost.entries
private val NightModes = NightMode.entries

private enum class Page { MAIN, SPEED, AUDIO, SLEEP }

/**
 * Frosted right-side player settings panel (mirrors [TrackPanel] chrome). The top level lists
 * Subtitle delay / Playback speed / Audio / Sleep timer; activating a row either drills into a
 * sub-page or — for subtitle delay — closes the panel and hands off to the on-screen HUD via
 * [onAdjustSubtitleDelay]. Back steps sub-page → main → closed.
 */
@Composable
fun PlayerSettingsPanel(
    subtitleDelayMs: Long,
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
    onClose: () -> Unit
) {
    var page by remember { mutableStateOf(Page.MAIN) }
    val firstFocus = remember { FocusRequester() }

    fun back() {
        if (page == Page.MAIN) onClose() else page = Page.MAIN
    }
    BackHandler { back() }
    LaunchedEffect(page) { runCatching { firstFocus.requestFocus() } }

    Row(
        Modifier
            .fillMaxSize()
            .focusGroup()
            .background(PanelDimScrim)
    ) {
        Spacer(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusProperties { canFocus = false }
        )
        Column(
            Modifier
                .width(380.dp)
                .fillMaxHeight()
                .padding(top = PanelEdgeInset, bottom = PanelEdgeInset, end = PanelEdgeInset)
                .clip(RoundedCornerShape(PanelCornerRadius))
                .background(PanelGlassFill)
                .padding(vertical = 20.dp)
                .focusGroup()
        ) {
            PanelHeader(
                title = when (page) {
                    Page.MAIN -> "Settings"
                    Page.SPEED -> "Playback speed"
                    Page.AUDIO -> "Audio"
                    Page.SLEEP -> "Sleep timer"
                }
            )
            Column(
                Modifier
                    .padding(horizontal = ContentInset)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                when (page) {
                    Page.MAIN -> {
                        NavRow("Subtitle delay", formatDelay(subtitleDelayMs), firstFocus, onClose = ::back, blockUp = true, showChevron = false, onClick = onAdjustSubtitleDelay)
                        NavRow("Playback speed", formatSpeed(playbackSpeed), null, onClose = ::back) { page = Page.SPEED }
                        NavRow("Audio", "Boost ${audioBoostLabel(audioBoost)} · Night ${nightModeLabel(nightMode)}", null, onClose = ::back) { page = Page.AUDIO }
                        NavRow("Sleep timer", sleepSummary(sleep), null, onClose = ::back) { page = Page.SLEEP }
                        NavRow(
                            "Playback info",
                            if (showStatsForNerds) "On" else "Off",
                            null,
                            onClose = ::back,
                            showChevron = false,
                            blockDown = !pipSupported
                        ) { onToggleStatsForNerds() }
                        if (pipSupported) {
                            NavRow("Enter Picture-in-Picture", "", null, onClose = ::back, blockDown = true, showChevron = false) {
                                onEnterPip()
                                back()
                            }
                        }
                    }
                    Page.SPEED -> {
                        Speeds.forEachIndexed { i, speed ->
                            SelectRow(
                                primary = formatSpeed(speed),
                                selected = kotlin.math.abs(speed - playbackSpeed) < 0.001f,
                                focusRequester = if (kotlin.math.abs(speed - playbackSpeed) < 0.001f) firstFocus else null,
                                onClick = {
                                    onSpeed(speed)
                                    page = Page.MAIN
                                },
                                onClose = ::back,
                                blockUp = i == 0,
                                blockDown = i == Speeds.lastIndex
                            )
                        }
                    }
                    Page.AUDIO -> {
                        StepRow(
                            label = "Boost",
                            value = audioBoostLabel(audioBoost),
                            focusRequester = firstFocus,
                            onLeft = { Boosts.getOrNull(audioBoost.ordinal - 1)?.let(onAudioBoost) },
                            onRight = { Boosts.getOrNull(audioBoost.ordinal + 1)?.let(onAudioBoost) },
                            onClose = ::back,
                            blockUp = true
                        )
                        StepRow(
                            label = "Night mode",
                            value = nightModeLabel(nightMode),
                            focusRequester = null,
                            onLeft = { NightModes.getOrNull(nightMode.ordinal - 1)?.let(onNightMode) },
                            onRight = { NightModes.getOrNull(nightMode.ordinal + 1)?.let(onNightMode) },
                            onClose = ::back,
                            blockDown = true
                        )
                    }
                    Page.SLEEP -> {
                        SleepModes.forEachIndexed { i, mode ->
                            SelectRow(
                                primary = sleepModeLabel(mode),
                                selected = mode == sleep.mode,
                                focusRequester = if (mode == sleep.mode) firstFocus else null,
                                onClick = {
                                    onSleep(mode)
                                    page = Page.MAIN
                                },
                                onClose = ::back,
                                blockUp = i == 0,
                                blockDown = i == SleepModes.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

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

/** Row that drills into a sub-page or triggers an action; shows a value + chevron. */
@Composable
private fun NavRow(
    label: String,
    value: String,
    focusRequester: FocusRequester?,
    onClose: () -> Unit,
    blockUp: Boolean = false,
    blockDown: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    RowFrame(focused, { focused = it }, focusRequester, blockUp, blockDown, onClose, { onClick() }) {
        Text(label, color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
        Text(value, color = if (focused) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (showChevron) {
                if (focused) Color.Black else Color.White.copy(alpha = 0.6f)
            } else {
                Color.Transparent
            }
        )
    }
}

/** Row that selects one value from a list (checkmark on the active one). */
@Composable
private fun SelectRow(
    primary: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onClose: () -> Unit,
    blockUp: Boolean,
    blockDown: Boolean
) {
    var focused by remember { mutableStateOf(false) }
    RowFrame(focused, { focused = it }, focusRequester, blockUp, blockDown, onClose, { onClick() }) {
        Text(primary, color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.55f))
        }
    }
}

/** Row whose value is cycled with left/right D-pad (no focus move). */
@Composable
private fun StepRow(
    label: String,
    value: String,
    focusRequester: FocusRequester?,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onClose: () -> Unit,
    blockUp: Boolean = false,
    blockDown: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val base = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Row(
        modifier = base
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCornerRadius))
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onLeft()
                        true
                    }
                    Key.DirectionRight -> {
                        onRight()
                        true
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
        Text(label, color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronLeft, contentDescription = "Decrease", tint = if (focused) Color.Black else Color.White.copy(alpha = 0.7f))
        Text(value, color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = "Increase", tint = if (focused) Color.Black else Color.White.copy(alpha = 0.7f))
    }
}

/** Shared focusable row container: white-on-focus, left-blocked, center/back handling. */
@Composable
private fun RowFrame(
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean,
    onClose: () -> Unit,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val base = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Row(
        modifier = base
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCornerRadius))
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .focusProperties {
                left = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onClick()
                        true
                    }
                    Key.Back -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = RowInnerPadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
