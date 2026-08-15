package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.playback.LanguagePickerRow
import app.picnic.player.data.playback.languagePickerRows
import app.picnic.player.data.playback.resolveLanguageCode
import app.picnic.player.data.playback.selectedLanguageRow
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors

private enum class LanguagePickerKind { AUDIO, SUBTITLE }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenSeerrDetail: ((app.picnic.player.data.seerr.SeerrMediaRequest) -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Settings shows the plain ocean wash — drop any backdrop left by a media screen.
    app.picnic.player.ui.ambient.PublishBackdrop(null)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val seerr by viewModel.seerrState.collectAsStateWithLifecycle()
    // ViewModel-owned so Requests stays selected after Detail/Seerr Detail pop
    // (rememberSaveable resets when this composable is disposed on push).
    val selected by viewModel.selectedCategory.collectAsStateWithLifecycle()

    // Requests is content-only and appears only while Seerr is linked; the
    // connection itself is managed under Account.
    val visibleCategories = remember(seerr.linkState) {
        if (seerr.linkState == SeerrLinkState.Linked) {
            SettingsCategory.entries.toList()
        } else {
            SettingsCategory.entries.filterNot { it == SettingsCategory.REQUESTS }
        }
    }

    // One requester per rail item so Back / D-pad Left can return focus to the category the user
    // came from. A single shared requester marks the detail panel's entry row (mirrors the
    // season↔episode pattern in SeriesEpisodesScreen).
    val categoryFocusRequesters = remember {
        SettingsCategory.entries.associateWith { FocusRequester() }
    }
    val detailEnterFr = remember { FocusRequester() }
    var detailHasFocus by remember { mutableStateOf(false) }

    var activePicker by remember { mutableStateOf<ActivePicker?>(null) }
    var languagePickerKind by remember { mutableStateOf<LanguagePickerKind?>(null) }
    val youtubeApps by viewModel.launcherApps.collectAsStateWithLifecycle()
    val cultureOptions by viewModel.cultureOptions.collectAsStateWithLifecycle()
    val serverAudioLanguage by viewModel.serverAudioLanguage.collectAsStateWithLifecycle()
    val serverSubtitleLanguage by viewModel.serverSubtitleLanguage.collectAsStateWithLifecycle()

    // Both of these must be declared before every sub-page return below: a return skips the
    // remember calls after it, and a skipped remember is dropped from the composition — the state
    // would be back to its initial value on the way in, not restored.
    //
    // The detail row to refocus once a full-screen sub-page pops back. The sub-page takes the
    // panel with it, so the row that opened it has to be named rather than remembered by the row.
    var restoreDetailRow by remember { mutableStateOf<SubPageRow?>(null) }

    // Scroll offset of the detail panel; without it a panel rebuilt at offset 0 visibly scrolls
    // down to the restored row. Keyed on category so switching categories still starts at the top.
    val detailScrollState = remember(selected) { ScrollState(0) }

    // Open source licenses render as a full-screen page over Settings (its own
    // focus/scroll and Back handling), not a dialog.
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        OpenSourceLicensesScreen(onBack = { showLicenses = false })
        return
    }

    // Subtitle appearance is a full-screen page too — its live preview needs the
    // whole panel, not a dialog.
    var showSubtitleAppearance by remember { mutableStateOf(false) }
    if (showSubtitleAppearance) {
        SubtitleAppearanceScreen(onBack = { showSubtitleAppearance = false })
        return
    }

    // First open → Account (default selected). After Detail pop with a pending request
    // restore, do not focus the rail — RequestsSettingsPanel seeds the card (or Requests
    // rail if empty). Incidental Account focus is ignored by selectCategory while pending.
    LaunchedEffect(Unit) {
        val restoreRequestRow =
            selected == SettingsCategory.REQUESTS && viewModel.focusedRequestId.value != null
        // A pending detail-row restore owns focus; seeding the rail here would steal it.
        if (!restoreRequestRow && restoreDetailRow == null) {
            runCatching { categoryFocusRequesters.getValue(selected).requestFocus() }
        }
    }

    // Back from the detail panel returns to the selected rail item; Back from the rail exits home.
    BackHandler {
        if (detailHasFocus) {
            runCatching { categoryFocusRequesters.getValue(selected).requestFocus() }
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 48.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = PicnicColors.OnDark
        )
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxSize()) {
            val updateViewModel: UpdateViewModel = hiltViewModel()
            val updateBadge by updateViewModel.updateAvailable.collectAsStateWithLifecycle()
            CategoryRail(
                categories = visibleCategories,
                selected = selected,
                onSelect = viewModel::selectCategory,
                focusRequesters = categoryFocusRequesters,
                detailEnterFr = detailEnterFr,
                badgedCategory = if (updateBadge) SettingsCategory.ABOUT else null,
                modifier = Modifier.fillMaxHeight().width(IntrinsicSize.Max)
            )
            Spacer(Modifier.width(48.dp))
            DetailPanel(
                category = selected,
                settings = settings,
                viewModel = viewModel,
                onSignedOut = onSignedOut,
                enterFr = detailEnterFr,
                leftFocus = categoryFocusRequesters.getValue(selected),
                onFocusChanged = { detailHasFocus = it },
                youtubeApps = youtubeApps,
                showPicker = { activePicker = it },
                onShowAudioLanguagePicker = { languagePickerKind = LanguagePickerKind.AUDIO },
                onShowSubtitleLanguagePicker = { languagePickerKind = LanguagePickerKind.SUBTITLE },
                restoreRow = restoreDetailRow,
                onRestored = { restoreDetailRow = null },
                scrollState = detailScrollState,
                onOpenSubtitleAppearance = {
                    restoreDetailRow = SubPageRow.SUBTITLE_APPEARANCE
                    showSubtitleAppearance = true
                },
                onOpenSeerrDetail = onOpenSeerrDetail,
                onOpenLicenses = {
                    restoreDetailRow = SubPageRow.LICENSES
                    showLicenses = true
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }

    languagePickerKind?.let { kind ->
        val appCode = when (kind) {
            LanguagePickerKind.AUDIO -> settings.preferredAudioLanguage
            LanguagePickerKind.SUBTITLE -> settings.preferredSubtitleLanguage
        }
        val serverCode = when (kind) {
            LanguagePickerKind.AUDIO -> serverAudioLanguage
            LanguagePickerKind.SUBTITLE -> serverSubtitleLanguage
        }
        val picker = remember(cultureOptions, kind) {
            languagePickerRows(
                base = cultureOptions,
                deviceLanguage = viewModel.deviceLanguage,
                includeDefaultAudioTrack = kind == LanguagePickerKind.AUDIO
            )
        }
        LanguagePreferenceDialog(
            title = when (kind) {
                LanguagePickerKind.AUDIO -> "Preferred audio language"
                LanguagePickerKind.SUBTITLE -> "Preferred subtitle language"
            },
            rows = picker.rows,
            separatorAfterIndex = picker.separatorAfterIndex,
            selectedRow = selectedLanguageRow(
                rows = picker.rows,
                // Check the language actually in effect (override → server → device) so the pinned
                // device row is highlighted on a fresh install with no local override.
                languageCode = resolveLanguageCode(appCode, serverCode, viewModel.deviceLanguage),
                preferDefaultAudioTrack = kind == LanguagePickerKind.AUDIO && settings.preferDefaultAudioTrack
            ),
            onSelect = { row ->
                when (row) {
                    LanguagePickerRow.DefaultAudioTrack -> viewModel.preferDefaultAudioTrack()
                    is LanguagePickerRow.Culture -> when (kind) {
                        LanguagePickerKind.AUDIO -> viewModel.setPreferredAudioLanguage(row.option.languageCode)
                        LanguagePickerKind.SUBTITLE -> viewModel.setPreferredSubtitleLanguage(row.option.languageCode)
                    }
                }
            },
            onDismiss = { languagePickerKind = null }
        )
    }

    activePicker?.let { picker ->
        OptionPickerDialog(
            title = picker.title,
            options = picker.options,
            onDismiss = { activePicker = null }
        )
    }
}

@Composable
private fun CategoryRail(
    categories: List<SettingsCategory>,
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    focusRequesters: Map<SettingsCategory, FocusRequester>,
    detailEnterFr: FocusRequester,
    badgedCategory: SettingsCategory? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categories.forEachIndexed { index, category ->
            CategoryRailItem(
                label = category.label,
                selected = category == selected,
                focusRequester = focusRequesters.getValue(category),
                detailEnterFr = detailEnterFr,
                isFirst = index == 0,
                isLast = index == categories.lastIndex,
                badge = category == badgedCategory,
                onFocused = { onSelect(category) }
            )
        }
    }
}

