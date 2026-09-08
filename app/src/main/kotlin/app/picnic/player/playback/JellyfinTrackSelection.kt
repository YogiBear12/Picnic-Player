@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

/**
 * Maps Jellyfin stream indices to ExoPlayer track groups. Always pass audio + subtitle
 * indices together; the UI lists Jellyfin [MediaStream]s, not ExoPlayer groups.
 */
object JellyfinTrackSelection {

    fun createTrackSelections(
        trackSelectionParams: TrackSelectionParameters,
        tracks: Tracks,
        supportsDirectPlay: Boolean,
        audioIndex: Int?,
        subtitleIndex: Int?,
        mediaStreams: List<MediaStream>
    ): TrackSelectionResult {
        val externalSubtitleCount = mediaStreams.externalSubtitleCount
        val paramsBuilder = trackSelectionParams.buildUpon()
        val groups = tracks.groups

        val subtitleSelected =
            if (subtitleIndex != null && subtitleIndex >= 0) {
                val subtitleIsExternal = mediaStreams.findExternalSubtitle(subtitleIndex) != null
                if (subtitleIsExternal || supportsDirectPlay) {
                    val chosenTrack =
                        if (subtitleIsExternal) {
                            groups.firstOrNull { group ->
                                group.type == C.TRACK_TYPE_TEXT &&
                                    group.isSupported &&
                                    (0 until group.length)
                                        .mapNotNull { group.getTrackFormat(it).id }
                                        .any { SideloadedTrackId.indexOf(it) == subtitleIndex }
                            }
                        } else {
                            val actualEmbeddedCount =
                                groups.count { group ->
                                    group.type == C.TRACK_TYPE_TEXT &&
                                        (0 until group.length)
                                            .mapNotNull { group.getTrackFormat(it).id }
                                            .none { SideloadedTrackId.isSideloaded(it) }
                                }
                            val indexToFind = calculateIndexToFind(subtitleIndex, externalSubtitleCount)
                            groups.firstOrNull { group ->
                                group.type == C.TRACK_TYPE_TEXT &&
                                    group.isSupported &&
                                    (0 until group.length)
                                        .filter {
                                            if (subtitleIsExternal) {
                                                SideloadedTrackId.isSideloaded(group.getTrackFormat(0).id)
                                            } else {
                                                !SideloadedTrackId.isSideloaded(group.getTrackFormat(0).id)
                                            }
                                        }
                                        .map { group.getTrackFormat(it).idAsInt }
                                        .contains(indexToFind)
                            }
                        }
                    chosenTrack?.let {
                        paramsBuilder
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                    }
                    chosenTrack != null
                } else {
                    false
                }
            } else {
                paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                true
            }

        val audioSelected =
            if (audioIndex != null && audioIndex >= 0 && supportsDirectPlay) {
                val indexToFind = calculateIndexToFind(audioIndex, externalSubtitleCount)
                val chosenTrack =
                    groups.firstOrNull { group ->
                        group.type == C.TRACK_TYPE_AUDIO &&
                            group.isSupported &&
                            (0 until group.length)
                                .map { group.getTrackFormat(it).idAsInt }
                                .contains(indexToFind)
                    }
                chosenTrack?.let {
                    paramsBuilder
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                }
                chosenTrack != null
            } else {
                true
            }

        return TrackSelectionResult(paramsBuilder.build(), audioSelected, subtitleSelected)
    }

    /** ExoPlayer: server stream index → container track id. */
    private fun calculateIndexToFind(serverIndex: Int, externalSubtitleCount: Int): Int = serverIndex - externalSubtitleCount + 1
}

data class TrackSelectionResult(
    val trackSelectionParameters: TrackSelectionParameters,
    val audioSelected: Boolean,
    val subtitleSelected: Boolean
) {
    val bothSelected: Boolean = audioSelected && subtitleSelected
}

val Format.idAsInt: Int?
    get() = id?.let {
        if (it.contains(":")) it.split(":").last().toIntOrNull() else it.toIntOrNull()
    }

val List<MediaStream>.externalSubtitleCount: Int
    get() = count { it.type == MediaStreamType.SUBTITLE && it.isExternal }

val List<MediaStream>.embeddedSubtitleCount: Int
    get() = count { it.type == MediaStreamType.SUBTITLE && !it.isExternal }

val List<MediaStream>.videoStream: MediaStream?
    get() = firstOrNull { it.type == MediaStreamType.VIDEO }

val List<MediaStream>.videoStreamCount: Int
    get() = count { it.type == MediaStreamType.VIDEO }

val List<MediaStream>.audioStreamCount: Int
    get() = count { it.type == MediaStreamType.AUDIO }

fun List<MediaStream>.findExternalSubtitle(subtitleIndex: Int): MediaStream? = firstOrNull {
    it.type == MediaStreamType.SUBTITLE &&
        (it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL || it.isExternal) &&
        it.index == subtitleIndex
}
