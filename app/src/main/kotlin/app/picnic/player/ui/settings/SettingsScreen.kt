package app.picnic.player.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.format.Formatter
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.playback.CulturePickerOption
import app.picnic.player.data.playback.cultureDisplayName
import app.picnic.player.data.playback.culturePickerOptions
import app.picnic.player.data.playback.pinnedLanguageOptions
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.playback.quality.defaultQualityLabel
import app.picnic.player.data.playback.resolveLanguageCode
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SegmentAction
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.settings.ThemeMusicVolume
import app.picnic.player.ui.theme.PicnicColors
import app.picnic.player.util.LanguageDisplay
import coil3.imageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private enum class LanguagePickerKind { AUDIO, SUBTITLE }

data class YouTubeAppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val seerrRepository: app.picnic.player.data.seerr.SeerrRepository,
    private val pictureInPictureSupport: app.picnic.player.data.device.PictureInPictureSupport,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val settings: StateFlow<PlaybackSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettings())

    /** Hide PiP setting when the device does not advertise system PiP. */
    val pictureInPictureSupported: Boolean = pictureInPictureSupport.isSupported

    val seerrState = seerrRepository.state
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            app.picnic.player.data.seerr.SeerrSessionState()
        )

    private val _myRequests =
        MutableStateFlow<List<app.picnic.player.data.seerr.SeerrRequestDisplay>>(emptyList())
    val myRequests: StateFlow<List<app.picnic.player.data.seerr.SeerrRequestDisplay>> = _myRequests

    private val _seerrConnecting = MutableStateFlow(false)
    val seerrConnecting: StateFlow<Boolean> = _seerrConnecting

    private val _seerrConnectError = MutableStateFlow<String?>(null)
    val seerrConnectError: StateFlow<String?> = _seerrConnectError

    private val _activeUsername = MutableStateFlow("")
    val activeUsername: StateFlow<String> = _activeUsername

    /** Avatar of the active Jellyfin profile (tag-guarded when the tag is cached). */
    private val _activeUserImageUrl = MutableStateFlow<String?>(null)
    val activeUserImageUrl: StateFlow<String?> = _activeUserImageUrl

    private val _cultureOptions = MutableStateFlow<List<CulturePickerOption>>(emptyList())
    val cultureOptions: StateFlow<List<CulturePickerOption>> = _cultureOptions

    /** Server-side per-user language defaults (#149); null until loaded, blank = user set none. */
    private val _serverAudioLanguage = MutableStateFlow<String?>(null)
    val serverAudioLanguage: StateFlow<String?> = _serverAudioLanguage

    private val _serverSubtitleLanguage = MutableStateFlow<String?>(null)
    val serverSubtitleLanguage: StateFlow<String?> = _serverSubtitleLanguage

    /** Device locale language, pinned to the top of the language pickers. */
    val deviceLanguage: String = java.util.Locale.getDefault().language

    /** Survives Detail/Seerr Detail push while Settings stays on the back stack. */
    private val _selectedCategory = MutableStateFlow(SettingsCategory.EXPERIENCE)
    val selectedCategory: StateFlow<SettingsCategory> = _selectedCategory

    /**
     * Request row to restore focus to after Seerr/Jellyfin Detail pop (#43/#44).
     * Set on activate; cleared after restore completes, or on disconnect.
     * While non-null, [selectCategory] ignores non-REQUESTS switches so incidental
     * Account rail focus on Settings recompose cannot clear restore state.
     */
    private val _focusedRequestId = MutableStateFlow<Int?>(null)
    val focusedRequestId: StateFlow<Int?> = _focusedRequestId

    init {
        viewModelScope.launch {
            val session = authRepository.activeSession() ?: return@launch
            _activeUsername.value = session.username
            val tag = authRepository.cachedPublicUsers(session.server.id)
                .firstOrNull { it.id == session.userId }
                ?.primaryImageTag
            val base = session.server.baseUrl.trimEnd('/') +
                "/Users/${session.userId}/Images/Primary?fillWidth=256&quality=90"
            _activeUserImageUrl.value = if (tag != null) "$base&tag=$tag" else base
        }
        viewModelScope.launch {
            val session = authRepository.activeSession() ?: return@launch
            val cultures = runCatching { mediaRepository.cultures(session) }.getOrDefault(emptyList())
            if (cultures.isNotEmpty()) {
                _cultureOptions.value = culturePickerOptions(cultures)
            }
            val config = runCatching { mediaRepository.userConfiguration(session) }.getOrNull()
            _serverAudioLanguage.value = config?.audioLanguagePreference
            _serverSubtitleLanguage.value = config?.subtitleLanguagePreference
        }
        // The Requests category exists only while Seerr is linked — losing the link
        // while it is selected must not leave the detail panel orphaned.
        viewModelScope.launch {
            seerrState.collect { state ->
                if (state.linkState != SeerrLinkState.Linked &&
                    _selectedCategory.value == SettingsCategory.REQUESTS
                ) {
                    _selectedCategory.value = SettingsCategory.EXPERIENCE
                }
            }
        }
    }

    fun rememberFocusedRequest(id: Int) {
        _focusedRequestId.value = id
    }

    fun clearFocusedRequest() {
        _focusedRequestId.value = null
    }

    fun selectCategory(category: SettingsCategory) {
        // Pending restore (#44): CategoryRailItem calls this on focus. After Detail
        // pop, Account (entries.first) often gets brief focus before the Requests
        // panel seeds — must not switch category or clear focusedRequestId.
        if (_focusedRequestId.value != null && category != SettingsCategory.REQUESTS) {
            return
        }
        if (category != SettingsCategory.REQUESTS) {
            _focusedRequestId.value = null
        }
        _selectedCategory.value = category
    }

    fun languageLabel(code: String): String = cultureDisplayName(
        languageCode = code,
        options = _cultureOptions.value,
        fallback = { LanguageDisplay.name(it) }
    )

    fun setPreferredAudioLanguage(code: String?) = viewModelScope.launch {
        store.setPreferredAudioLanguage(code)
    }

    fun setPreferredSubtitleLanguage(code: String?) = viewModelScope.launch {
        store.setPreferredSubtitleLanguage(code)
    }

    fun toggleAlwaysDisplaySubtitles() = viewModelScope.launch {
        store.setAlwaysDisplaySubtitles(!settings.value.alwaysDisplaySubtitles)
    }

    fun signOut(onDone: () -> Unit) = viewModelScope.launch {
        authRepository.signOut()
        onDone()
    }

    fun connectSeerr(url: String, password: String) = viewModelScope.launch {
        _seerrConnecting.value = true
        _seerrConnectError.value = null
        seerrRepository.connect(url, password)
            .onSuccess {
                refreshMyRequests()
            }
            .onFailure { e ->
                _seerrConnectError.value = e.message ?: "Could not connect to Seerr"
            }
        _seerrConnecting.value = false
    }

    fun disconnectSeerr() = viewModelScope.launch {
        seerrRepository.disconnect()
        _myRequests.value = emptyList()
        _focusedRequestId.value = null
    }

    /**
     * Reloads Your requests. Suspend so callers (esp. focus seed-on-return) can await a
     * settled list before restoring focus — fire-and-forget raced Cancel→Back (#44 leftover).
     */
    suspend fun refreshMyRequests() {
        val requests = runCatching { seerrRepository.myRequests() }.getOrDefault(emptyList())
        // Show rows immediately (inline/cache/placeholder), then fill titles (#44).
        _myRequests.value = seerrRepository.requestDisplaysCached(requests)
        _myRequests.value = runCatching {
            seerrRepository.hydrateRequestDisplays(requests)
        }.getOrDefault(_myRequests.value)
    }

    fun canCancelRequest(request: app.picnic.player.data.seerr.SeerrMediaRequest): Boolean = seerrRepository.canCancel(seerrState.value.user, request)

    fun cancelRequest(request: app.picnic.player.data.seerr.SeerrMediaRequest) = viewModelScope.launch {
        runCatching { seerrRepository.cancelRequest(request.id) }
        refreshMyRequests()
    }

    private val _imageCacheSize = MutableStateFlow(context.imageLoader.diskCache?.size ?: 0L)
    val imageCacheSize: StateFlow<Long> = _imageCacheSize

    fun clearImageCache() {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
        _imageCacheSize.value = context.imageLoader.diskCache?.size ?: 0L
    }

    fun setSkipForwardSeconds(seconds: Int) = viewModelScope.launch { store.setSkipForwardSeconds(seconds) }
    fun setSkipBackwardSeconds(seconds: Int) = viewModelScope.launch { store.setSkipBackwardSeconds(seconds) }
    fun setOsdHideSeconds(seconds: Int) = viewModelScope.launch { store.setOsdHideSeconds(seconds) }
    fun setNextUpCountdownSeconds(seconds: Int) = viewModelScope.launch { store.setNextUpCountdownSeconds(seconds) }
    fun setThemeMusicVolume(volume: ThemeMusicVolume) = viewModelScope.launch { store.setThemeMusicVolume(volume) }
    fun setIntroAction(action: SegmentAction) = viewModelScope.launch { store.setIntroAction(action) }
    fun setRecapAction(action: SegmentAction) = viewModelScope.launch { store.setRecapAction(action) }
    fun setOutroAction(action: SegmentAction) = viewModelScope.launch { store.setOutroAction(action) }
    fun setPreviewAction(action: SegmentAction) = viewModelScope.launch { store.setPreviewAction(action) }
    fun setCommercialAction(action: SegmentAction) = viewModelScope.launch { store.setCommercialAction(action) }
    fun setDefaultVideoQuality(rung: QualityRung?) = viewModelScope.launch { store.setDefaultVideoQuality(rung) }

    fun toggleClickToPause() = viewModelScope.launch {
        store.setClickToPause(!settings.value.clickToPause)
    }

    fun toggleColouredFocus() = viewModelScope.launch {
        store.setColouredFocus(!settings.value.colouredFocus)
    }

    fun togglePulseFocusGlow() = viewModelScope.launch {
        store.setPulseFocusGlow(!settings.value.pulseFocusGlow)
    }

    fun toggleAmbientBackgrounds() = viewModelScope.launch {
        store.setAmbientBackgrounds(!settings.value.ambientBackgrounds)
    }

    fun toggleCapBadgeCount() = viewModelScope.launch {
        store.setCapBadgeCount(!settings.value.capBadgeCount)
    }

    fun setTrailerYouTubePackage(packageName: String?) = viewModelScope.launch {
        store.setTrailerYouTubePackage(packageName)
    }

    /**
     * Launcher apps for the trailers app picker. Queried lazily off the main
     * thread — loading label + icon for every installed app is not cheap, and this was
     * previously done synchronously at first composition.
     */
    val launcherApps: StateFlow<List<YouTubeAppInfo>> =
        flow { emit(queryLauncherApps()) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun queryLauncherApps(): List<YouTubeAppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)

        return resolveInfos.map { info ->
            YouTubeAppInfo(
                packageName = info.activityInfo.packageName,
                name = info.loadLabel(context.packageManager).toString(),
                icon = info.loadIcon(context.packageManager)
            )
        }.distinctBy { it.packageName }
    }

    fun toggleDisplayNextUpDuringOutro() = viewModelScope.launch {
        store.setDisplayNextUpDuringOutro(!settings.value.displayNextUpDuringOutro)
    }

    fun toggleAutoLoginLastUser() = viewModelScope.launch {
        store.setAutoLoginLastUser(!settings.value.autoLoginLastUser)
    }

    fun togglePictureInPicture() = viewModelScope.launch {
        store.setPictureInPicture(!settings.value.pictureInPicture)
    }

    fun toggleMatchRefreshRate() = viewModelScope.launch {
        store.setMatchRefreshRate(!settings.value.matchRefreshRate)
    }

    fun toggleMatchResolution() = viewModelScope.launch {
        store.setMatchResolution(!settings.value.matchResolution)
    }

    fun toggleDownmixStereo() = viewModelScope.launch {
        store.setDownmixStereo(!settings.value.downmixStereo)
    }

    fun toggleForceDoviProfile7() = viewModelScope.launch {
        store.setForceDoviProfile7(!settings.value.forceDoviProfile7)
    }

    fun toggleForceDirectPlay() = viewModelScope.launch {
        store.setForceDirectPlay(!settings.value.forceDirectPlay)
    }

    companion object {
        val SKIP_FORWARD_OPTIONS = listOf(10, 15, 30, 45, 60)
        val SKIP_BACKWARD_OPTIONS = listOf(5, 10, 15, 30)
        val HIDE_CONTROLS_OPTIONS = listOf(2, 3, 5, 10)
        val NEXT_UP_COUNTDOWN_OPTIONS = listOf(0, 5, 10, 15)
    }
}

