package app.picnic.player.ui.player.osd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.picnic.player.data.playback.TrickplayFrame
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws one trickplay sprite-sheet cell (crops [frame] out of its sheet). The caller
 * sizes it via [modifier]. The sheet is decoded off the main thread and resolved from
 * Coil's memory cache (sheets are prefetched by the player), then cropped on the GPU.
 *
 * Shared by the scrub-preview overlay and the chapters row so the crop logic lives once.
 */
@Composable
fun TrickplayCell(
    frame: TrickplayFrame,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Keyed on the SHEET url only — the same sheet serves many cells, so we avoid
    // re-decoding when only the cropped cell (column/row) changes.
    val sheet by produceState<ImageBitmap?>(initialValue = null, frame.url) {
        value = withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context)
                .data(frame.url)
                .size(CoilSize.ORIGINAL)
                .allowHardware(false) // we sample pixels via Canvas.drawImage
                .build()
            (context.imageLoader.execute(request) as? SuccessResult)
                ?.image?.toBitmap()?.asImageBitmap()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black) // letterbox/pillarbox bars + loading placeholder
    ) {
        val bitmap = sheet ?: return@Box
        val cellW = bitmap.width / frame.columns
        val cellH = bitmap.height / frame.rows
        if (cellW > 0 && cellH > 0) {
            Canvas(Modifier.fillMaxSize()) {
                // Fit the frame inside the (fixed-aspect) box, centered, preserving its native
                // aspect — non-16:9 content is letterboxed against the black bars, matching how
                // the player itself renders the video.
                val cellAspect = cellW.toFloat() / cellH.toFloat()
                val boxAspect = size.width / size.height
                val dstW: Float
                val dstH: Float
                if (cellAspect > boxAspect) {
                    dstW = size.width
                    dstH = size.width / cellAspect
                } else {
                    dstH = size.height
                    dstW = size.height * cellAspect
                }
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(cellW * frame.column, cellH * frame.row),
                    srcSize = IntSize(cellW, cellH),
                    dstOffset = IntOffset(
                        ((size.width - dstW) / 2f).roundToInt(),
                        ((size.height - dstH) / 2f).roundToInt()
                    ),
                    dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt())
                )
            }
        }
    }
}
