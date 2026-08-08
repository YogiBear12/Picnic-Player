package app.picnic.player.data.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.picnic.player.di.IoDispatcher
import app.picnic.player.playback.BlackBarTrack
import app.picnic.player.playback.TimedBars
import app.picnic.player.playback.barsInFrame
import app.picnic.player.playback.blackBarSegments
import app.picnic.player.playback.rowLuma
import coil3.imageLoader
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Measures an item's baked-in black bars from its trickplay sprite sheets. Playback is never
 * touched: no decoder is opened and the video stream is never read, and any failure reports
 * [BlackBarTrack.None], which anchors cues exactly as the Image area does.
 *
 * Every thumbnail is read, so a film that changes framing part way through is placed rather than
 * flattened. The sheets are already on disk for the scrub preview, so this costs no network.
 */
@Singleton
class BlackBarProbe @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClient: Lazy<OkHttpClient>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun detect(sheets: List<TrickplaySheet>): BlackBarTrack = withContext(ioDispatcher) {
        runCatching {
            val frames = buildList {
                for (sheet in sheets) {
                    coroutineContext.ensureActive()
                    addAll(measure(sheet))
                }
            }
            BlackBarTrack(blackBarSegments(frames))
        }.getOrDefault(BlackBarTrack.None)
    }

    private suspend fun measure(sheet: TrickplaySheet): List<TimedBars> {
        if (sheet.columns <= 0 || sheet.rows <= 0) return emptyList()
        val bitmap = decode(sheet.url) ?: return emptyList()
        return try {
            val cellWidth = bitmap.width / sheet.columns
            val cellHeight = bitmap.height / sheet.rows
            if (cellWidth <= 0 || cellHeight < MinFrameHeight) return emptyList()
            val pixels = IntArray(cellWidth * cellHeight)
            (0 until sheet.columns * sheet.rows)
                .mapNotNull { cell ->
                    coroutineContext.ensureActive()
                    bitmap.getPixels(
                        pixels,
                        0,
                        cellWidth,
                        (cell % sheet.columns) * cellWidth,
                        (cell / sheet.columns) * cellHeight,
                        cellWidth,
                        cellHeight
                    )
                    barsInFrame(FloatArray(cellHeight) { row -> rowLuma(pixels, row * cellWidth, cellWidth, ColumnStride) })
                        ?.let { TimedBars(sheet.positionMsOf(cell), it) }
                }
        } finally {
            bitmap.recycle()
        }
    }

    /** Subsampled: bars are measured in whole rows, so full resolution would only cost memory. */
    private fun decode(url: String): Bitmap? {
        val bytes = bytesOf(url) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = generateSequence(1) { it * 2 }.first { longest / it <= MaxDecodedEdge }
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * The sheets are already on disk for the scrub preview, so taking them from there keeps the
     * probe off the network entirely while the video is starting.
     */
    private fun bytesOf(url: String): ByteArray? = cached(url) ?: httpClient.get()
        .newCall(Request.Builder().url(url).build())
        .execute()
        .use { response -> if (response.isSuccessful) response.body?.bytes() else null }

    private fun cached(url: String): ByteArray? {
        val disk = appContext.imageLoader.diskCache ?: return null
        return runCatching {
            disk.openSnapshot(url)?.use { snapshot -> disk.fileSystem.read(snapshot.data) { readByteArray() } }
        }.getOrNull()
    }

    private companion object {
        const val MaxDecodedEdge = 1600
        const val ColumnStride = 4
        const val MinFrameHeight = 48
    }
}
