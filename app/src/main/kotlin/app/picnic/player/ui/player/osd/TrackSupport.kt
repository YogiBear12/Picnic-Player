@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player.osd

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks

data class TrackSupport(
    val id: String?,
    val type: String,
    val supported: String,
    val selected: Boolean,
    val labels: List<String>,
    val codecs: String?,
    val format: Format
)

fun checkForSupport(tracks: Tracks): List<TrackSupport> = tracks.groups.flatMap { group ->
    buildList {
        val typeStr = when (group.type) {
            C.TRACK_TYPE_VIDEO -> "VIDEO"
            C.TRACK_TYPE_AUDIO -> "AUDIO"
            C.TRACK_TYPE_TEXT -> "TEXT"
            C.TRACK_TYPE_METADATA -> "METADATA"
            else -> "UNKNOWN"
        }
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val labels =
                format.labels
                    .map {
                        if (it.language != null) {
                            "${it.value} (${it.language})"
                        } else {
                            it.value
                        }
                    } +
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        listOf("res=${format.width}x${format.height}")
                    } else if (group.type == C.TRACK_TYPE_AUDIO) {
                        listOf("channels=${format.channelCount}", "lang=${format.language}")
                    } else if (group.type == C.TRACK_TYPE_TEXT) {
                        listOf("lang=${format.language}")
                    } else {
                        listOf()
                    }

            val supportInt = group.getTrackSupport(i)
            val reasonStr = when (supportInt) {
                C.FORMAT_HANDLED -> "HANDLED"
                C.FORMAT_EXCEEDS_CAPABILITIES -> "EXCEEDS_CAPABILITIES"
                C.FORMAT_UNSUPPORTED_DRM -> "UNSUPPORTED_DRM"
                C.FORMAT_UNSUPPORTED_SUBTYPE -> "UNSUPPORTED_SUBTYPE"
                C.FORMAT_UNSUPPORTED_TYPE -> "UNSUPPORTED_TYPE"
                else -> "UNKNOWN"
            }

            add(
                TrackSupport(
                    id = if (format.id != null) "${format.id} (${format.idAsInt})" else "null (null)",
                    type = typeStr,
                    supported = reasonStr,
                    selected = group.isSelected,
                    labels = labels,
                    codecs = format.codecs,
                    format = format
                )
            )
        }
    }
}

private val Format.idAsInt: Int
    get() {
        return try {
            id?.toInt() ?: -1
        } catch (e: Exception) {
            -1
        }
    }
