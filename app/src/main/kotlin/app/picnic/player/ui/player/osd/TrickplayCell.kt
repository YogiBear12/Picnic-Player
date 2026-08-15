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
import app.picnic.player.data.playback.trickplaySheetCacheKey
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrickplayCell(
    frame: TrickplayFrame,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheet by produceState<ImageBitmap?>(initialValue = null, frame.url) {
        value = withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context)
                .data(frame.url)
                .diskCacheKey(trickplaySheetCacheKey(frame.url))
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
            .background(Color.Black)
    ) {
        val bitmap = sheet ?: return@Box
        val cellW = bitmap.width / frame.columns
        val cellH = bitmap.height / frame.rows
        if (cellW > 0 && cellH > 0) {
            Canvas(Modifier.fillMaxSize()) {
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
