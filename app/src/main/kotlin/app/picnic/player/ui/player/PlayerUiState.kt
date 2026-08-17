package app.picnic.player.ui.player

import androidx.media3.common.text.Cue
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.playback.MediaSegment
import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.SleepTimerState
import app.picnic.player.ui.browse.ShortDateFormat
import app.picnic.player.ui.browse.TICKS_PER_MINUTE
import app.picnic.player.ui.player.osd.TrackSupport
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.TranscodingInfo

sealed interface PlayerNavEvent {
    data object Exit : PlayerNavEvent

    data class PlayNext(val itemId: String) : PlayerNavEvent
}

data class TrackOption(
    val id: String,
    val label: String?,
    val language: String?,
    val displayLanguage: String,
    val selected: Boolean
)

data class TrickplayPreview(
    val frame: TrickplayFrame,
    val fraction: Float
)

data class ChapterMark(
    val index: Int,
    val title: String,
    val startMs: Long,
    val imageUrl: String?
)

data class NextUpItem(
    val id: String,
    val title: String,
    val seasonEpisode: String,
    val meta: String,
    val overview: String,
    val backdropUrl: String?,
    val thumbUrl: String?
)

data class PlayerUiState(
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
    val nextUp: NextUpItem? = null,
    val endedAwaitingNext: Boolean = false,
    val videoStillPlaying: Boolean = false,
    val chapters: List<ChapterMark> = emptyList(),
    val subtitleDelayMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val audioBoost: AudioBoost = AudioBoost.OFF,
    val nightMode: NightMode = NightMode.OFF,
    val sleep: SleepTimerState = SleepTimerState(),
    val showStatsForNerds: Boolean = false,
    val playMethod: PlayMethodKind? = null,
    val mediaSourceId: String? = null,
    val mediaSource: MediaSourceInfo? = null,
    val transcodingInfo: TranscodingInfo? = null,
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val estimatedBitrate: Long? = null,
    val notice: String? = null,
    val qualityOptions: List<QualityOption> = emptyList(),
    val streamRung: QualityRung? = null,
    val tracks: List<TrackSupport> = emptyList()
) {
    val activeQuality: QualityOption? get() = when {
        playMethod != PlayMethodKind.TRANSCODE -> QualityOption.Original
        streamRung != null -> QualityOption.Transcode(streamRung)
        else -> null
    }
}

internal fun buildNextUpItem(session: UserSession, item: BaseItemDto): NextUpItem {
    val seasonNum = item.parentIndexNumber
    val epNum = item.indexNumber
    val seasonEpisode = if (seasonNum != null && epNum != null) "S$seasonNum E$epNum" else ""

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
        title = item.name.orEmpty(),
        seasonEpisode = seasonEpisode,
        meta = metaParts.joinToString(" • "),
        overview = item.overview.orEmpty(),
        backdropUrl = JellyfinImages.backdrop(session, item),
        thumbUrl = JellyfinImages.thumb(session, item)
    )
}
