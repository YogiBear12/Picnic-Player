package app.picnic.player.data.playback

import app.picnic.player.data.settings.SegmentAction
import app.picnic.player.playback.SleepMode

/**
 * Pure playback-tick decision, extracted from `PlayerViewModel.pushState()`
 * (runs ~2 Hz). Given a snapshot of player position/phase, media segments,
 * skip settings, sleep mode and the previous UI state, it decides:
 *
 * - segment auto-skip and the ask-to-skip button ([PlaybackTickDecision.currentSegment]),
 * - the outro / next-up overlay trigger,
 * - sleep-timer end-of-episode/queue pausing,
 * - the derived player-mirror fields for the next UI state.
 *
 * The function is player-free so it is unit-testable; the caller applies the
 * side-effect requests ([PlaybackTickDecision.autoSkipToMs] etc.) to the real
 * ExoPlayer and folds the state fields into `_state`.
 */
enum class PlaybackPhase { IDLE, BUFFERING, READY, ENDED, OTHER }

/** Snapshot of the per-segment-kind skip actions + outro next-up preference. */
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
    // Side-effect requests for the caller to apply to the real player/session.
    val presentFirstFrame: Boolean = false,
    val autoSkipToMs: Long? = null,
    val markSkippedId: String? = null,
    val pauseForSleep: Boolean = false,
    val setOutroNextUpShown: Boolean = false,
    // Derived fields folded into the next UI state.
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

/** Tolerance for treating an outro's end as "runs to the end of the file". */
const val OUTRO_END_TOLERANCE_MS = 3_000L

fun playbackTick(input: PlaybackTickInput): PlaybackTickDecision {
    val presentFirstFrame = !input.hasPresentedFirstFrame &&
        (input.phase == PlaybackPhase.READY || input.phase == PlaybackPhase.ENDED)
    val presentedFirstFrame = input.hasPresentedFirstFrame || presentFirstFrame

    val pos = input.positionMs
    val active = input.segments.firstOrNull {
        it.kind != SegmentKind.UNKNOWN && pos in it.startMs until it.endMs
    }

    val segmentAction = active?.let {
        when (it.kind) {
            SegmentKind.INTRO -> input.settings.introAction
            SegmentKind.RECAP -> input.settings.recapAction
            SegmentKind.OUTRO -> input.settings.outroAction
            SegmentKind.PREVIEW -> input.settings.previewAction
            SegmentKind.COMMERCIAL -> input.settings.commercialAction
            SegmentKind.UNKNOWN -> SegmentAction.DO_NOT_SKIP
        }
    }

    val autoSkip = active != null &&
        active.id !in input.autoSkippedIds &&
        segmentAction == SegmentAction.SKIP_AUTOMATICALLY
    val autoSkipToMs = if (autoSkip) active!!.endMs else null
    val markSkippedId = if (autoSkip) active!!.id else null
    val effectiveSkipped = if (markSkippedId != null) input.autoSkippedIds + markSkippedId else input.autoSkippedIds

    // The skip button shows only for ask-to-skip segments not already skipped away, and only once
    // the video is on screen — during load the position sits at 0, so a segment starting at 0 would
    // otherwise offer a skip over the loading spinner.
    val nextSegment = active?.takeIf {
        segmentAction == SegmentAction.ASK_TO_SKIP && it.id !in effectiveSkipped && presentedFirstFrame
    }

    val endedNow = input.phase == PlaybackPhase.ENDED

    // Opt-in outro mode: overlay triggers when playback enters an OUTRO that is NOT auto-skip and
    // runs to the end of the file (content after the outro falls back to the at-end overlay).
    val outroRunsToEnd = active?.kind == SegmentKind.OUTRO &&
        segmentAction != SegmentAction.SKIP_AUTOMATICALLY &&
        input.durationMs > 0 &&
        active.endMs >= input.durationMs - OUTRO_END_TOLERANCE_MS
    val outroTriggered = !input.prevEndedAwaitingNext &&
        !input.outroNextUpShown &&
        input.settings.displayNextUpDuringOutro &&
        outroRunsToEnd &&
        input.hasNextUp

    // Once the video reaches ENDED while videoStillPlaying was true (outro mode), clear it.
    val videoStillNow = when {
        outroTriggered -> true
        endedNow && input.prevVideoStillPlaying -> false
        else -> input.prevVideoStillPlaying
    }

    // Sleep end-of-X modes win over next-up: at the relevant end, pause and suppress next-up.
    val pauseForSleep = endedNow &&
        when (input.sleepMode) {
            SleepMode.END_OF_EPISODE -> true
            SleepMode.END_OF_QUEUE -> !input.hasNextUp
            else -> false
        }

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
        endedAwaitingNext = input.prevEndedAwaitingNext ||
            ((endedNow || outroTriggered) && !pauseForSleep),
        videoStillPlaying = videoStillNow
    )
}
