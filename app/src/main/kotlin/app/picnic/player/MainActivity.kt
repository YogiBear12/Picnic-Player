package app.picnic.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.socket.RemoteControlAction
import app.picnic.player.data.socket.RemoteControlBus
import app.picnic.player.data.socket.RemoteKey
import app.picnic.player.data.socket.ServerMessageBus
import app.picnic.player.playback.LocalThemeMusicPlayer
import app.picnic.player.playback.ThemeMusicPlayer
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.ambient.AmbientPrewarmer
import app.picnic.player.ui.ambient.BackdropController
import app.picnic.player.ui.ambient.LocalAmbientBackgrounds
import app.picnic.player.ui.ambient.LocalAmbientPaletteLoader
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.LocalBackdropController
import app.picnic.player.ui.ambient.LocalPulseFocusGlow
import app.picnic.player.ui.common.ServerNoticeHost
import app.picnic.player.ui.navigation.AppNavigationViewModel
import app.picnic.player.ui.navigation.BrowseKey
import app.picnic.player.ui.navigation.DetailKey
import app.picnic.player.ui.navigation.EpisodesKey
import app.picnic.player.ui.navigation.PicnicNavHost
import app.picnic.player.ui.navigation.PlayerKey
import app.picnic.player.ui.navigation.SettingsKey
import app.picnic.player.ui.navigation.StartupKey
import app.picnic.player.ui.theme.PicnicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val navViewModel: AppNavigationViewModel by viewModels()

    @Inject lateinit var ambientLoader: AmbientPaletteLoader

    @Inject lateinit var ambientPrewarmer: AmbientPrewarmer

    @Inject lateinit var backdropController: BackdropController

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var themeMusicPlayer: ThemeMusicPlayer

    @Inject lateinit var serverMessageBus: ServerMessageBus

    @Inject lateinit var remoteControlBus: RemoteControlBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        observeRemoteControl()

        // Draw edge-to-edge so the IME reports as a Compose inset: screens apply
        // imePadding() to lift content above the keyboard while the root Surface
        // still paints the full window (no black gap behind the keyboard).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PicnicTheme {
                // Reading these locals toggles chrome live (no restart/nav reset).
                val ambientBackgrounds by remember {
                    settingsStore.settings.map { it.ambientBackgrounds }
                }.collectAsStateWithLifecycle(initialValue = true)
                val pulseFocusGlow by remember {
                    settingsStore.settings.map { it.pulseFocusGlow }
                }.collectAsStateWithLifecycle(initialValue = true)
                CompositionLocalProvider(
                    LocalAmbientPaletteLoader provides ambientLoader,
                    LocalAmbientPrewarmer provides ambientPrewarmer,
                    LocalAmbientBackgrounds provides ambientBackgrounds,
                    LocalPulseFocusGlow provides pulseFocusGlow,
                    LocalBackdropController provides backdropController,
                    LocalThemeMusicPlayer provides themeMusicPlayer
                ) {
                    Box {
                        PicnicNavHost()
                        // Transient server notices sit above every screen (#119, Slice 2).
                        ServerNoticeHost(notices = serverMessageBus.messages)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Theme music is browse-time ambience only; never let it play from the background.
        themeMusicPlayer.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Services app-level remote-control actions (#119, Slice 2): navigation, cast-target launch,
     * and the phone-as-remote D-pad proxy. Collected only while STARTED so a backgrounded app
     * neither navigates nor injects keys. Runs on the main thread (lifecycleScope) as both
     * navigation and [dispatchKeyEvent] require.
     */
    private fun observeRemoteControl() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteControlBus.actions.collect { action ->
                    when (action) {
                        RemoteControlAction.GoHome -> navViewModel.resetTo(BrowseKey)
                        RemoteControlAction.GoToSettings -> navViewModel.push(SettingsKey)
                        is RemoteControlAction.ShowItem ->
                            navViewModel.push(DetailKey(action.itemId, null, null))
                        is RemoteControlAction.Play ->
                            navViewModel.push(
                                PlayerKey(action.itemId, action.startPositionMs?.let { it * TICKS_PER_MS })
                            )
                        // D-pad proxy: inject a synthetic key press through the activity window so
                        // Compose-TV focus traversal (and the player's own onKeyEvent) handle it
                        // exactly as a real remote does — no bespoke focus plumbing. See class doc.
                        is RemoteControlAction.DispatchKey -> dispatchRemoteKey(action.key)
                    }
                }
            }
        }
    }

    /**
     * Injects [key] as a phone-as-remote press. BACK routes through the back-press dispatcher so
     * the BackHandler chain (incl. predictive back) runs; the rest are dispatched into the
     * window's view tree as down+up [KeyEvent]s, driving Compose-TV focus / the player's
     * onKeyEvent exactly like a physical remote. Uses the window's decor view rather than the
     * restricted ComponentActivity.dispatchKeyEvent.
     */
    private fun dispatchRemoteKey(key: RemoteKey) {
        if (key == RemoteKey.BACK) {
            onBackPressedDispatcher.onBackPressed()
            return
        }
        val keyCode = when (key) {
            RemoteKey.UP -> KeyEvent.KEYCODE_DPAD_UP
            RemoteKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            RemoteKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            RemoteKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            RemoteKey.SELECT -> KeyEvent.KEYCODE_DPAD_CENTER
            RemoteKey.PAGE_UP -> KeyEvent.KEYCODE_PAGE_UP
            RemoteKey.PAGE_DOWN -> KeyEvent.KEYCODE_PAGE_DOWN
            RemoteKey.BACK -> return // handled above
        }
        val now = SystemClock.uptimeMillis()
        val decorView = window.decorView
        decorView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        decorView.dispatchKeyEvent(KeyEvent(SystemClock.uptimeMillis(), now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun handleIntent(intent: Intent?) {
        val key = deepLinkKey(intent?.data) ?: return
        // If we are already past startup, navigate immediately; otherwise stash
        // until onboarding resolves to Browse.
        if (navViewModel.backStack.isNotEmpty() && navViewModel.backStack.last() !is StartupKey) {
            navViewModel.push(key)
        } else {
            navViewModel.pendingDeepLink = key
        }
    }

    companion object {
        /** Jellyfin position ticks per millisecond (100-ns ticks). */
        private const val TICKS_PER_MS = 10_000L

        /**
         * Maps picnic:// deep links to navigation keys.
         * - picnic://item/{id} → detail (movie / series)
         * - picnic://series/{seriesId}/episode/{episodeId}?seasonId=… → episode listing
         */
        fun deepLinkKey(uri: Uri?): NavKey? {
            if (uri == null || uri.scheme != "picnic") return null
            return when (uri.host) {
                "item" -> uri.lastPathSegment?.let { DetailKey(it, null, null) }
                "series" -> {
                    val segments = uri.pathSegments
                    // /{seriesId}/episode/{episodeId}
                    if (segments.size >= 3 && segments[1] == "episode") {
                        EpisodesKey(
                            seriesId = segments[0],
                            focusEpisodeId = segments[2],
                            seasonId = uri.getQueryParameter("seasonId")
                        )
                    } else {
                        segments.firstOrNull()?.let { DetailKey(it, null, null) }
                    }
                }
                else -> null
            }
        }
    }
}
