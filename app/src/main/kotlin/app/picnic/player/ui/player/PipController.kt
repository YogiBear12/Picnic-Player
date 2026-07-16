package app.picnic.player.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.media3.common.Player
import app.picnic.player.R

private const val ACTION_PLAY_PAUSE = "PIP_PLAY_PAUSE"
private const val ACTION_MUTE = "PIP_MUTE"

/** Aspect ratios Android allows for a PiP window; anything outside is left unset. */
private val PipAspectRange = 0.4185f..2.39f

/**
 * Owns everything Picture-in-Picture for the player screen: the in-PiP / exited-PiP
 * mode state, the window's remote play-pause/mute actions and their broadcast
 * receiver, and the params (source rect hint, aspect ratio, auto-enter) that must be
 * re-published to the system as playback state and window bounds change.
 *
 * The screen keeps three touch points: [sourceRectModifier] on the video window,
 * [enterPip] from the settings panel, and [exitedPip]/[consumeExitedPip] in its
 * lifecycle handling (leaving PiP by closing the window must exit the player).
 */
@Stable
class PipController internal constructor(
    private val activity: ComponentActivity?,
    private val player: Player
) {
    var inPipMode by mutableStateOf(activity?.isInPictureInPictureMode ?: false)
        internal set

    /** True after the PiP window was dismissed; the screen exits playback on ON_STOP. */
    var exitedPip by mutableStateOf(false)
        internal set

    fun consumeExitedPip() {
        exitedPip = false
    }

    internal var isMuted by mutableStateOf(player.volume == 0f)
    internal var autoEnterEnabled by mutableStateOf(false)
    internal var sourceRect by mutableStateOf<Rect?>(null)
    internal var aspect by mutableStateOf<Rational?>(null)

    internal fun toggleMute() {
        isMuted = !isMuted
        player.volume = if (isMuted) 0f else 1f
    }

    /**
     * Attach to the video window so the PiP enter animation morphs from its on-screen
     * bounds, and so the window keeps the video's true display aspect ratio (accounts
     * for anamorphic pixel ratios).
     */
    fun sourceRectModifier(): Modifier = Modifier.onGloballyPositioned { coordinates ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || inPipMode) return@onGloballyPositioned
        val bounds = coordinates.boundsInWindow()
        sourceRect = Rect(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt()
        )
        val videoSize = player.videoSize
        val width = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
        val height = videoSize.height
        aspect = if (width > 0 &&
            height > 0 &&
            width.toFloat() / height.toFloat() in PipAspectRange
        ) {
            Rational(width, height)
        } else {
            null
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val builder = PictureInPictureParams.Builder()
            aspect?.let { builder.setAspectRatio(it) }
            activity?.enterPictureInPictureMode(builder.build())
        } catch (_: Exception) {
            // PiP unsupported/denied on this device — the setting stays a no-op.
        }
    }

    private fun action(suffix: String) = "${activity?.packageName}.$suffix"

    internal fun handleBroadcast(intent: Intent?) {
        when (intent?.action) {
            action(ACTION_PLAY_PAUSE) -> if (player.isPlaying) player.pause() else player.play()
            action(ACTION_MUTE) -> toggleMute()
        }
    }

    internal fun intentFilter() = IntentFilter().apply {
        addAction(action(ACTION_PLAY_PAUSE))
        addAction(action(ACTION_MUTE))
    }

    private fun remoteAction(suffix: String, requestCode: Int, iconRes: Int, title: String): RemoteAction {
        val intent = Intent(action(suffix)).apply { setPackage(activity?.packageName) }
        val pending = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(Icon.createWithResource(activity, iconRes), title, title, pending)
    }

    /** Republish PiP params; call whenever bounds, playback state, or mute state change. */
    internal fun publishParams(isPlaying: Boolean) {
        val rect = sourceRect ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val builder = PictureInPictureParams.Builder()
        builder.setSourceRectHint(rect)
        aspect?.let { builder.setAspectRatio(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
        }
        builder.setActions(
            listOf(
                remoteAction(
                    ACTION_PLAY_PAUSE,
                    requestCode = 0,
                    iconRes = if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                    title = if (isPlaying) "Pause" else "Play"
                ),
                remoteAction(
                    ACTION_MUTE,
                    requestCode = 1,
                    iconRes = if (isMuted) R.drawable.ic_pip_unmute else R.drawable.ic_pip_mute,
                    title = if (isMuted) "Unmute" else "Mute"
                )
            )
        )
        try {
            activity?.setPictureInPictureParams(builder.build())
        } catch (_: Exception) {
            // Ignored — some devices reject params outside a PiP-capable state.
        }
    }
}

/**
 * Remember a [PipController] wired to the host activity: PiP mode-change listener,
 * remote-action broadcast receiver, and params republishing. [autoEnterEnabled] is
 * the user's Picture-in-Picture setting; [isPlaying] drives the play/pause action icon.
 */
@Composable
fun rememberPipController(
    player: Player,
    autoEnterEnabled: Boolean,
    isPlaying: Boolean
): PipController {
    val activity = LocalActivity.current as? ComponentActivity
    val controller = remember(activity, player) { PipController(activity, player) }
    SideEffect { controller.autoEnterEnabled = autoEnterEnabled }

    DisposableEffect(controller) {
        if (activity == null) return@DisposableEffect onDispose {}
        val observer = Consumer<PictureInPictureModeChangedInfo> { info ->
            if (controller.inPipMode && !info.isInPictureInPictureMode) {
                controller.exitedPip = true
            }
            controller.inPipMode = info.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(observer)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(observer)
            // Leaving the screen must not let the system auto-enter PiP for it later.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    DisposableEffect(controller) {
        if (activity == null) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                controller.handleBroadcast(intent)
            }
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            controller.intentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { activity.unregisterReceiver(receiver) }
    }

    LaunchedEffect(
        controller.sourceRect,
        controller.aspect,
        controller.autoEnterEnabled,
        controller.isMuted,
        isPlaying
    ) {
        controller.publishParams(isPlaying)
    }

    return controller
}
