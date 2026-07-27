@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import app.picnic.player.playback.videoDisplayHints
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.player.osd.ChaptersPanel
import app.picnic.player.ui.player.osd.ModernOsd
import app.picnic.player.ui.player.osd.PlayerSettingsPanel
import app.picnic.player.ui.player.osd.SkipIndicator
import app.picnic.player.ui.player.osd.SkipIndicatorState
import app.picnic.player.ui.player.osd.SkipSegmentButton
import app.picnic.player.ui.player.osd.StatsForNerdsPanel
import app.picnic.player.ui.player.osd.SubtitleDelayHud
import app.picnic.player.ui.player.osd.TrackPanel
import app.picnic.player.ui.player.osd.TrickplayCell
import app.picnic.player.ui.player.osd.qualitySummary
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class Panel { NONE, AUDIO, SUBTITLE, CHAPTERS, SETTINGS }

private val OsdHorizontalPadding = 56.dp

// Match the chapter card size (ChapterRow CardWidth × CardImageHeight, 16:9).
private val TrickplayPreviewWidth = 180.dp
private val TrickplayPreviewHeight = 101.dp
private val TrickplayGapAboveScrubBar = 10.dp

// Fraction of the screen the video shrinks to (top-left) while the next-up overlay is shown, plus
// the margin it sits in from the top-left corner. Tuned to the next-up reference design.
private const val NextUpPlayerScale = 0.35f
private val NextUpPlayerInset = 56.dp

/**
 * How far past a segment's start playback may be for the skip pill to auto-appear. Natural
 * playback crosses the boundary within one ~500ms tick; landing deeper than this means the
 * user seeked into the middle of the segment, where skip lives only in the OSD.
 */
private const val SkipPillEntryWindowMs = 2_000L

private const val NOTICE_VISIBLE_MS = 3_000L

/**
 * Full-screen player. [PlayerSurface] uses SurfaceView for better performance;
 * libass ASS overlay is a transparent [SubtitleView].
 *
 * [onPlayNext] is called with the next item's id when the user selects the next-up thumbnail
 * or the countdown reaches zero. The nav host navigates to a fresh player route (popUpTo the
 * current player so Back skips the finished episode).
 */
