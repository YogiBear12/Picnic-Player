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
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SettingKeys
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors

private enum class LanguagePickerKind { AUDIO, SUBTITLE }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenSeerrDetail: ((app.picnic.player.data.seerr.SeerrMediaRequest) -> Unit)? = null,
    onOpenItem: (org.jellyfin.sdk.model.api.BaseItemDto, String?, String?) -> Unit = { _, _, _ -> },
    viewModel: SettingsViewModel = hiltViewModel()
) {
    app.picnic.player.ui.ambient.PublishBackdrop(null)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selected by viewModel.selectedCategory.collectAsStateWithLifecycle()

    val categoryFocusRequesters = remember {
        SettingsCategory.entries.associateWith { FocusRequester() }
    }
    val detailEnterFr = remember { FocusRequester() }
    var detailHasFocus by remember { mutableStateOf(false) }
    var holdSelection by remember { mutableStateOf(false) }
    var detailReturnFocus by remember(selected) { mutableStateOf<FocusRequester?>(null) }
    val enterDetail = {
        val stored = detailReturnFocus
        val landed = stored != null && runCatching { stored.requestFocus() }.isSuccess
        if (!landed) detailReturnFocus = null
        landed || runCatching { detailEnterFr.requestFocus() }.isSuccess
    }
    val panelFocus = remember(detailEnterFr, selected) {
        SettingsPanelFocus(
            enterFr = detailEnterFr,
            leftFocus = categoryFocusRequesters.getValue(selected),
            onFocusChanged = { detailHasFocus = it },
            onRowFocused = { detailReturnFocus = it },
            onHoldSelection = { holdSelection = it }
        )
    }

    var activePicker by remember { mutableStateOf<ActivePicker?>(null) }
    var languagePickerKind by remember { mutableStateOf<LanguagePickerKind?>(null) }
    val youtubeApps by viewModel.launcherApps.collectAsStateWithLifecycle()
    val cultureOptions by viewModel.cultureOptions.collectAsStateWithLifecycle()
    val serverAudioLanguage by viewModel.serverAudioLanguage.collectAsStateWithLifecycle()
    val serverSubtitleLanguage by viewModel.serverSubtitleLanguage.collectAsStateWithLifecycle()

    var restoreDetailRow by remember { mutableStateOf<SubPageRow?>(null) }

    val detailScrollState = remember(selected) { ScrollState(0) }

    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        OpenSourceLicensesScreen(onBack = { showLicenses = false })
        return
    }

    var showSubtitleAppearance by remember { mutableStateOf(false) }
    if (showSubtitleAppearance) {
        SubtitleAppearanceScreen(onBack = { showSubtitleAppearance = false })
        return
    }

    LaunchedEffect(Unit) {
        if (restoreDetailRow == null) {
            runCatching { categoryFocusRequesters.getValue(selected).requestFocus() }
        }
    }

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
                categories = SettingsCategory.entries,
                selected = selected,
                onSelect = { category -> if (!holdSelection) viewModel.selectCategory(category) },
                focusRequesters = categoryFocusRequesters,
                enterDetail = enterDetail,
                badgedCategory = if (updateBadge) SettingsCategory.ABOUT else null,
                modifier = Modifier.fillMaxHeight().width(IntrinsicSize.Max)
            )
            Spacer(Modifier.width(48.dp))
            DetailPanel(
                category = selected,
                settings = settings,
                viewModel = viewModel,
                onSignedOut = onSignedOut,
                focus = panelFocus,
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
                onOpenItem = onOpenItem,
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
                languageCode = resolveLanguageCode(appCode, serverCode, viewModel.deviceLanguage),
                preferDefaultAudioTrack = kind == LanguagePickerKind.AUDIO && settings.preferDefaultAudioTrack
            ),
            onSelect = { row ->
                when (row) {
                    LanguagePickerRow.DefaultAudioTrack -> viewModel.preferDefaultAudioTrack()
                    is LanguagePickerRow.Culture -> when (kind) {
                        LanguagePickerKind.AUDIO -> viewModel.setPreferredAudioLanguage(row.option.languageCode)
                        LanguagePickerKind.SUBTITLE -> viewModel.set(SettingKeys.PreferredSubtitleLanguage, row.option.languageCode)
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
    enterDetail: () -> Boolean,
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
                enterDetail = enterDetail,
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
    enterDetail: () -> Boolean,
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
            .focusProperties {
                if (isFirst) up = FocusRequester.Cancel
                if (isLast) down = FocusRequester.Cancel
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.DirectionRight -> {
                        if (!enterDetail()) focusManager.moveFocus(FocusDirection.Right)
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
    focus: SettingsPanelFocus,
    youtubeApps: List<YouTubeAppInfo>,
    showPicker: (ActivePicker) -> Unit,
    onShowAudioLanguagePicker: () -> Unit,
    onShowSubtitleLanguagePicker: () -> Unit,
    onOpenSubtitleAppearance: () -> Unit,
    onOpenSeerrDetail: ((app.picnic.player.data.seerr.SeerrMediaRequest) -> Unit)?,
    onOpenItem: (org.jellyfin.sdk.model.api.BaseItemDto, String?, String?) -> Unit,
    onOpenLicenses: () -> Unit,
    restoreRow: SubPageRow?,
    onRestored: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val imageCacheSize by viewModel.imageCacheSize.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (category) {
        SettingsCategory.ACCOUNT -> {
            AccountSettingsPanel(
                viewModel = viewModel,
                onSignedOut = onSignedOut,
                onOpenItem = onOpenItem,
                onOpenSeerrDetail = onOpenSeerrDetail,
                focus = focus,
                modifier = modifier
            )
            return
        }
        SettingsCategory.ABOUT -> {
            AboutSettingsPanel(
                focus = focus,
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
            .onFocusChanged { focus.onFocusChanged(it.hasFocus) },
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
                    enterOrRestoreFr = when {
                        item.subPage != null && item.subPage == restoreRow -> restoreFr
                        si == 0 && ii == 0 -> focus.enterFr
                        else -> null
                    },
                    leftFocus = focus.leftFocus,
                    blockUp = si == 0 && ii == 0,
                    blockDown = si == lastSection && ii == section.items.lastIndex,
                    onFocused = focus.onRowFocused,
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
    enterOrRestoreFr: FocusRequester?,
    leftFocus: FocusRequester,
    blockUp: Boolean,
    blockDown: Boolean,
    onFocused: (FocusRequester) -> Unit,
    onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
            )
            .focusRequester(rowFocus)
            .then(
                if (enterOrRestoreFr != null) {
                    Modifier.focusRequester(enterOrRestoreFr)
                } else {
                    Modifier
                }
            )
            .focusProperties {
                left = leftFocus
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .padding(horizontal = 20.dp, vertical = 16.dp)
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
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(rowFocus)
            }
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