/** Top-level settings categories shown in the master rail. */
enum class SettingsCategory(val label: String) {
    REQUESTS("Requests"),
    EXPERIENCE("Experience"),
    PLAYBACK("Playback"),
    ADVANCED("Advanced"),
    ACCOUNT("Account"),
    ABOUT("About")
}

/** One tunable setting rendered in the detail panel. [description] shows a muted
 *  helper line under the label; null hides it. */
private data class SettingItem(
    val label: String,
    val value: String,
    val description: String? = null,
    val onActivate: () -> Unit,
    /** When false the row is greyed out and cannot be activated (still focusable). */
    val enabled: Boolean = true
)

/** A group of rows within a category's detail panel. [title] draws a section
 *  header above the rows; null renders the rows with no header. */
private data class SettingSection(
    val title: String?,
    val items: List<SettingItem>
)

/** An open multi-option picker (standard glass dialog). */
private data class ActivePicker(
    val title: String,
    val options: List<PickerOption>
)

private fun ThemeMusicVolume.display(): String = when (this) {
    ThemeMusicVolume.DISABLED -> "Off"
    ThemeMusicVolume.QUIET -> "Quiet"
    ThemeMusicVolume.LOW -> "Low"
    ThemeMusicVolume.MEDIUM -> "Medium"
    ThemeMusicVolume.HIGH -> "High"
    ThemeMusicVolume.LOUD -> "Loud"
}