@Composable
fun PlayerScreen(
    itemId: String,
    startTicks: Long?,
    mediaSourceId: String? = null,
    onExit: () -> Unit,
    onPlayNext: (nextItemId: String) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    PublishBackdrop(null)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { viewModel.load(itemId, startTicks) }

    val view = LocalView.current
    SideEffect { view.keepScreenOn = state.isPlaying }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false } }

    var osdVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var lastPanel by remember { mutableStateOf(Panel.NONE) }
    var revealTick by remember { mutableIntStateOf(0) }
    var pulseTick by remember { mutableIntStateOf(0) }
    var pulsePlaying by remember { mutableStateOf(true) }
    var pulseVisible by remember { mutableStateOf(false) }
    var scrubPreview by remember { mutableStateOf<TrickplayPreview?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var scrubBarBottomInset by remember { mutableStateOf(0.dp) }
    val rootFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val scrubberFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val osdSkipFocus = remember { FocusRequester() }
    val subAdjustFocus = remember { FocusRequester() }

    var skipPillDismissed by remember { mutableStateOf(false) }

    // Subtitle-delay live-adjust mode (entered from the settings panel): OSD + panel hide so the
    // subtitles stay visible while the centered HUD shows the offset. Holding L/R accelerates.
    var subtitleAdjust by remember { mutableStateOf(false) }
    var subAdjustHeldDir by remember { mutableIntStateOf(0) }

    val pipState = rememberPipController(
        player = viewModel.player,
        autoEnterEnabled = settings.pictureInPicture && viewModel.pictureInPictureSupported,
        isPlaying = state.isPlaying
    )
    val inPipMode = pipState.inPipMode
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    pipState.consumeExitedPip()
                }
                Lifecycle.Event.ON_STOP -> {
                    // Leaving the app ends the viewing: the player, its decoder and the server's
                    // encoder all go, and whatever screen playback started from comes back.
                    if (!pipState.inPipMode) {
                        pipState.consumeExitedPip()
                        viewModel.onPlayerExit()
                        viewModel.endPlayback()
                        onExit()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val jellyfinDisplayHints = remember(state.mediaSource) {
        state.mediaSource?.mediaStreams.orEmpty().videoDisplayHints()
    }
    DisplayModeManager(
        player = viewModel.player,
        matchRefreshRate = settings.matchRefreshRate,
        matchResolution = settings.matchResolution,
        jellyfinHints = jellyfinDisplayHints
    )

    // Next-up overlay is active once playback has ended (or entered an outro in opt-in mode) and a
    // next episode is known. Declared early so focus/back effects can react to it.
    val showNextUpOverlay = state.endedAwaitingNext && state.nextUp != null && !inPipMode

    // The OSD and track panels must never sit under the next-up screen; close them immediately when
    // it appears (otherwise an open OSD lingers until its inactivity timeout).
    LaunchedEffect(showNextUpOverlay, inPipMode) {
        if (showNextUpOverlay || inPipMode) {
            osdVisible = false
            panel = Panel.NONE
        }
    }

    // Quick-skip pill (WS-2): signed running total of the current D-pad seek burst.
    var skipAccumMs by remember { mutableLongStateOf(0L) }
    var skipTick by remember { mutableIntStateOf(0) }
    var skipVisible by remember { mutableStateOf(false) }

    fun reveal() {
        if (inPipMode) return
        osdVisible = true
        revealTick++
        // Opening the OSD ends any quick-skip burst.
        skipVisible = false
        skipAccumMs = 0L
    }

    fun pulse(playing: Boolean) {
        pulsePlaying = playing
        pulseTick++
    }

    fun togglePlay() {
        val willPlay = !state.isPlaying
        viewModel.playPause()
        pulse(willPlay)
    }

    fun closePanel() {
        panel = Panel.NONE
        reveal()
    }

    // Real exit to browse: tear down the app-scoped session controls (audio + sleep), then leave.
    fun exitPlayer() {
        viewModel.onPlayerExit()
        onExit()
    }

    // Remote control (#119, Slice 2): the VM applies pause/seek/track changes itself, but Stop and
    // NextTrack need navigation, which only the screen owns — service them through its callbacks.
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                PlayerNavEvent.Exit -> exitPlayer()
                is PlayerNavEvent.PlayNext -> {
                    viewModel.onAutoplayHandoff()
                    onPlayNext(event.itemId)
                }
            }
        }
    }

    // Record which panel is open so closing it can return focus to the OSD button it came from.
    LaunchedEffect(panel) { if (panel != Panel.NONE) lastPanel = panel }

    // Single owner of OSD focus seeding: whenever the OSD is interactive (no panel open),
    // focus the button that opened the just-closed panel, else the scrubber (a fresh reveal
    // or a Chapters close). Replaces ModernOsd's self-seed, which raced this and won,
    // dropping focus onto the scrubber instead of the audio/subtitle/settings button.
    LaunchedEffect(panel, osdVisible) {
        if (panel != Panel.NONE || !osdVisible) return@LaunchedEffect
        val target = when (lastPanel) {
            Panel.AUDIO -> audioFocus
            Panel.SUBTITLE -> subtitleFocus
            Panel.SETTINGS -> settingsFocus
            Panel.CHAPTERS, Panel.NONE -> scrubberFocus
        }
        target.requestFocusWhenAttached()
        lastPanel = Panel.NONE
    }

    // Pause on scrub start, restore the pre-scrub play state on commit or cancel. Driven off the
    // scrubbing flag alone: commit vs cancel differ only in whether a seek fired (the OSD owns that).
    LaunchedEffect(scrubbing) {
        if (scrubbing) {
            wasPlayingBeforeScrub = viewModel.player.isPlaying
            viewModel.player.pause()
        } else if (wasPlayingBeforeScrub) {
            viewModel.player.play()
        }
    }

    LaunchedEffect(revealTick, osdVisible, panel, state.isPlaying, scrubbing) {
        // Hold the OSD open while an active scrub is in progress, else it would hide mid-scrub and
        // strand a paused video with no controls.
        if (osdVisible && panel == Panel.NONE && !scrubbing) {
            delay(settings.osdHideSeconds.toLong().coerceAtLeast(2) * 1000)
            osdVisible = false
            scrubPreview = null
        }
    }
    LaunchedEffect(pulseTick) {
        if (pulseTick > 0) {
            pulseVisible = true
            delay(750)
            pulseVisible = false
        }
    }
    // Quick-skip burst timeout: each press restarts this; ~1s of quiet hides + resets.
    LaunchedEffect(skipTick) {
        if (skipTick > 0) {
            skipVisible = true
            delay(1000)
            skipVisible = false
            skipAccumMs = 0L
        }
    }
    // Subtitle-delay live adjust: holding L/R accelerates from the 100ms fine step to the 1s coarse
    // step via a single effect keyed on the held direction (no coroutine-per-tick).
    val latestSubDelay = rememberUpdatedState(state.subtitleDelayMs)
    LaunchedEffect(subAdjustHeldDir) {
        if (subAdjustHeldDir == 0) return@LaunchedEffect
        var elapsed = 0L
        while (isActive) {
            val coarse = elapsed >= 600L
            val stepMs = if (coarse) 1000L else 100L
            val next = (latestSubDelay.value + subAdjustHeldDir * stepMs).coerceIn(-60_000L, 60_000L)
            if (next != latestSubDelay.value) viewModel.setSubtitleDelayMs(next)
            val wait = if (coarse) 90L else 140L
            delay(wait)
            elapsed += wait
        }
    }
    LaunchedEffect(subtitleAdjust) {
        if (subtitleAdjust) runCatching { subAdjustFocus.requestFocus() }
    }
    LaunchedEffect(osdVisible, panel, showNextUpOverlay, subtitleAdjust, skipPillDismissed) {
        if (!osdVisible && panel == Panel.NONE && !showNextUpOverlay && !subtitleAdjust) {
            // Also re-runs when the skip pill is dismissed/times out so focus returns to the
            // video surface (the pill held focus) instead of being orphaned.
            runCatching { rootFocus.requestFocus() }
        } else if (osdVisible || panel != Panel.NONE) {
            skipPillDismissed = true
        }
    }
    LaunchedEffect(state.currentSegment) {
        val segment = state.currentSegment
        if (segment != null) {
            // The pill auto-appears only when playback entered the segment at its start (natural
            // boundary crossing). Seeking into the middle of a segment keeps it dismissed — skip
            // then lives only in the OSD.
            val enteredAtStart = state.positionMs - segment.startMs <= SkipPillEntryWindowMs
            if (osdVisible || panel != Panel.NONE || !enteredAtStart) {
                skipPillDismissed = true
            } else {
                skipPillDismissed = false
                // Focus is requested from inside the pill's AnimatedVisibility content (below),
                // once its node has actually composed — requesting here races the pill's layout and
                // loses on a janky cold start (the node isn't attached yet, so the request times out).
            }
        } else {
            skipPillDismissed = false
            if (!osdVisible && panel == Panel.NONE) {
                runCatching { rootFocus.requestFocus() }
            }
        }
    }
    LaunchedEffect(state.currentSegment, skipPillDismissed) {
        if (state.currentSegment != null && !skipPillDismissed) {
            delay(10_000)
            skipPillDismissed = true
        }
    }

    // When endedAwaitingNext becomes true with no next item, fall through to today's exit.
    LaunchedEffect(state.endedAwaitingNext) {
        if (state.endedAwaitingNext && state.nextUp == null) {
            exitPlayer()
        }
    }

    // Back during the overlay: if the video is still playing (outro mode) return to it; otherwise
    // (playback already ended) fall through to today's exit.
    val onNextUpBack: () -> Unit = {
        if (state.videoStillPlaying) viewModel.dismissNextUp() else exitPlayer()
    }

    BackHandler {
        when {
            subtitleAdjust -> {
                subAdjustHeldDir = 0
                subtitleAdjust = false
                panel = Panel.SETTINGS
            }
            showNextUpOverlay -> onNextUpBack()
            panel != Panel.NONE -> closePanel()
            osdVisible -> {
                // Cancel any active scrub: keep the current position, drop the target. Clearing
                // scrubbing lets the pause/resume effect restore the pre-scrub play state.
                scrubbing = false
                osdVisible = false
                scrubPreview = null
            }
            // The skip pill is showing (segment active, nothing else on top): Back dismisses the
            // early pill for this segment instead of exiting. Skip still lives in the OSD. Back is
            // routed here rather than the pill's onKeyEvent because enableOnBackInvokedCallback
            // sends Back straight to the OnBackPressedDispatcher — it never reaches focused nodes.
            state.currentSegment != null && !skipPillDismissed -> {
                skipPillDismissed = true
            }
            else -> exitPlayer()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable(panel == Panel.NONE && !osdVisible && !showNextUpOverlay && !subtitleAdjust)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (osdVisible || panel != Panel.NONE || showNextUpOverlay || subtitleAdjust) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        togglePlay()
                        reveal()
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        val d = -settings.skipBackwardSeconds * 1000L
                        viewModel.seekBy(d)
                        skipAccumMs += d
                        skipTick++
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        val d = settings.skipForwardSeconds * 1000L
                        viewModel.seekBy(d)
                        skipAccumMs += d
                        skipTick++
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        reveal()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Next-up backdrop: full-screen image of the upcoming episode, painted beneath the shrunk
        // video and the card. Fades in with the overlay.
        AnimatedVisibility(
            visible = showNextUpOverlay,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(Modifier.fillMaxSize()) {
                state.nextUp?.backdropUrl?.let { url ->
                    Image(
                        painter = rememberAsyncImagePainter(url),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Scrim for overall contrast under the card.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
        }

        // Shrink the video to a small inset window in the top-left while the next-up overlay is
        // active, leaving room for the backdrop and the bottom-right card. animateFloatAsState keeps
        // the resize on a GPU layer and preserves the aspect ratio (both dimensions scale together).
        // The top-left margin is derived from the same animation progress so it tracks the shrink.
        val playerScale by animateFloatAsState(
            targetValue = if (showNextUpOverlay) NextUpPlayerScale else 1f,
            animationSpec = tween(400),
            label = "playerScale"
        )
        val insetFraction = ((1f - playerScale) / (1f - NextUpPlayerScale)).coerceIn(0f, 1f)
        val playerInset = NextUpPlayerInset * insetFraction

        // Size the surface to the video's true display aspect ratio (accounts for anamorphic
        // pixel ratios), so 4:3 content is pillar-boxed instead of stretched to the 16:9 screen.
        // videoSizeDp is null until the first frame's size is known; ContentScale.Fit letterboxes.
        val presentationState = rememberPresentationState(viewModel.player)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = playerInset, top = playerInset)
                .fillMaxSize(playerScale)
                // Round the corners as the video shrinks (0 at full screen → media-card radius),
                // matching the rest of the app's cards. TEXTURE_VIEW clips cleanly.
                .clip(RoundedCornerShape(12.dp * insetFraction))
                // Black fill so pillar/letterbox bars stay black inside the card — otherwise the
                // next-up backdrop (drawn beneath) shows through the bars for non-16:9 content.
                .background(Color.Black)
                .then(pipState.sourceRectModifier())
                .zIndex(if (showNextUpOverlay) 2f else 0f),
            contentAlignment = Alignment.Center
        ) {
            PlayerSurface(
                player = viewModel.player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.resizeWithContentScale(
                    ContentScale.Fit,
                    presentationState.videoSizeDp
                )
            )
            // libass overlay, sized exactly to the video display rect (libass maps frame
            // coordinates onto its bounds). Tracks pillar/letterboxing and the next-up shrink.
            AndroidView(
                factory = { context -> viewModel.assOverlayView(context) },
                modifier = Modifier.resizeWithContentScale(
                    ContentScale.Fit,
                    presentationState.videoSizeDp
                )
            )
        }

        // Full screen: SRT/VTT text cues stay screen-relative, not bound to the picture.
        AndroidView(
            factory = { context -> SubtitleView(context) },
            update = { subtitleView ->
                viewModel.attachSubtitleView(subtitleView)
                subtitleView.setCues(state.subtitleCues)
            },
            onReset = { it.setCues(emptyList()) },
            modifier = Modifier.fillMaxSize()
        )

        if (state.isLoading && state.error == null) {
            state.backdropUrl?.let { url ->
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
            CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = PicnicColors.Accent
            )
        } else if (state.buffering && state.error == null && !state.isPlaying) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = PicnicColors.Accent
            )
        }

        state.error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center)) }

        // The OSD and the chapters panel share the bottom slot and swap: opening chapters slides the
        // OSD down off-screen while the chapters panel slides up into its place (and fully hides it).
        AnimatedVisibility(
            visible = osdVisible && state.error == null && !showNextUpOverlay && panel != Panel.CHAPTERS,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            ModernOsd(
                state = state,
                onPlayPause = { togglePlay() },
                onSeek = { viewModel.seekTo(it) },
                onAudio = { panel = Panel.AUDIO },
                onSubtitles = { panel = Panel.SUBTITLE },
                onSettings = { panel = Panel.SETTINGS },
                onChapters = { if (state.chapters.isNotEmpty()) panel = Panel.CHAPTERS },
                skipForwardSeconds = settings.skipForwardSeconds,
                skipBackwardSeconds = settings.skipBackwardSeconds,
                onDismiss = {
                    osdVisible = false
                    scrubPreview = null
                },
                onInteract = { revealTick++ },
                onScrubPreviewChange = { scrubPreview = it },
                onScrubbingChange = { scrubbing = it },
                onScrubBarBottomInset = { scrubBarBottomInset = it },
                trickplayFor = viewModel::trickplayFor,
                audioFocusRequester = audioFocus,
                subtitleFocusRequester = subtitleFocus,
                settingsFocusRequester = settingsFocus,
                scrubberFocusRequester = scrubberFocus,
                osdSkipFocusRequester = osdSkipFocus,
                showSkipInOsd = state.currentSegment != null && (skipPillDismissed || osdVisible || panel != Panel.NONE),
                onSkip = { viewModel.skipCurrentSegment() },
                focusEnabled = panel == Panel.NONE
            )
        }
        AnimatedVisibility(
            visible = panel == Panel.CHAPTERS && !showNextUpOverlay,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(3f),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            ChaptersPanel(
                chapters = state.chapters,
                positionMs = state.positionMs,
                trickplayFor = viewModel::trickplayFor,
                onSelect = { viewModel.seekTo(it) },
                onClose = ::closePanel
            )
        }

        AnimatedVisibility(
            visible = state.currentSegment != null &&
                !skipPillDismissed &&
                !osdVisible &&
                panel == Panel.NONE &&
                !showNextUpOverlay,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 56.dp, bottom = 56.dp)
                .zIndex(2f),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            state.currentSegment?.let { segment ->
                // Request focus once the pill is actually in composition. Keyed on kind so a
                // back-to-back segment change (intro -> recap without the pill hiding) re-grabs
                // focus. Runs late-but-correct if cold-start jank delays this subtree's layout.
                LaunchedEffect(segment.kind) {
                    skipFocus.requestFocusWhenAttached()
                }
                SkipSegmentButton(
                    kind = segment.kind,
                    onClick = { viewModel.skipCurrentSegment() },
                    modifier = Modifier
                        .focusRequester(skipFocus)
                        .onKeyEvent { event ->
                            // Back is handled by the screen's BackHandler (enableOnBackInvokedCallback
                            // routes Back past focused nodes anyway). Here we only trap the D-pad so
                            // arrows don't move focus off the pill while it's showing.
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                                else -> false
                            }
                        }
                )
            }
        }

        if (osdVisible && panel == Panel.NONE && scrubPreview != null && scrubBarBottomInset > 0.dp) {
            val preview = scrubPreview!!
            val frame = preview.frame
            val previewW = TrickplayPreviewWidth
            // Fixed card matching the chapter card, regardless of source aspect — the frame is
            // letterboxed inside it (see TrickplayCell), so the hover preview stays consistent.
            val previewH = TrickplayPreviewHeight
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            ) {
                val barWidth = maxWidth - OsdHorizontalPadding * 2
                val xOffset = OsdHorizontalPadding +
                    (barWidth * preview.fraction - previewW / 2f).coerceIn(0.dp, barWidth - previewW)
                TrickplayPreviewImage(
                    frame = frame,
                    width = previewW,
                    height = previewH,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        // Lambda overload: the offset tracks the scrub fraction, so deferring it to
                        // the layout phase avoids recomposing on every step (UseOfNonLambdaOffsetOverload).
                        .offset {
                            IntOffset(
                                x = xOffset.roundToPx(),
                                y = -(scrubBarBottomInset + TrickplayGapAboveScrubBar).roundToPx()
                            )
                        }
                )
            }
        }

        if (panel == Panel.AUDIO) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                TrackPanel(
                    title = "Audio",
                    options = state.audioTracks,
                    allowOff = false,
                    onSelect = { id ->
                        id?.let(viewModel::selectAudio)
                        closePanel()
                    },
                    onClose = ::closePanel
                )
            }
        }
        if (panel == Panel.SUBTITLE) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                TrackPanel(
                    title = "Subtitles",
                    options = state.subtitleTracks,
                    allowOff = true,
                    onSelect = { id ->
                        viewModel.selectSubtitle(id)
                        closePanel()
                    },
                    onClose = ::closePanel
                )
            }
        }
        if (panel == Panel.SETTINGS) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                PlayerSettingsPanel(
                    subtitleDelayMs = state.subtitleDelayMs,
                    qualityOptions = state.qualityOptions,
                    selectedQuality = state.activeQuality,
                    qualitySummary = qualitySummary(state.playMethod, state.activeQuality),
                    onSelectQuality = { viewModel.selectQuality(it) },
                    playbackSpeed = state.playbackSpeed,
                    audioBoost = state.audioBoost,
                    nightMode = state.nightMode,
                    sleep = state.sleep,
                    showStatsForNerds = state.showStatsForNerds,
                    onAdjustSubtitleDelay = {
                        panel = Panel.NONE
                        osdVisible = false
                        subtitleAdjust = true
                    },
                    onSpeed = { viewModel.setSpeed(it) },
                    onAudioBoost = { viewModel.setAudioBoost(it) },
                    onNightMode = { viewModel.setNightMode(it) },
                    onSleep = { viewModel.setSleep(it) },
                    onToggleStatsForNerds = { viewModel.toggleStatsForNerds() },
                    onEnterPip = pipState::enterPip,
                    pipSupported = viewModel.pictureInPictureSupported,
                    onClose = ::closePanel
                )
            }
        }

        state.notice?.let { notice ->
            LaunchedEffect(notice) {
                delay(NOTICE_VISIBLE_MS)
                viewModel.clearNotice()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(4f)
                    .padding(bottom = 96.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Subtitle-delay live adjust: invisible key-catcher (L/R steps, Back returns to the panel)
        // plus a centered HUD. OSD + panel are hidden so the subtitles stay visible at the bottom.
        if (subtitleAdjust) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .focusRequester(subAdjustFocus)
                    .focusable()
                    .onKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                                subAdjustHeldDir = 0
                                true
                            }
                            event.type != KeyEventType.KeyDown -> false
                            event.key == Key.DirectionLeft -> {
                                subAdjustHeldDir = -1
                                true
                            }
                            event.key == Key.DirectionRight -> {
                                subAdjustHeldDir = 1
                                true
                            }
                            event.key == Key.Back || event.key == Key.DirectionCenter || event.key == Key.Enter -> {
                                subAdjustHeldDir = 0
                                subtitleAdjust = false
                                panel = Panel.SETTINGS
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                SubtitleDelayHud(
                    delayMs = state.subtitleDelayMs,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        if (state.showStatsForNerds) {
            Box(Modifier.fillMaxSize().zIndex(4f)) {
                StatsForNerdsPanel(
                    state = state,
                    player = viewModel.player
                )
            }
        }

        SkipIndicator(
            state = SkipIndicatorState(
                accumMs = skipAccumMs,
                visible = skipVisible && !osdVisible && panel == Panel.NONE && !showNextUpOverlay
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(2f)
        )

        AnimatedVisibility(
            visible = pulseVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.72f, animationSpec = tween(200)) +
                fadeIn(animationSpec = tween(200)),
            exit = scaleOut(targetScale = 0.88f, animationSpec = tween(240)) +
                fadeOut(animationSpec = tween(240))
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (pulsePlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // Next-up card occupies the bottom band below the shrunk video; the video stays visible
        // at the top. Shown when STATE_ENDED fires (default) or an OUTRO segment is entered (opt-in).
        // In outro mode the countdown spans the remaining episode time plus the configured delay, so
        // it lines up with the actual end of the episode. Captured once when the overlay appears.
        val nextUpCountdownStart = remember(showNextUpOverlay) {
            if (showNextUpOverlay && state.videoStillPlaying) {
                val remainingSec = ((state.durationMs - state.positionMs) / 1000L)
                    .toInt().coerceAtLeast(0)
                remainingSec + settings.nextUpCountdownSeconds
            } else {
                settings.nextUpCountdownSeconds
            }
        }
        AnimatedVisibility(
            visible = showNextUpOverlay,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(4f),
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            state.nextUp?.let { nextUp ->
                NextUpOverlay(
                    item = nextUp,
                    countdownSeconds = nextUpCountdownStart,
                    onPlayNext = {
                        viewModel.onAutoplayHandoff()
                        onPlayNext(nextUp.id)
                    },
                    onBack = onNextUpBack
                )
            }
        }
    }
}

@Composable
private fun TrickplayPreviewImage(
    frame: TrickplayFrame,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    TrickplayCell(
        frame = frame,
        modifier = modifier
            .width(width)
            .height(height)
    )
}
