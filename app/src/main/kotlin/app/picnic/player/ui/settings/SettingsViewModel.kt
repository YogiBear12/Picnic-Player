package app.picnic.player.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.playback.CulturePickerOption
import app.picnic.player.data.playback.cultureDisplayName
import app.picnic.player.data.playback.culturePickerOptions
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SegmentAction
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.settings.ThemeMusicVolume
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

/**
 * Detail rows that open a full-screen sub-page. The page returns early from [SettingsScreen] and
 * takes the panel with it, so the row that opened it is named by one of these and refocused when
 * the page pops back.
 */
internal enum class SubPageRow { SUBTITLE_APPEARANCE, LICENSES }

data class YouTubeAppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val authRepository: AuthRepository,
    activeUserAvatar: app.picnic.player.data.auth.ActiveUserAvatar,
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

    /** Avatar of the active Jellyfin profile; shared with the nav drawer. */
    val activeUserImageUrl: StateFlow<String?> = activeUserAvatar.url

    private val _cultureOptions = MutableStateFlow<List<CulturePickerOption>>(emptyList())
    val cultureOptions: StateFlow<List<CulturePickerOption>> = _cultureOptions

    /** Server-side per-user language defaults; null until loaded, blank = user set none. */
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
     * Request row to restore focus to after Seerr/Jellyfin Detail pop.
     * Set on activate; cleared after restore completes, or on disconnect.
     * While non-null, [selectCategory] ignores non-REQUESTS switches so incidental
     * Account rail focus on Settings recompose cannot clear restore state.
     */
    private val _focusedRequestId = MutableStateFlow<Int?>(null)
    val focusedRequestId: StateFlow<Int?> = _focusedRequestId

    init {
        viewModelScope.launch {
            _activeUsername.value = authRepository.activeSession()?.username.orEmpty()
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
        // Pending restore: CategoryRailItem calls this on focus. After Detail
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

    /** Naming a language also drops the "Default" choice — the two are alternatives. */
    fun setPreferredAudioLanguage(code: String?) = viewModelScope.launch {
        store.setPreferredAudioLanguage(code)
        store.setPreferDefaultAudioTrack(false)
    }

    fun preferDefaultAudioTrack() = viewModelScope.launch {
        store.setPreferDefaultAudioTrack(true)
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
     * settled list before restoring focus — fire-and-forget raced Cancel→Back.
     */
    suspend fun refreshMyRequests() {
        val requests = runCatching { seerrRepository.myRequests() }.getOrDefault(emptyList())
        // Show rows immediately (inline/cache/placeholder), then fill titles.
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

    fun toggleAllowFourKTranscoding() = viewModelScope.launch {
        store.setAllowFourKTranscoding(!settings.value.allowFourKTranscoding)
    }

    companion object {
        val SKIP_FORWARD_OPTIONS = listOf(10, 15, 30, 45, 60)
        val SKIP_BACKWARD_OPTIONS = listOf(5, 10, 15, 30)
        val HIDE_CONTROLS_OPTIONS = listOf(2, 3, 5, 10)
        val NEXT_UP_COUNTDOWN_OPTIONS = listOf(0, 5, 10, 15)
    }
}
