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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.playback.subtitleBottomPaddingFraction
import app.picnic.player.playback.videoDisplayHints
import app.picnic.player.playback.videoRectHeightFraction
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.player.osd.ChaptersPanel
import app.picnic.player.ui.player.osd.ModernOsd
import app.picnic.player.ui.player.osd.PlayerSettingsPanel
import app.picnic.player.ui.player.osd.PlayerSettingsPanelWidth
import app.picnic.player.ui.player.osd.SidePanel
import app.picnic.player.ui.player.osd.SkipIndicator
import app.picnic.player.ui.player.osd.SkipIndicatorState
import app.picnic.player.ui.player.osd.SkipSegmentButton
import app.picnic.player.ui.player.osd.StatsForNerdsPanel
import app.picnic.player.ui.player.osd.SubtitleDelayHud
import app.picnic.player.ui.player.osd.TrackPanel
import app.picnic.player.ui.player.osd.TrackPanelWidth
import app.picnic.player.ui.player.osd.TrickplayCell
import app.picnic.player.ui.player.osd.qualitySummary
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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

    // Starts hidden: an OSD that auto-opens over the loading screen also swallows a skip button for
    // a segment that begins at t=0, since the pill only auto-shows while the OSD is down.
    val chrome = remember { PlayerChrome() }
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

    // Subtitle-delay live-adjust mode (entered from the settings panel): holding L/R accelerates.
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
            if (event == Lifecycle.Event.ON_RESUME) pipState.consumeExitedPip()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // The VM ends the viewing when the app leaves the screen; PiP is the one case where it must
    // not, so keep it told which mode the window is in.
    LaunchedEffect(inPipMode) { viewModel.onPipModeChanged(inPipMode) }

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
    LaunchedEffect(showNextUpOverlay) { chrome.onNextUpVisibleChanged(showNextUpOverlay) }
    LaunchedEffect(inPipMode) { chrome.onPipModeChanged(inPipMode) }

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
        chrome.closePanel()
    }

    // Real exit to browse: end the viewing (playback stops here, not when this entry is finally
    // disposed after the fade), then leave.
    fun exitPlayer() {
        viewModel.endViewing()
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

    // Single owner of OSD focus seeding: whenever the OSD is interactive (no panel open),
    // focus the button that opened the just-closed panel, else the scrubber (a fresh reveal
    // or a Chapters close). Replaces ModernOsd's self-seed, which raced this and won,
    // dropping focus onto the scrubber instead of the audio/subtitle/settings button.
    LaunchedEffect(chrome.panel, chrome.osdVisible) {
        if (chrome.panel != Panel.NONE || !chrome.osdVisible) return@LaunchedEffect
        val target = when (chrome.lastPanel) {
            Panel.AUDIO -> audioFocus
            Panel.SUBTITLE -> subtitleFocus
            Panel.SETTINGS -> settingsFocus
            Panel.CHAPTERS, Panel.NONE -> scrubberFocus
        }
        target.requestFocusWhenAttached()
        chrome.consumeLastPanel()
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

    LaunchedEffect(chrome.revealTick, chrome.osdVisible, chrome.panel, state.isPlaying, scrubbing) {
        // Hold the OSD open while an active scrub is in progress, else it would hide mid-scrub and
        // strand a paused video with no controls.
        if (chrome.osdVisible && chrome.panel == Panel.NONE && !scrubbing) {
            delay(settings.osdHideSeconds.toLong().coerceAtLeast(2) * 1000)
            chrome.hideOsd()
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
    LaunchedEffect(chrome.quickSkipTick) {
        if (chrome.quickSkipTick > 0) {
            delay(1000)
            chrome.endQuickSkip()
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
    LaunchedEffect(chrome.subtitleAdjust) {
        if (chrome.subtitleAdjust) runCatching { subAdjustFocus.requestFocus() }
    }
    LaunchedEffect(chrome.videoHasFocus, chrome.skipPillDismissed) {
        // Also re-runs when the skip pill is dismissed/times out so focus returns to the video
        // surface (the pill held focus) instead of being orphaned.
        if (chrome.videoHasFocus) runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(state.currentSegment) {
        val segment = state.currentSegment
        if (segment != null) {
            // The pill auto-appears only when playback entered the segment at its start (natural
            // boundary crossing). Seeking into the middle of a segment keeps it dismissed — skip
            // then lives only in the OSD.
            // Focus for the pill is requested from inside its AnimatedVisibility content (below),
            // once the node has actually composed — requesting here races the pill's layout and
            // loses on a janky cold start (the node isn't attached yet, so the request times out).
            chrome.onSegmentChanged(
                segmentActive = true,
                enteredAtStart = state.positionMs - segment.startMs <= SkipPillEntryWindowMs
            )
        } else {
            chrome.onSegmentChanged(segmentActive = false, enteredAtStart = false)
            if (chrome.videoHasFocus) runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(state.currentSegment, chrome.skipPillDismissed) {
        if (state.currentSegment != null && !chrome.skipPillDismissed) {
            delay(10_000)
            chrome.dismissSkipPill()
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

    // Back is routed through the chrome rather than the focused node: enableOnBackInvokedCallback
    // sends it straight to the OnBackPressedDispatcher, so widgets never see it.
    BackHandler {
        subAdjustHeldDir = 0
        when (chrome.onBack(segmentActive = state.currentSegment != null)) {
            BackOutcome.Handled -> Unit
            BackOutcome.ClosedOsd -> {
                // Cancel any active scrub: keep the current position, drop the target. Clearing
                // scrubbing lets the pause/resume effect restore the pre-scrub play state.
                scrubbing = false
                scrubPreview = null
            }
            BackOutcome.NextUp -> onNextUpBack()
            BackOutcome.ExitPlayer -> exitPlayer()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable(chrome.videoHasFocus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (!chrome.videoHasFocus) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        togglePlay()
                        chrome.reveal()
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        val d = -settings.skipBackwardSeconds * 1000L
                        viewModel.seekBy(d)
                        chrome.onQuickSkip(d)
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        val d = settings.skipForwardSeconds * 1000L
                        viewModel.seekBy(d)
                        chrome.onQuickSkip(d)
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        chrome.reveal()
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

        // Text and bitmap cues are placed against different rectangles, so each gets its own view.
        val (bitmapCues, textCues) = state.subtitleCues.partition { it.bitmap != null }

        // Size the surface to the video's true display aspect ratio (accounts for anamorphic
        // pixel ratios), so 4:3 content is pillar-boxed instead of stretched to the 16:9 screen.
        // videoSizeDp is null until the first frame's size is known; ContentScale.Fit letterboxes.
        val presentationState = rememberPresentationState(viewModel.player)
        BoxWithConstraints(
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
            // PGS/DVB bitmaps, painted into whichever rect their plane belongs in — the picture
            // when the plane matches it, the screen when the video was cropped out from under it.
            // Lives inside the video box so it shrinks with the next-up overlay either way.
            AndroidView(
                factory = { context -> SubtitleView(context) },
                update = { it.setCues(bitmapCues) },
                onReset = { it.setCues(emptyList()) },
                modifier = Modifier.resizeWithContentScale(
                    ContentScale.Fit,
                    bitmapSubtitleFrame(bitmapCues, presentationState.videoSizeDp)
                )
            )
            // Spans the box whatever the area: a SubtitleView clips cues to its own bounds, so one
            // sized to the video rect could never place a cue in the bars.
            val subtitleRange by viewModel.subtitleRenderRange.collectAsStateWithLifecycle()
            val blackBarTrack by viewModel.blackBars.collectAsStateWithLifecycle()
            // Derived so a track with segments only recomposes when the framing actually changes,
            // not on every position tick.
            val blackBars by remember { derivedStateOf { blackBarTrack.at(state.positionMs) } }
            val appearance = settings.subtitleAppearance
            val videoRectHeight = presentationState.videoSizeDp?.let { video ->
                videoRectHeightFraction(video.width, video.height, maxWidth.value, maxHeight.value)
            } ?: 1f
            val bottomPadding = subtitleBottomPaddingFraction(
                appearance.area,
                appearance.insetPercent,
                videoRectHeight,
                blackBars
            )
            AndroidView(
                factory = { context -> SubtitleView(context) },
                update = { subtitleView ->
                    viewModel.attachSubtitleView(subtitleView, bottomPadding, subtitleRange)
                    subtitleView.setCues(textCues)
                },
                onReset = { it.setCues(emptyList()) },
                modifier = Modifier.fillMaxSize()
            )
        }

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

        // Opening any panel slides the OSD off the bottom; closing one slides it back and the
        // focus-seeding effect above returns focus to the button that opened it.
        AnimatedVisibility(
            visible = chrome.osdVisible && state.error == null && !showNextUpOverlay && chrome.panel == Panel.NONE,
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
                onAudio = { chrome.openPanel(Panel.AUDIO) },
                onSubtitles = { chrome.openPanel(Panel.SUBTITLE) },
                onSettings = { chrome.openPanel(Panel.SETTINGS) },
                onChapters = { if (state.chapters.isNotEmpty()) chrome.openPanel(Panel.CHAPTERS) },
                skipForwardSeconds = settings.skipForwardSeconds,
                skipBackwardSeconds = settings.skipBackwardSeconds,
                onDismiss = {
                    chrome.hideOsd()
                    scrubPreview = null
                },
                onInteract = { chrome.keepAlive() },
                onScrubPreviewChange = { scrubPreview = it },
                onScrubbingChange = { scrubbing = it },
                onScrubBarBottomInset = { scrubBarBottomInset = it },
                trickplayFor = viewModel::trickplayFor,
                audioFocusRequester = audioFocus,
                subtitleFocusRequester = subtitleFocus,
                settingsFocusRequester = settingsFocus,
                scrubberFocusRequester = scrubberFocus,
                osdSkipFocusRequester = osdSkipFocus,
                showSkipInOsd = chrome.skipInOsd(state.currentSegment != null),
                onSkip = { viewModel.skipCurrentSegment() },
                focusEnabled = chrome.panel == Panel.NONE
            )
        }
        AnimatedVisibility(
            visible = chrome.panel == Panel.CHAPTERS && !showNextUpOverlay,
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
                active = chrome.panel == Panel.CHAPTERS,
                onClose = ::closePanel
            )
        }

        AnimatedVisibility(
            visible = chrome.skipPillShowing(state.currentSegment != null),
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

        if (chrome.osdVisible && chrome.panel == Panel.NONE && scrubPreview != null && scrubBarBottomInset > 0.dp) {
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

        SidePanel(
            visible = chrome.panel == Panel.AUDIO,
            width = TrackPanelWidth,
            modifier = Modifier.zIndex(3f)
        ) { active ->
            TrackPanel(
                title = "Audio",
                options = state.audioTracks,
                allowOff = false,
                onSelect = { id ->
                    id?.let(viewModel::selectAudio)
                    closePanel()
                },
                active = active,
                onClose = ::closePanel
            )
        }
        SidePanel(
            visible = chrome.panel == Panel.SUBTITLE,
            width = TrackPanelWidth,
            modifier = Modifier.zIndex(3f)
        ) { active ->
            TrackPanel(
                title = "Subtitles",
                options = state.subtitleTracks,
                allowOff = true,
                onSelect = { id ->
                    viewModel.selectSubtitle(id)
                    closePanel()
                },
                active = active,
                onClose = ::closePanel
            )
        }
        SidePanel(
            visible = chrome.panel == Panel.SETTINGS,
            width = PlayerSettingsPanelWidth,
            modifier = Modifier.zIndex(3f)
        ) { active ->
            PlayerSettingsPanel(
                subtitleDelayMs = state.subtitleDelayMs,
                subtitleAppearance = settings.subtitleAppearance,
                onSubtitleSize = { viewModel.cycleSubtitleSize(it) },
                onSubtitleColour = { viewModel.cycleSubtitleColour(it) },
                onSubtitleBackground = { viewModel.toggleSubtitleBackground() },
                onSubtitleBackgroundStyle = { viewModel.cycleSubtitleBackgroundStyle(it) },
                onSubtitleBackgroundFill = { viewModel.cycleSubtitleBackgroundFill(it) },
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
                    chrome.enterSubtitleAdjust()
                },
                onSpeed = { viewModel.setSpeed(it) },
                onAudioBoost = { viewModel.setAudioBoost(it) },
                onNightMode = { viewModel.setNightMode(it) },
                onSleep = { viewModel.setSleep(it) },
                onToggleStatsForNerds = { viewModel.toggleStatsForNerds() },
                onEnterPip = pipState::enterPip,
                pipSupported = viewModel.pictureInPictureSupported,
                focusSubtitleDelay = chrome.returningFromSubtitleAdjust,
                onFocusSubtitleDelayConsumed = chrome::consumeSubtitleAdjustReturn,
                active = active,
                onClose = ::closePanel
            )
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
        if (chrome.subtitleAdjust) {
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
                                chrome.exitSubtitleAdjust()
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
                accumMs = chrome.quickSkipMs,
                visible = chrome.quickSkipVisible && chrome.videoHasFocus
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
