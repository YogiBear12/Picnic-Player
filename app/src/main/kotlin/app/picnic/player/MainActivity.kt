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
import app.picnic.player.data.playback.msToTicks
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

    @Inject lateinit var updateRepository: app.picnic.player.data.update.UpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        observeRemoteControl()

        lifecycleScope.launch {
            updateRepository.bootCleanup()
            runCatching { updateRepository.check() }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PicnicTheme {
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
                        ServerNoticeHost(notices = serverMessageBus.messages)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        themeMusicPlayer.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

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
                                PlayerKey(action.itemId, action.startPositionMs?.let { it.msToTicks() })
                            )
                        is RemoteControlAction.DispatchKey -> dispatchRemoteKey(action.key)
                    }
                }
            }
        }
    }

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
            RemoteKey.BACK -> return
        }
        val now = SystemClock.uptimeMillis()
        val decorView = window.decorView
        decorView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        decorView.dispatchKeyEvent(KeyEvent(SystemClock.uptimeMillis(), now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun handleIntent(intent: Intent?) {
        val key = deepLinkKey(intent?.data) ?: return
        if (navViewModel.backStack.isNotEmpty() && navViewModel.backStack.last() !is StartupKey) {
            navViewModel.push(key)
        } else {
            navViewModel.pendingDeepLink = key
        }
    }

    companion object {
        fun deepLinkKey(uri: Uri?): NavKey? {
            if (uri == null || uri.scheme != "picnic") return null
            return when (uri.host) {
                "item" -> uri.lastPathSegment?.let { DetailKey(it, null, null) }
                "series" -> {
                    val segments = uri.pathSegments
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
