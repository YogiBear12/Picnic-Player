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
import androidx.compose.runtime.remember
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
import app.picnic.player.ui.player.osd.serverTranscode
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

private val OsdHorizontalPadding = 56.dp

private val TrickplayPreviewWidth = 180.dp
private val TrickplayPreviewHeight = 101.dp
private val TrickplayGapAboveScrubBar = 10.dp

private const val NextUpPlayerScale = 0.35f
private val NextUpPlayerInset = 56.dp

private const val SkipPillEntryWindowMs = 2_000L

private const val NOTICE_VISIBLE_MS = 3_000L

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

    val chrome = remember { PlayerChrome() }
    val pulse = rememberPlayerPulse()
    val scrub = rememberPlayerScrub(viewModel.player)
    val rootFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val scrubberFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val osdSkipFocus = remember { FocusRequester() }
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
        val target = when (chrome.lastPanel) {
            Panel.AUDIO -> audioFocus
            Panel.SUBTITLE -> subtitleFocus
            Panel.SETTINGS -> settingsFocus
            Panel.CHAPTERS, Panel.NONE -> scrubberFocus
        }
        target.requestFocusWhenAttached()
        chrome.consumeLastPanel()
    }

    LaunchedEffect(chrome.revealTick, chrome.osdVisible, chrome.panel, state.isPlaying, scrub.active) {
        if (chrome.osdVisible && chrome.panel == Panel.NONE && !scrub.active) {
            delay(settings.osdHideSeconds.toLong().coerceAtLeast(2) * 1000)
            chrome.hideOsd()
            scrub.clearPreview()
        }
    }
    LaunchedEffect(chrome.quickSkipTick) {
        if (chrome.quickSkipTick > 0) {
            delay(1000)
            chrome.endQuickSkip()
        }
    }
    LaunchedEffect(chrome.videoHasFocus, chrome.skipPillDismissed) {
        if (chrome.videoHasFocus) runCatching { rootFocus.requestFocus() }
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
            if (chrome.videoHasFocus) runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(state.currentSegment, chrome.skipPillDismissed) {
        if (state.currentSegment != null && !chrome.skipPillDismissed) {
            delay(10_000)
            chrome.dismissSkipPill()
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
        when (chrome.onBack(segmentActive = state.currentSegment != null)) {
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
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
        }

        val playerScale by animateFloatAsState(
            targetValue = if (showNextUpOverlay) NextUpPlayerScale else 1f,
            animationSpec = tween(400),
            label = "playerScale"
        )
        val insetFraction = ((1f - playerScale) / (1f - NextUpPlayerScale)).coerceIn(0f, 1f)
        val playerInset = NextUpPlayerInset * insetFraction

        val (bitmapCues, textCues) = state.subtitleCues.partition { it.bitmap != null }

        val presentationState = rememberPresentationState(viewModel.player)
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = playerInset, top = playerInset)
                .fillMaxSize(playerScale)
                .clip(RoundedCornerShape(12.dp * insetFraction))
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
            AndroidView(
                factory = { context -> viewModel.assOverlayView(context) },
                modifier = Modifier.resizeWithContentScale(
                    ContentScale.Fit,
                    presentationState.videoSizeDp
                )
            )
            AndroidView(
                factory = { context -> SubtitleView(context) },
                update = { it.setCues(bitmapCues) },
                onReset = { it.setCues(emptyList()) },
                modifier = Modifier.resizeWithContentScale(
                    ContentScale.Fit,
                    bitmapSubtitleFrame(bitmapCues, presentationState.videoSizeDp)
                )
            )
            val videoDynamicRange by viewModel.videoDynamicRange.collectAsStateWithLifecycle()
            val blackBars by viewModel.blackBars.collectAsStateWithLifecycle()
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
                    viewModel.attachSubtitleView(subtitleView, bottomPadding, videoDynamicRange)
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
                LaunchedEffect(segment.kind) {
                    skipFocus.requestFocusWhenAttached()
                }
                SkipSegmentButton(
                    kind = segment.kind,
                    onClick = { viewModel.skipCurrentSegment() },
                    modifier = Modifier
                        .focusRequester(skipFocus)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                                else -> false
                            }
                        }
                )
            }
        }

        val preview = scrub.preview
        if (chrome.osdVisible && chrome.panel == Panel.NONE && preview != null && scrub.barBottomInset > 0.dp) {
            val frame = preview.frame
            val previewW = TrickplayPreviewWidth
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
                        .offset {
                            IntOffset(
                                x = xOffset.roundToPx(),
                                y = -(scrub.barBottomInset + TrickplayGapAboveScrubBar).roundToPx()
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
            val serverTranscode = serverTranscode(
                state.playMethod,
                state.streamRung,
                state.transcodingInfo?.height,
                state.transcodingInfo?.bitrate
            )
            PlayerSettingsPanel(
                subtitleDelayMs = state.subtitleDelayMs,
                subtitleAppearance = settings.subtitleAppearance,
                onSubtitleSize = { viewModel.cycleSubtitleSize(it) },
                onSubtitleColour = { viewModel.cycleSubtitleColour(it) },
                onSubtitleBackground = { viewModel.cycleSubtitleBackground(it) },
                onSubtitleBackgroundFill = { viewModel.cycleSubtitleBackgroundFill(it) },
                onSubtitleArea = { viewModel.cycleSubtitleArea(it) },
                onSubtitleInset = { viewModel.stepSubtitleInset(it) },
                qualityOptions = state.qualityOptions,
                selectedQuality = state.activeQuality,
                qualitySummary = qualitySummary(state.playMethod, state.streamRung, serverTranscode),
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

        if (chrome.subtitleAdjust) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .focusRequester(subtitleAdjust.focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                                subtitleAdjust.release()
                                true
                            }
                            event.type != KeyEventType.KeyDown -> false
                            event.key == Key.DirectionLeft -> {
                                subtitleAdjust.holdEarlier()
                                true
                            }
                            event.key == Key.DirectionRight -> {
                                subtitleAdjust.holdLater()
                                true
                            }
                            event.key == Key.Back || event.key == Key.DirectionCenter || event.key == Key.Enter -> {
                                subtitleAdjust.release()
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
            visible = pulse.visible,
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
                    if (pulse.playing) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

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
