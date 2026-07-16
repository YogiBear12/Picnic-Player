package app.picnic.player.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR code. Used for Quick Connect pairing: the user scans
 * to open `{server}/web/#/quickconnect` on an already-signed-in device. Encoding
 * is pure-CPU (ZXing) and memoised on the inputs, so it is safe to compose
 * directly without a background dispatcher.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    foreground: ComposeColor = ComposeColor.Black,
    background: ComposeColor = ComposeColor.White
) {
    val bitmap: ImageBitmap = remember(content, foreground, background) {
        encodeQr(content, foreground.toArgb(), background.toArgb())
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.size(size),
        // Nearest-neighbour keeps QR modules crisp when scaled up to TV size.
        filterQuality = FilterQuality.None
    )
}

private fun encodeQr(content: String, fg: Int, bg: Int): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    val w = matrix.width
    val h = matrix.height
    val bmp = createBitmap(w, h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            bmp[x, y] = if (matrix[x, y]) fg else bg
        }
    }
    return bmp.asImageBitmap()
}
