package app.picnic.player.data.playback

import app.picnic.player.data.settings.SegmentAction
import app.picnic.player.playback.SleepMode

enum class PlaybackPhase { IDLE, BUFFERING, READY, ENDED, OTHER }

data class PlaybackTickSettings(
    val introAction: SegmentAction,
    val recapAction: SegmentAction,
    val outroAction: SegmentAction,
    val previewAction: SegmentAction,
    val commercialAction: SegmentAction,
    val displayNextUpDuringOutro: Boolean
)

data class PlaybackTickInput(
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val isPlaying: Boolean,
    val phase: PlaybackPhase,
    val segments: List<MediaSegment>,
    val settings: PlaybackTickSettings,
    val sleepMode: SleepMode,
    val hasNextUp: Boolean,
    val hasPresentedFirstFrame: Boolean,
    val autoSkippedIds: Set<String>,
    val outroNextUpShown: Boolean,
    val prevEndedAwaitingNext: Boolean,
    val prevVideoStillPlaying: Boolean,
    val hasError: Boolean
)

data class PlaybackTickDecision(
    val presentFirstFrame: Boolean = false,
    val autoSkipToMs: Long? = null,
    val markSkippedId: String? = null,
    val pauseForSleep: Boolean = false,
    val setOutroNextUpShown: Boolean = false,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val buffering: Boolean,
    val isLoading: Boolean,
    val currentSegment: MediaSegment?,
    val endedAwaitingNext: Boolean,
    val videoStillPlaying: Boolean
)

const val OUTRO_END_TOLERANCE_MS = 3_000L
private const val SEGMENT_ENTRY_WINDOW_MS = 2_000L

fun MediaSegment.enteredAtStart(positionMs: Long): Boolean = positionMs - startMs <= SEGMENT_ENTRY_WINDOW_MS

fun playbackTick(input: PlaybackTickInput): PlaybackTickDecision {
    val presentFirstFrame = !input.hasPresentedFirstFrame &&
        (input.phase == PlaybackPhase.READY || input.phase == PlaybackPhase.ENDED)
    val presentedFirstFrame = input.hasPresentedFirstFrame || presentFirstFrame

    val pos = input.positionMs
    val active = input.segments.firstOrNull { pos in it.startMs until it.endMs }

    val segmentAction = active?.let {
        when (it.kind) {
            SegmentKind.INTRO -> input.settings.introAction
            SegmentKind.RECAP -> input.settings.recapAction
            SegmentKind.OUTRO -> input.settings.outroAction
            SegmentKind.PREVIEW -> input.settings.previewAction
            SegmentKind.COMMERCIAL -> input.settings.commercialAction
        }
    }

    val autoSkip = active != null &&
        active.id !in input.autoSkippedIds &&
        segmentAction == SegmentAction.SKIP_AUTOMATICALLY
    val skipTarget = active?.takeIf { autoSkip }
    val autoSkipToMs = skipTarget?.endMs
    val markSkippedId = skipTarget?.id
    val effectiveSkipped = if (markSkippedId != null) input.autoSkippedIds + markSkippedId else input.autoSkippedIds

    val nextSegment = active?.takeIf {
        segmentAction == SegmentAction.ASK_TO_SKIP && it.id !in effectiveSkipped && presentedFirstFrame
    }

    val endedNow = input.phase == PlaybackPhase.ENDED

    val outroRunsToEnd = active?.kind == SegmentKind.OUTRO &&
        segmentAction != SegmentAction.SKIP_AUTOMATICALLY &&
        input.durationMs > 0 &&
        active.endMs >= input.durationMs - OUTRO_END_TOLERANCE_MS
    val outroTriggered = !input.prevEndedAwaitingNext &&
        !input.outroNextUpShown &&
        input.settings.displayNextUpDuringOutro &&
        outroRunsToEnd &&
        active.enteredAtStart(pos) &&
        input.hasNextUp

    val videoStillNow = when {
        outroTriggered -> true
        endedNow && input.prevVideoStillPlaying -> false
        else -> input.prevVideoStillPlaying
    }

    val pauseForSleep = endedNow && input.sleepMode == SleepMode.END_OF_EPISODE && input.hasNextUp

    return PlaybackTickDecision(
        presentFirstFrame = presentFirstFrame,
        autoSkipToMs = autoSkipToMs,
        markSkippedId = markSkippedId,
        pauseForSleep = pauseForSleep,
        setOutroNextUpShown = outroTriggered,
        isPlaying = input.isPlaying,
        positionMs = input.positionMs.coerceAtLeast(0),
        durationMs = input.durationMs.coerceAtLeast(0),
        bufferedMs = input.bufferedMs.coerceAtLeast(0),
        buffering = input.phase == PlaybackPhase.BUFFERING || input.phase == PlaybackPhase.IDLE,
        isLoading = !presentedFirstFrame && !input.hasError,
        currentSegment = nextSegment,
        endedAwaitingNext = input.prevEndedAwaitingNext || endedNow || outroTriggered,
        videoStillPlaying = videoStillNow
    )
}
