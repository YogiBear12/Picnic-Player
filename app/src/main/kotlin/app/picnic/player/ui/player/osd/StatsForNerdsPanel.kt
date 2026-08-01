@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player.osd

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.playback.subtitleRenderRange
import app.picnic.player.ui.player.PlayerUiState
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val PanelDimScrim = Color.Transparent
private val PanelGlassFill = Color(0x80181E24)
private val PanelCornerRadius = 20.dp
private val PanelEdgeInset = 28.dp
private val ContentInset = 16.dp
private val RowInnerPadding = 14.dp

@Composable
fun StatsForNerdsPanel(
    state: PlayerUiState,
    player: ExoPlayer?
) {
    val context = LocalContext.current
    val display = remember(context) {
        try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        } catch (ex: Exception) {
            null
        }
    }
    val displayMode by produceState<String?>("Detecting...") {
        while (isActive) {
            value = display?.mode?.let {
                val rate = String.format(Locale.getDefault(), "%.3f", it.refreshRate)
                "${it.physicalWidth}x${it.physicalHeight}@${rate}fps"
            }
            delay(10.seconds)
        }
    }

    val memoryUsed by produceState("") {
        withContext(Dispatchers.Default) {
            while (isActive) {
                val runtime = Runtime.getRuntime()
                val total = runtime.totalMemory()
                val free = runtime.freeMemory()
                val used = total - free
                val totalMemory = formatBytes(total)
                val usedMemory = formatBytes(used)
                value = "$usedMemory / $totalMemory"
                delay(2.seconds)
            }
        }
    }

    val droppedFrames by produceState(0) {
        while (isActive) {
            value = player?.videoDecoderCounters?.droppedBufferCount ?: 0
            delay(2.seconds)
        }
    }

    val videoFormat = player?.videoFormat
    val audioFormat = player?.audioFormat

    val mediaSource = state.mediaSource
    val videoStream = mediaSource?.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }
    val audioStream = mediaSource?.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO && it.index == state.selectedAudioId?.toIntOrNull() }
        ?: mediaSource?.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }

    Row(
        Modifier
            .fillMaxSize()
            .background(PanelDimScrim)
    ) {
        Column(
            Modifier
                .width(440.dp)
                .padding(top = PanelEdgeInset, bottom = PanelEdgeInset, start = PanelEdgeInset)
                .clip(RoundedCornerShape(PanelCornerRadius))
                .background(PanelGlassFill)
                .padding(vertical = 20.dp)
        ) {
            Column(
                Modifier.padding(horizontal = ContentInset),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Playback Info
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader("Playback Info")

                    val playMethodLabel = when (state.playMethod) {
                        app.picnic.player.data.playback.PlayMethodKind.DIRECT_PLAY -> "Direct Play"
                        app.picnic.player.data.playback.PlayMethodKind.DIRECT_STREAM -> "Direct Stream"
                        app.picnic.player.data.playback.PlayMethodKind.TRANSCODE -> "Transcode"
                        else -> "Unknown"
                    }

                    val playbackInfoRows = mutableListOf<Pair<String, Any?>>(
                        "Player" to "ExoPlayer",
                        "Playback" to playMethodLabel
                    )

                    val reasons = state.transcodingInfo?.transcodeReasons.orEmpty()
                    if (reasons.isNotEmpty()) {
                        val reasonLabel = if (
                            state.playMethod == app.picnic.player.data.playback.PlayMethodKind.DIRECT_STREAM
                        ) {
                            "Remux reason"
                        } else {
                            "Transcode reason"
                        }
                        playbackInfoRows.add(reasonLabel to reasons.joinToString(", "))
                    }
                    playbackInfoRows.add("Dropped frames" to droppedFrames)

                    SimpleTable(
                        playbackInfoRows,
                        keyWidth = 140.dp
                    )
                }

                val vDirect = state.playMethod == app.picnic.player.data.playback.PlayMethodKind.DIRECT_PLAY || state.transcodingInfo?.isVideoDirect == true
                val aDirect = state.playMethod == app.picnic.player.data.playback.PlayMethodKind.DIRECT_PLAY || state.transcodingInfo?.isAudioDirect == true

                val vCodecOrig = listOfNotNull(videoStream?.codec?.uppercase(), videoStream?.profile).joinToString(" ")
                val aCodecOrig = audioStream?.codec?.uppercase()

                val finalVideoCodec = if (vDirect) {
                    if (vCodecOrig.isNotBlank()) "$vCodecOrig (direct)" else "Unknown (direct)"
                } else {
                    val tcCodec = state.transcodingInfo?.videoCodec?.uppercase() ?: "Unknown"
                    "$tcCodec (transcode)"
                }

                val finalAudioCodec = if (aDirect) {
                    if (!aCodecOrig.isNullOrBlank()) "$aCodecOrig (direct)" else "Unknown (direct)"
                } else {
                    val tcCodec = state.transcodingInfo?.audioCodec?.uppercase() ?: "Unknown"
                    "$tcCodec (transcode)"
                }

                val finalVideoBitrate = if (vDirect) {
                    videoStream?.bitRate?.toLong()?.takeIf { it > 0 } ?: videoFormat?.bitrate?.toLong()?.takeIf { it > 0 }
                } else {
                    videoFormat?.bitrate?.toLong()?.takeIf { it > 0 } ?: state.transcodingInfo?.bitrate?.toLong()
                }

                val finalAudioChannels = if (aDirect) {
                    audioStream?.channels?.takeIf { it > 0 } ?: audioFormat?.channelCount?.takeIf { it > 0 }
                } else {
                    state.transcodingInfo?.audioChannels?.takeIf { it > 0 } ?: audioFormat?.channelCount?.takeIf { it > 0 }
                }

                val finalAudioBitrate = if (aDirect) {
                    audioStream?.bitRate?.toLong()?.takeIf { it > 0 } ?: audioFormat?.bitrate?.toLong()?.takeIf { it > 0 }
                } else {
                    audioFormat?.bitrate?.toLong()?.takeIf { it > 0 }
                }

                // 2. Video Info
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader("Video Info")
                    SimpleTable(
                        listOf(
                            "Video resolution" to "${videoFormat?.width ?: 0}x${videoFormat?.height ?: 0}",
                            "Video codec" to finalVideoCodec,
                            "Video bitrate" to (finalVideoBitrate?.let { formatBitrate(it) } ?: "Unknown"),
                            "Dynamic range type" to (videoStream?.videoRangeType?.name ?: "Unknown"),
                            "Subtitle range" to subtitleRenderRange(videoFormat).name
                        ),
                        keyWidth = 140.dp
                    )
                }

                // 3. Audio Info
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader("Audio Info")
                    SimpleTable(
                        listOf(
                            "Audio codec" to finalAudioCodec,
                            "Audio channels" to (finalAudioChannels ?: "Unknown"),
                            "Audio bitrate" to (finalAudioBitrate?.let { formatBitrate(it) } ?: "Unknown")
                        ),
                        keyWidth = 140.dp
                    )
                }

                // (Tracks removed)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PanelHeader(title: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentInset)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = RowInnerPadding)
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(horizontal = RowInnerPadding)
    )
}

@Composable
private fun SimpleTable(
    rows: List<Pair<String, Any?>>,
    modifier: Modifier = Modifier,
    keyWidth: Dp = 100.dp
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(horizontal = RowInnerPadding)
    ) {
        rows.forEach {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = it.first,
                    modifier = Modifier.width(keyWidth),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = it.second.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GiB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.2f MiB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.2f KiB", kb)
        else -> "$bytes B"
    }
}

private fun formatBitrate(bitrate: Long): String {
    val kbps = bitrate / 1000.0
    val mbps = kbps / 1000.0
    return when {
        mbps >= 1.0 -> String.format(Locale.getDefault(), "%.2f Mbps", mbps)
        else -> String.format(Locale.getDefault(), "%.0f Kbps", kbps)
    }
}
