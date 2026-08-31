package app.picnic.player.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.WatchStats
import app.picnic.player.data.media.WatchStatsCache
import app.picnic.player.data.playback.CulturePickerOption
import app.picnic.player.data.playback.cultureDisplayName
import app.picnic.player.data.playback.culturePickerOptions
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SettingKey
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.util.LanguageDisplay
import coil3.imageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val watchStatsCache: WatchStatsCache,
    private val changeBus: LibraryChangeBus,
    private val pictureInPictureSupport: app.picnic.player.data.device.PictureInPictureSupport,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val settings: StateFlow<PlaybackSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettings())

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

    val activeUserImageUrl: StateFlow<String?> = activeUserAvatar.url

    private val _cultureOptions = MutableStateFlow<List<CulturePickerOption>>(emptyList())
    val cultureOptions: StateFlow<List<CulturePickerOption>> = _cultureOptions

    private val _serverAudioLanguage = MutableStateFlow<String?>(null)
    val serverAudioLanguage: StateFlow<String?> = _serverAudioLanguage

    private val _serverSubtitleLanguage = MutableStateFlow<String?>(null)
    val serverSubtitleLanguage: StateFlow<String?> = _serverSubtitleLanguage

    val deviceLanguage: String = java.util.Locale.getDefault().language

    private val _activeUserId = MutableStateFlow<java.util.UUID?>(null)

    private val accountRefresh = MutableStateFlow(0)
    private var accountOpened = false
    private var lastFavorites: List<org.jellyfin.sdk.model.api.BaseItemDto> = emptyList()

    fun onAccountOpened() {
        if (accountOpened) refreshAccount()
        accountOpened = true
    }

    fun refreshAccount() {
        accountRefresh.value++
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val watchStats: StateFlow<WatchStats?> = combine(
        _activeUserId.filterNotNull(),
        accountRefresh
    ) { userId, _ -> userId }
        .flatMapLatest { userId ->
            watchStatsCache.statsFor(userId)
                .onStart { viewModelScope.launch { watchStatsCache.refresh(userId) } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favorites: StateFlow<List<org.jellyfin.sdk.model.api.BaseItemDto>?> = accountRefresh
        .mapLatest { loadFavorites() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private suspend fun loadFavorites(): List<org.jellyfin.sdk.model.api.BaseItemDto> = try {
        mediaRepository.favoriteItems()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w(ACCOUNT_LOG_TAG, "favorites load failed", failure)
        lastFavorites
    }.also { lastFavorites = it }

    private val _selectedCategory = MutableStateFlow(SettingsCategory.ACCOUNT)
    val selectedCategory: StateFlow<SettingsCategory> = _selectedCategory

    init {
        viewModelScope.launch {
            val session = authRepository.activeSession()
            _activeUsername.value = session?.username.orEmpty()
            _activeUserId.value = session?.userUuid
        }
        viewModelScope.launch {
            authRepository.activeSession() ?: return@launch
            val cultures = runCatching { mediaRepository.cultures() }.getOrDefault(emptyList())
            if (cultures.isNotEmpty()) {
                _cultureOptions.value = culturePickerOptions(cultures)
            }
            val config = runCatching { mediaRepository.userConfiguration() }.getOrNull()
            _serverAudioLanguage.value = config?.audioLanguagePreference
            _serverSubtitleLanguage.value = config?.subtitleLanguagePreference
        }
        viewModelScope.launch {
            changeBus.changes().debounce(CHANGE_DEBOUNCE_MS).collectLatest { refreshAccount() }
        }
        viewModelScope.launch {
            authRepository.activeSession() ?: return@launch
            if (seerrRepository.state.value.linkState ==
                app.picnic.player.data.seerr.SeerrLinkState.Linked
            ) {
                refreshMyRequests()
            }
        }
    }

    fun selectCategory(category: SettingsCategory) {
        _selectedCategory.value = category
    }

    fun languageLabel(code: String): String = cultureDisplayName(
        languageCode = code,
        options = _cultureOptions.value,
        fallback = { LanguageDisplay.name(it) }
    )

    fun setPreferredAudioLanguage(code: String?) = viewModelScope.launch {
        store.setPreferredAudioLanguage(code)
        store.setPreferDefaultAudioTrack(false)
    }

    fun preferDefaultAudioTrack() = viewModelScope.launch {
        store.setPreferDefaultAudioTrack(true)
    }

    fun <T> set(setting: SettingKey<T>, value: T) = viewModelScope.launch { setting.write(store, value) }

    fun toggle(setting: SettingKey<Boolean>) = viewModelScope.launch {
        setting.write(store, !setting.read(settings.value))
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
    }

    suspend fun refreshMyRequests() {
        val requests = runCatching { seerrRepository.myRequests() }.getOrDefault(emptyList())
        _myRequests.value = seerrRepository.requestDisplaysCached(requests)
        _myRequests.value = runCatching {
            seerrRepository.hydrateRequestDisplays(requests)
        }.getOrDefault(_myRequests.value)
    }

    private val _imageCacheSize = MutableStateFlow(context.imageLoader.diskCache?.size ?: 0L)
    val imageCacheSize: StateFlow<Long> = _imageCacheSize

    fun clearImageCache() {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
        _imageCacheSize.value = context.imageLoader.diskCache?.size ?: 0L
    }

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

    companion object {
        private const val ACCOUNT_LOG_TAG = "PicnicAccount"
        private const val CHANGE_DEBOUNCE_MS = 400L

        val SKIP_FORWARD_OPTIONS = listOf(10, 15, 30, 45, 60)
        val SKIP_BACKWARD_OPTIONS = listOf(5, 10, 15, 30)
        val HIDE_CONTROLS_OPTIONS = listOf(2, 3, 5, 10)
        val NEXT_UP_COUNTDOWN_OPTIONS = listOf(0, 5, 10, 15)
    }
}
