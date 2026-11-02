package app.picnic.player.ui.player

import androidx.media3.common.text.Cue
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.playback.MediaSegment
import app.picnic.player.data.playback.NextUpPhase
import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState
import app.picnic.player.playback.VideoOutput
import app.picnic.player.ui.browse.ShortDateFormat
import app.picnic.player.ui.browse.TICKS_PER_MINUTE
import app.picnic.player.ui.player.osd.TrackSupport
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.TranscodingInfo

sealed interface PlayerNavEvent {
    data object Exit : PlayerNavEvent

    data class PlayNext(val itemId: String) : PlayerNavEvent

    data object ToLobby : PlayerNavEvent
}

data class TrackOption(
    val id: String,
    val label: String?,
    val language: String?,
    val displayLanguage: String,
    val chips: List<String> = emptyList(),
    val selected: Boolean
)

data class ChapterMark(
    val index: Int,
    val title: String,
    val startMs: Long,
    val imageUrl: String?
)

data class NextUpItem(
    val id: String,
    val logoName: String,
    val episodeLine: String?,
    val meta: String,
    val overview: String,
    val backdropUrl: String?,
    val logoUrl: String?
)

data class PlayerUiState(
    val nowPlayingItem: BaseItemDto? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val buffering: Boolean = true,
    val error: String? = null,
    val isLoading: Boolean = true,
    val backdropUrl: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val selectedAudioId: String? = null,
    val selectedSubtitleId: String? = null,
    val subtitleCues: List<Cue> = emptyList(),
    val currentSegment: MediaSegment? = null,
    val segments: List<MediaSegment> = emptyList(),
    val nextUp: NextUpItem? = null,
    val nextUpPhase: NextUpPhase = NextUpPhase.NONE,
    val chapters: List<ChapterMark> = emptyList(),
    val subtitleDelayMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val audioBoost: AudioBoost = AudioBoost.OFF,
    val nightMode: NightMode = NightMode.OFF,
    val sleep: SleepTimerState = SleepTimerState(),
    val stillWatching: Boolean = false,
    val showStatsForNerds: Boolean = false,
    val playMethod: PlayMethodKind? = null,
    val mediaSourceId: String? = null,
    val mediaSource: MediaSourceInfo? = null,
    val transcodingInfo: TranscodingInfo? = null,
    val videoOutput: VideoOutput? = null,
    val estimatedBitrate: Long? = null,
    val notice: String? = null,
    val inRoom: Boolean = false,
    val qualityOptions: List<QualityOption> = emptyList(),
    val streamRung: QualityRung? = null,
    val tracks: List<TrackSupport> = emptyList(),
    val directPlayBlockedBy: List<String> = emptyList()
) {
    val isEpisode: Boolean get() = nowPlayingItem?.type == BaseItemKind.EPISODE

    val activeQuality: QualityOption? get() = when {
        playMethod != PlayMethodKind.TRANSCODE -> QualityOption.Original
        streamRung != null -> QualityOption.Transcode(streamRung)
        else -> null
    }

    val queuedNextUp: NextUpItem? get() = nextUp.takeIf { nextUpPhase == NextUpPhase.ENDED }

    fun nextUpExpiry(sleepMode: SleepMode): NextUpExpiry = when {
        stillWatching || nextUpPhase == NextUpPhase.NONE || nextUp == null -> NextUpExpiry.IGNORE
        sleepMode == SleepMode.END_OF_EPISODE -> NextUpExpiry.PAUSE_FOR_SLEEP
        else -> NextUpExpiry.AUTOPLAY
    }

    fun completedByNextUpExpiry(askStillWatching: Boolean): PlayerUiState = copy(nextUpPhase = NextUpPhase.ENDED, stillWatching = askStillWatching)
}

enum class NextUpExpiry { IGNORE, PAUSE_FOR_SLEEP, AUTOPLAY }

internal fun buildNextUpItem(session: UserSession, item: BaseItemDto): NextUpItem {
    val isEpisode = item.type == BaseItemKind.EPISODE
    val seasonNum = item.parentIndexNumber
    val epNum = item.indexNumber
    val seasonEpisode = if (seasonNum != null && epNum != null) "S$seasonNum E$epNum" else ""
    val episodeLine = listOf(seasonEpisode, item.name.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")

    val metaParts = mutableListOf<String>()
    item.premiereDate?.let { date ->
        runCatching { metaParts += date.format(ShortDateFormat) }
    }
    item.runTimeTicks?.takeIf { it > 0L }?.let { ticks ->
        metaParts += "${(ticks / TICKS_PER_MINUTE).coerceAtLeast(1)}m"
    }
    item.communityRating?.let { rating ->
        metaParts += "${"%.1f".format(rating)} ★"
    }

    return NextUpItem(
        id = item.id.toString(),
        logoName = (if (isEpisode) item.seriesName else null) ?: item.name.orEmpty(),
        episodeLine = episodeLine.takeIf { isEpisode && it.isNotEmpty() },
        meta = metaParts.joinToString(" • "),
        overview = item.overview.orEmpty(),
        backdropUrl = JellyfinImages.backdrop(session, item),
        logoUrl = JellyfinImages.logo(session, item)
    )
}
