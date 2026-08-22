@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.picnic.player.data.media.PlaylistQueue
import app.picnic.player.playback.videoDisplayHints
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.player.osd.ChaptersPanel
import app.picnic.player.ui.player.osd.ModernOsd
import app.picnic.player.ui.player.osd.SkipIndicator
import app.picnic.player.ui.player.osd.SkipIndicatorState
import app.picnic.player.ui.player.osd.SkipSegmentButton
import app.picnic.player.ui.player.osd.StatsForNerdsPanel

private const val SkipPillEntryWindowMs = 2_000L

@Composable
fun PlayerScreen(
    itemId: String,
    startTicks: Long?,
    mediaSourceId: String? = null,
    queue: PlaylistQueue? = null,
    onExit: () -> Unit,
    onPlayNext: (nextItemId: String) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    PublishBackdrop(null)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { viewModel.load(itemId, startTicks, mediaSourceId, queue) }

    val view = LocalView.current
    SideEffect { view.keepScreenOn = state.isPlaying }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false } }

    val pulse = rememberPlayerPulse()
    val scrub = rememberPlayerScrub(viewModel.player)
    val chrome = rememberPlayerChrome(
        osdHideSeconds = settings.osdHideSeconds,
        scrubbing = scrub.active,
        isPlaying = state.isPlaying,
        segment = state.currentSegment
    )
    val osdFocus = remember { PlayerOsdFocus() }
    val subtitleAdjust = rememberSubtitleDelayAdjust(
        active = chrome.subtitleAdjust,
        delayMs = state.subtitleDelayMs,
        onDelayChange = viewModel::setSubtitleDelayMs
    )

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

    val showNextUpOverlay = state.endedAwaitingNext && state.nextUp != null && !inPipMode

    LaunchedEffect(showNextUpOverlay) { chrome.onNextUpVisibleChanged(showNextUpOverlay) }
    LaunchedEffect(inPipMode) { chrome.onPipModeChanged(inPipMode) }

    fun togglePlay() {
        val willPlay = !state.isPlaying
        viewModel.playPause()
        pulse.show(willPlay)
    }

    fun closePanel() {
        chrome.closePanel()
    }

    fun exitPlayer() {
        viewModel.endViewing()
        onExit()
    }

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

    LaunchedEffect(chrome.panel, chrome.osdVisible) {
        if (chrome.panel != Panel.NONE || !chrome.osdVisible) return@LaunchedEffect
        osdFocus.seedFor(chrome.lastPanel)
        chrome.consumeLastPanel()
    }

    LaunchedEffect(chrome.videoHasFocus, chrome.skipPillDismissed) {
        if (chrome.videoHasFocus) osdFocus.requestVideo()
    }
    LaunchedEffect(state.currentSegment) {
        val segment = state.currentSegment
        if (segment != null) {
            chrome.onSegmentChanged(
                segmentActive = true,
                enteredAtStart = state.positionMs - segment.startMs <= SkipPillEntryWindowMs
            )
        } else {
            chrome.onSegmentChanged(segmentActive = false, enteredAtStart = false)
            if (chrome.videoHasFocus) osdFocus.requestVideo()
        }
    }

    LaunchedEffect(state.endedAwaitingNext) {
        if (state.endedAwaitingNext && state.nextUp == null) {
            exitPlayer()
        }
    }

    val onNextUpBack: () -> Unit = {
        if (state.videoStillPlaying) viewModel.dismissNextUp() else exitPlayer()
    }

    BackHandler {
        subtitleAdjust.release()
        when (chrome.onBack()) {
            BackOutcome.Handled -> Unit
            BackOutcome.ClosedOsd -> scrub.cancel()
            BackOutcome.NextUp -> onNextUpBack()
            BackOutcome.ExitPlayer -> exitPlayer()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(osdFocus.video)
            .focusable(chrome.videoHasFocus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (!chrome.videoKeysActive) return@onKeyEvent false
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
        NextUpScrim(state.nextUp?.backdropUrl, showNextUpOverlay)

        PlayerStage(
            viewModel = viewModel,
            state = state,
            subtitleAppearance = settings.subtitleAppearance,
            showNextUpOverlay = showNextUpOverlay,
            pipModifier = pipState.sourceRectModifier()
        )

        PlayerLoadingState(state)

        state.error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center)) }

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
                    scrub.clearPreview()
                },
                onInteract = { chrome.keepAlive() },
                onScrubPreviewChange = scrub::onPreviewChange,
                onScrubbingChange = scrub::onActiveChange,
                onScrubBarBottomInset = scrub::onBarBottomInset,
                trickplayFor = viewModel::trickplayFor,
                audioFocusRequester = osdFocus.audio,
                subtitleFocusRequester = osdFocus.subtitle,
                settingsFocusRequester = osdFocus.settings,
                scrubberFocusRequester = osdFocus.scrubber,
                osdSkipFocusRequester = osdFocus.osdSkip,
                showSkipInOsd = chrome.skipInOsd,
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
            visible = chrome.skipPillShowing,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 56.dp, bottom = 56.dp)
                .zIndex(2f),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            state.currentSegment?.let { segment ->
                LaunchedEffect(segment.kind) {
                    osdFocus.skipPill.requestFocusWhenAttached()
                }
                SkipSegmentButton(
                    kind = segment.kind,
                    onClick = { viewModel.skipCurrentSegment() },
                    modifier = Modifier
                        .focusRequester(osdFocus.skipPill)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.Back -> {
                                    chrome.dismissSkipPill()
                                    true
                                }
                                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                                else -> false
                            }
                        }
                )
            }
        }

        val preview = scrub.preview
        if (chrome.osdVisible && chrome.panel == Panel.NONE && preview != null && scrub.barBottomInset > 0.dp) {
            TrickplayPreviewOverlay(preview, scrub.barBottomInset)
        }

        TrackSidePanel(
            visible = chrome.panel == Panel.AUDIO,
            title = "Audio",
            options = state.audioTracks,
            allowOff = false,
            onSelect = { id ->
                id?.let(viewModel::selectAudio)
                closePanel()
            },
            onClose = ::closePanel
        )
        TrackSidePanel(
            visible = chrome.panel == Panel.SUBTITLE,
            title = "Subtitles",
            options = state.subtitleTracks,
            allowOff = true,
            onSelect = { id ->
                viewModel.selectSubtitle(id)
                closePanel()
            },
            onClose = ::closePanel
        )
        PlayerSettingsSidePanel(
            visible = chrome.panel == Panel.SETTINGS,
            viewModel = viewModel,
            state = state,
            subtitleAppearance = settings.subtitleAppearance,
            focusSubtitleDelay = chrome.returningFromSubtitleAdjust,
            onFocusSubtitleDelayConsumed = chrome::consumeSubtitleAdjustReturn,
            onAdjustSubtitleDelay = chrome::enterSubtitleAdjust,
            onEnterPip = pipState::enterPip,
            onClose = ::closePanel
        )

        PlayerNotice(state.notice, viewModel::clearNotice)

        if (chrome.subtitleAdjust) {
            SubtitleDelayOverlay(
                adjust = subtitleAdjust,
                delayMs = state.subtitleDelayMs,
                onExit = chrome::exitSubtitleAdjust
            )
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

        PlayPausePulse(pulse)

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