private fun SegmentAction.display(): String = when (this) {
    SegmentAction.ASK_TO_SKIP -> "Ask to skip"
    SegmentAction.SKIP_AUTOMATICALLY -> "Skip automatically"
    SegmentAction.DO_NOT_SKIP -> "Do not skip"
}

private fun secondsPicker(
    title: String,
    options: List<Int>,
    current: Int,
    onSelect: (Int) -> Unit
) = ActivePicker(
    title = title,
    options = options.map { s ->
        PickerOption(label = "${s}s", selected = s == current, onSelect = { onSelect(s) })
    }
)

private fun defaultQualityPicker(
    current: QualityRung?,
    onSelect: (QualityRung?) -> Unit
) = ActivePicker(
    title = "Default video quality",
    options = (listOf(null) + QualityRung.entries).map { quality ->
        PickerOption(
            label = defaultQualityLabel(quality),
            selected = quality == current,
            onSelect = { onSelect(quality) }
        )
    }
)

private fun segmentPicker(
    title: String,
    current: SegmentAction,
    onSelect: (SegmentAction) -> Unit
) = ActivePicker(
    title = title,
    options = SegmentAction.entries.map { action ->
        PickerOption(label = action.display(), selected = action == current, onSelect = { onSelect(action) })
    }
)