@Composable
private fun CategoryRailItem(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    detailEnterFr: FocusRequester,
    isFirst: Boolean,
    isLast: Boolean,
    badge: Boolean = false,
    onFocused: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val background = when {
        focused -> Color.White.copy(alpha = 0.16f)
        selected -> Color.White.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .focusRequester(focusRequester)
            // Rail is a column: Up/Down stop at the edges instead of escaping into
            // off-rail chrome; Right/Enter route into the detail panel's entry row,
            // falling back to spatial search when the panel has no marked entry.
            .focusProperties {
                if (isFirst) up = FocusRequester.Cancel
                if (isLast) down = FocusRequester.Cancel
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.DirectionRight -> {
                        runCatching { detailEnterFr.requestFocus() }
                            .onFailure { focusManager.moveFocus(FocusDirection.Right) }
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused || selected) PicnicColors.OnDark else PicnicColors.OnDarkMuted
        )
        if (badge) {
            Spacer(Modifier.width(8.dp))
            app.picnic.player.ui.browse.UpdateBadgeDot()
        }
    }
}

@Composable
private fun DetailPanel(
    category: SettingsCategory,
    settings: PlaybackSettings,
    viewModel: SettingsViewModel,
    onSignedOut: () -> Unit,
    enterFr: FocusRequester,
    leftFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    youtubeApps: List<YouTubeAppInfo>,
    showPicker: (ActivePicker) -> Unit,
    onShowAudioLanguagePicker: () -> Unit,
    onShowSubtitleLanguagePicker: () -> Unit,
    onOpenSubtitleAppearance: () -> Unit,
    onOpenSeerrDetail: ((app.picnic.player.data.seerr.SeerrMediaRequest) -> Unit)?,
    onOpenLicenses: () -> Unit,
    restoreRow: SubPageRow?,
    onRestored: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val imageCacheSize by viewModel.imageCacheSize.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (category) {
        SettingsCategory.REQUESTS -> {
            // The panel owns its scrolling — empty states centre in the full panel.
            RequestsSettingsPanel(
                viewModel = viewModel,
                enterFr = enterFr,
                leftFocus = leftFocus,
                onFocusChanged = onFocusChanged,
                onOpenSeerrDetail = onOpenSeerrDetail,
                modifier = modifier
            )
            return
        }
        SettingsCategory.ACCOUNT -> {
            AccountSettingsPanel(
                viewModel = viewModel,
                onSignedOut = onSignedOut,
                enterFr = enterFr,
                leftFocus = leftFocus,
                onFocusChanged = onFocusChanged,
                modifier = modifier.verticalScroll(rememberScrollState())
            )
            return
        }
        SettingsCategory.ABOUT -> {
            AboutSettingsPanel(
                enterFr = enterFr,
                leftFocus = leftFocus,
                onFocusChanged = onFocusChanged,
                onOpenLicenses = onOpenLicenses,
                restoreRow = restoreRow,
                onRestored = onRestored,
                modifier = modifier.verticalScroll(scrollState)
            )
            return
        }
        else -> Unit
    }

    val seerr by viewModel.seerrState.collectAsStateWithLifecycle()
    val serverAudioLanguage by viewModel.serverAudioLanguage.collectAsStateWithLifecycle()
    val serverSubtitleLanguage by viewModel.serverSubtitleLanguage.collectAsStateWithLifecycle()
    val sections = sectionsFor(
        category,
        settings,
        imageCacheSize,
        context,
        viewModel,
        youtubeApps,
        viewModel.pictureInPictureSupported,
        serverAudioLanguage,
        serverSubtitleLanguage,
        showPicker,
        onShowAudioLanguagePicker,
        onShowSubtitleLanguagePicker,
        onOpenSubtitleAppearance
    )
    val lastSection = sections.lastIndex
    // Focus target for a row returning from its full-screen sub-page.
    val restoreFr = remember { FocusRequester() }
    LaunchedEffect(restoreRow) {
        if (restoreRow != null) {
            restoreFr.requestFocusWhenAttached()
            onRestored()
        }
    }
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .onFocusChanged { onFocusChanged(it.hasFocus) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sections.forEachIndexed { si, section ->
            if (section.title != null) {
                SectionHeader(section.title, firstSection = si == 0)
            }
            section.items.forEachIndexed { ii, item ->
                SettingRow(
                    label = item.label,
                    value = item.value,
                    description = item.description,
                    enabled = item.enabled,
                    // The very first row is the entry target for D-pad Right / Enter from the rail;
                    // a row popping back from its sub-page takes precedence.
                    rowFocus = when {
                        item.subPage != null && item.subPage == restoreRow -> restoreFr
                        si == 0 && ii == 0 -> enterFr
                        else -> null
                    },
                    leftFocus = leftFocus,
                    blockUp = si == 0 && ii == 0,
                    blockDown = si == lastSection && ii == section.items.lastIndex,
                    onActivate = item.onActivate
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String, firstSection: Boolean) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = PicnicColors.OnDarkMuted,
        modifier = Modifier.padding(
            start = 20.dp,
            top = if (firstSection) 0.dp else 20.dp,
            bottom = 4.dp
        )
    )
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    description: String?,
    enabled: Boolean = true,
    rowFocus: FocusRequester?,
    leftFocus: FocusRequester,
    blockUp: Boolean,
    blockDown: Boolean,
    onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
            )
            .then(if (rowFocus != null) Modifier.focusRequester(rowFocus) else Modifier)
            // D-pad Left from any row returns to the selected rail item; Up/Down stop
            // at the panel edges instead of escaping into off-panel chrome. Nothing
            // sits to the right of a row, so Right is a dead end rather than an escape.
            .focusProperties {
                left = leftFocus
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .padding(horizontal = 20.dp, vertical = 16.dp)
            // Only Select activates — Right is a direction, not a second Select.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (enabled) onActivate()
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) PicnicColors.OnDark else PicnicColors.OnDarkMuted
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = PicnicColors.OnDarkMuted
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                !enabled -> PicnicColors.OnDarkMuted
                value.isEmpty() -> PicnicColors.OnDarkMuted
                else -> PicnicColors.Cyan
            }
        )
    }
}
