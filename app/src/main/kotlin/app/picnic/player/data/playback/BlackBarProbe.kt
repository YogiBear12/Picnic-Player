package app.picnic.player.data.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.picnic.player.di.IoDispatcher
import app.picnic.player.playback.BlackBarTrack
import app.picnic.player.playback.PlaybackDiagnostics
import app.picnic.player.playback.TimedBars
import app.picnic.player.playback.barsInFrame
import app.picnic.player.playback.blackBarSegments
import coil3.imageLoader
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class BlackBarProbe @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClient: Lazy<OkHttpClient>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private data class Measured(val frames: List<TimedBars>, val frameHeight: Int)

    private val memo = Mutex()
    private var measuredSheets: List<TrickplaySheet>? = null
    private var measuredTrack: BlackBarTrack? = null

    suspend fun detect(sheets: List<TrickplaySheet>): BlackBarTrack {
        memo.withLock {
            if (sheets == measuredSheets) {
                measuredTrack?.let {
                    PlaybackDiagnostics.log("black bars: reusing ${sheets.size} measured sheet(s)")
                    return it
                }
            }
        }
        PlaybackDiagnostics.log("black bars: measuring ${sheets.size} sheet(s)")
        val track = measureTrack(sheets)
        memo.withLock {
            measuredSheets = sheets
            measuredTrack = track
        }
        return track
    }

    private suspend fun measureTrack(sheets: List<TrickplaySheet>): BlackBarTrack = withContext(ioDispatcher) {
        runCatching {
            val frames = mutableListOf<TimedBars>()
            var frameHeight = 0
            for (sheet in sheets) {
                coroutineContext.ensureActive()
                val measured = measure(sheet)
                frames += measured.frames
                if (measured.frameHeight > 0) frameHeight = measured.frameHeight
            }
            BlackBarTrack(blackBarSegments(frames, frameHeight))
        }.getOrDefault(BlackBarTrack.None)
    }

    private suspend fun measure(sheet: TrickplaySheet): Measured {
        if (sheet.columns <= 0 || sheet.rows <= 0) return Measured(emptyList(), 0)
        val bitmap = decode(sheet.url) ?: return Measured(emptyList(), 0)
        return try {
            val cellWidth = bitmap.width / sheet.columns
            val cellHeight = bitmap.height / sheet.rows
            if (cellWidth <= 0 || cellHeight < MinFrameHeight) return Measured(emptyList(), 0)
            val pixels = IntArray(cellWidth * cellHeight)
            val frames = (0 until sheet.columns * sheet.rows)
                .map { cell ->
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
                    TimedBars(sheet.positionMsOf(cell), barsInFrame(pixels, cellWidth, cellHeight))
                }
            Measured(frames, cellHeight)
        } finally {
            bitmap.recycle()
        }
    }

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

    private fun bytesOf(url: String): ByteArray? = cached(url) ?: httpClient.get()
        .newCall(Request.Builder().url(url).build())
        .execute()
        .use { response -> if (response.isSuccessful) response.body?.bytes() else null }

    private fun cached(url: String): ByteArray? {
        val disk = appContext.imageLoader.diskCache ?: return null
        return runCatching {
            disk.openSnapshot(trickplaySheetCacheKey(url))?.use { snapshot -> disk.fileSystem.read(snapshot.data) { readByteArray() } }
        }.getOrNull()
    }

    private companion object {
        const val MaxDecodedEdge = 1600
        const val MinFrameHeight = 48
    }
}