private fun sectionsFor(
    category: SettingsCategory,
    settings: PlaybackSettings,
    imageCacheSize: Long,
    context: Context,
    viewModel: SettingsViewModel,
    youtubeApps: List<YouTubeAppInfo>,
    pictureInPictureSupported: Boolean,
    serverAudioLanguage: String?,
    serverSubtitleLanguage: String?,
    showPicker: (ActivePicker) -> Unit,
    onShowAudioLanguagePicker: () -> Unit,
    onShowSubtitleLanguagePicker: () -> Unit,
    onOpenSubtitleAppearance: () -> Unit
): List<SettingSection> = when (category) {
    // Account, Requests and About render dedicated panels, not generic rows.
    SettingsCategory.ACCOUNT, SettingsCategory.REQUESTS, SettingsCategory.ABOUT -> emptyList()
    SettingsCategory.EXPERIENCE -> listOf(
        SettingSection(
            null,
            buildList {
                add(
                    SettingItem(
                        "Sign in automatically",
                        if (settings.autoLoginLastUser) "On" else "Off",
                        "Skip the profile picker and sign in as the last-used profile",
                        viewModel::toggleAutoLoginLastUser
                    )
                )
                add(
                    SettingItem(
                        "Ambient backgrounds",
                        if (settings.ambientBackgrounds) "On" else "Off",
                        "Tint the background with colors extracted from the backdrop",
                        viewModel::toggleAmbientBackgrounds
                    )
                )
                add(
                    SettingItem(
                        "Ambient focus indicators",
                        if (settings.colouredFocus) "On" else "Off",
                        "Focus indicators dynamically change colors based on the focused card",
                        viewModel::toggleColouredFocus
                    )
                )
                add(
                    SettingItem(
                        "Live focus indicators",
                        if (settings.pulseFocusGlow) "On" else "Off",
                        "The glow from focus indicators gently pulse behind the card",
                        viewModel::togglePulseFocusGlow
                    )
                )
                add(
                    SettingItem(
                        "Limit episode badges",
                        if (settings.capBadgeCount) "On" else "Off",
                        "Unwatched episode counts are displayed as 99+ when the count exceeds 100",
                        viewModel::toggleCapBadgeCount
                    )
                )
                add(
                    SettingItem(
                        "Theme music",
                        settings.themeMusicVolume.display(),
                        "Play an item's theme music while browsing its details",
                        onActivate = {
                            showPicker(
                                ActivePicker(
                                    title = "Theme music",
                                    options = ThemeMusicVolume.entries.map { volume ->
                                        PickerOption(
                                            label = volume.display(),
                                            selected = volume == settings.themeMusicVolume,
                                            onSelect = { viewModel.setThemeMusicVolume(volume) }
                                        )
                                    }
                                )
                            )
                        }
                    )
                )
            }
        )
    )
    SettingsCategory.PLAYBACK -> listOf(
        SettingSection(
            "Languages",
            listOf(
                SettingItem(
                    "Preferred audio language",
                    viewModel.languageLabel(
                        resolveLanguageCode(
                            settings.preferredAudioLanguage,
                            serverAudioLanguage,
                            viewModel.deviceLanguage
                        )
                    ),
                    onActivate = onShowAudioLanguagePicker
                ),
                SettingItem(
                    "Preferred subtitle language",
                    viewModel.languageLabel(
                        resolveLanguageCode(
                            settings.preferredSubtitleLanguage,
                            serverSubtitleLanguage,
                            viewModel.deviceLanguage
                        )
                    ),
                    onActivate = onShowSubtitleLanguagePicker
                ),
                SettingItem(
                    "Always display subtitles",
                    if (settings.alwaysDisplaySubtitles) "On" else "Off",
                    onActivate = viewModel::toggleAlwaysDisplaySubtitles
                ),
                SettingItem(
                    "Subtitle appearance",
                    "",
                    onActivate = onOpenSubtitleAppearance
                )
            )
        ),
        SettingSection(
            "Controls",
            buildList {
                add(
                    SettingItem(
                        "Skip forward",
                        "${settings.skipForwardSeconds}s",
                        onActivate = {
                            showPicker(
                                secondsPicker(
                                    "Skip forward",
                                    SettingsViewModel.SKIP_FORWARD_OPTIONS,
                                    settings.skipForwardSeconds,
                                    viewModel::setSkipForwardSeconds
                                )
                            )
                        }
                    )
                )
                add(
                    SettingItem(
                        "Skip back",
                        "${settings.skipBackwardSeconds}s",
                        onActivate = {
                            showPicker(
                                secondsPicker(
                                    "Skip back",
                                    SettingsViewModel.SKIP_BACKWARD_OPTIONS,
                                    settings.skipBackwardSeconds,
                                    viewModel::setSkipBackwardSeconds
                                )
                            )
                        }
                    )
                )
                add(
                    SettingItem(
                        "Hide playback controls",
                        "${settings.osdHideSeconds}s",
                        onActivate = {
                            showPicker(
                                secondsPicker(
                                    "Hide playback controls",
                                    SettingsViewModel.HIDE_CONTROLS_OPTIONS,
                                    settings.osdHideSeconds,
                                    viewModel::setOsdHideSeconds
                                )
                            )
                        }
                    )
                )
                if (pictureInPictureSupported) {
                    add(
                        SettingItem(
                            "Enable Picture-in-Picture",
                            if (settings.pictureInPicture) "On" else "Off",
                            onActivate = viewModel::togglePictureInPicture
                        )
                    )
                }
            }
        ),
        SettingSection(
            "Next up behavior",
            listOf(
                SettingItem(
                    "Display next up during outro",
                    if (settings.displayNextUpDuringOutro) "On" else "Off",
                    onActivate = viewModel::toggleDisplayNextUpDuringOutro
                ),
                SettingItem(
                    "Next up countdown",
                    "${settings.nextUpCountdownSeconds}s",
                    onActivate = {
                        showPicker(
                            secondsPicker(
                                "Next up countdown",
                                SettingsViewModel.NEXT_UP_COUNTDOWN_OPTIONS,
                                settings.nextUpCountdownSeconds,
                                viewModel::setNextUpCountdownSeconds
                            )
                        )
                    }
                )
            )
        ),
        SettingSection(
            "Media Segments",
            listOf(
                SettingItem(
                    "Intros",
                    settings.introAction.display(),
                    onActivate = { showPicker(segmentPicker("Intros", settings.introAction, viewModel::setIntroAction)) }
                ),
                SettingItem(
                    "Recaps",
                    settings.recapAction.display(),
                    onActivate = { showPicker(segmentPicker("Recaps", settings.recapAction, viewModel::setRecapAction)) }
                ),
                SettingItem(
                    "Outros",
                    settings.outroAction.display(),
                    onActivate = { showPicker(segmentPicker("Outros", settings.outroAction, viewModel::setOutroAction)) }
                ),
                SettingItem(
                    "Previews",
                    settings.previewAction.display(),
                    onActivate = {
                        showPicker(segmentPicker("Previews", settings.previewAction, viewModel::setPreviewAction))
                    }
                ),
                SettingItem(
                    "Commercials",
                    settings.commercialAction.display(),
                    onActivate = {
                        showPicker(
                            segmentPicker("Commercials", settings.commercialAction, viewModel::setCommercialAction)
                        )
                    }
                )
            )
        )
    )
    SettingsCategory.ADVANCED -> listOf(
        SettingSection(
            null,
            listOf(
                SettingItem(
                    "Default video quality",
                    defaultQualityLabel(settings.defaultVideoQuality),
                    onActivate = {
                        showPicker(
                            defaultQualityPicker(settings.defaultVideoQuality, viewModel::setDefaultVideoQuality)
                        )
                    }
                ),
                SettingItem(
                    "Refresh rate switching",
                    if (settings.matchRefreshRate) "On" else "Off",
                    onActivate = viewModel::toggleMatchRefreshRate
                ),
                SettingItem(
                    "Resolution switching",
                    if (settings.matchResolution) "On" else "Off",
                    onActivate = viewModel::toggleMatchResolution
                ),
                SettingItem(
                    "Force direct play",
                    if (settings.forceDirectPlay) "On" else "Off",
                    onActivate = viewModel::toggleForceDirectPlay
                ),
                SettingItem(
                    "Downmix to stereo",
                    if (settings.downmixStereo) "On" else "Off",
                    // Force direct play announces full compatibility, so this has no effect.
                    enabled = !settings.forceDirectPlay,
                    onActivate = viewModel::toggleDownmixStereo
                ),
                SettingItem(
                    "Force DoVi Profile 7 support",
                    if (settings.forceDoviProfile7) "On" else "Off",
                    enabled = !settings.forceDirectPlay,
                    onActivate = viewModel::toggleForceDoviProfile7
                ),
                SettingItem(
                    "External application for trailers",
                    youtubeApps.find { it.packageName == settings.trailerYouTubePackage }?.name ?: "System Default",
                    onActivate = {
                        showPicker(
                            ActivePicker(
                                title = "External application for trailers",
                                options = buildList {
                                    add(
                                        PickerOption(
                                            label = "System Default",
                                            selected = settings.trailerYouTubePackage == null,
                                            onSelect = { viewModel.setTrailerYouTubePackage(null) }
                                        )
                                    )
                                    youtubeApps.forEach { app ->
                                        add(
                                            PickerOption(
                                                label = app.name,
                                                selected = app.packageName == settings.trailerYouTubePackage,
                                                icon = app.icon,
                                                onSelect = { viewModel.setTrailerYouTubePackage(app.packageName) }
                                            )
                                        )
                                    }
                                }
                            )
                        )
                    }
                ),
                SettingItem(
                    "Clear image cache",
                    Formatter.formatFileSize(context, imageCacheSize),
                    onActivate = viewModel::clearImageCache
                )
            )
        )
    )
}

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
        if (!restoreRequestRow) {
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
                onOpenSubtitleAppearance = { showSubtitleAppearance = true },
                onOpenSeerrDetail = onOpenSeerrDetail,
                onOpenLicenses = { showLicenses = true },
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
        val picker = remember(cultureOptions) {
            pinnedLanguageOptions(cultureOptions, viewModel.deviceLanguage)
        }
        LanguagePreferenceDialog(
            title = when (kind) {
                LanguagePickerKind.AUDIO -> "Preferred audio language"
                LanguagePickerKind.SUBTITLE -> "Preferred subtitle language"
            },
            options = picker.options,
            separatorAfterIndex = picker.separatorAfterIndex,
            // Check the language actually in effect (override → server → device) so the pinned
            // device row is highlighted on a fresh install with no local override.
            selectedLanguageCode = resolveLanguageCode(appCode, serverCode, viewModel.deviceLanguage),
            onSelect = { code ->
                when (kind) {
                    LanguagePickerKind.AUDIO -> viewModel.setPreferredAudioLanguage(code)
                    LanguagePickerKind.SUBTITLE -> viewModel.setPreferredSubtitleLanguage(code)
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
                modifier = modifier.verticalScroll(rememberScrollState())
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
    // Keyed on category: switching categories starts the new panel at the top
    // instead of inheriting the previous panel's scroll offset.
    val scrollState = remember(category) { ScrollState(0) }
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
                    // The very first row is the entry target for D-pad Right / Enter from the rail.
                    rowFocus = if (si == 0 && ii == 0) enterFr else null,
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
            // Only Select activates — Right is a direction, not a second Select (#138).
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
