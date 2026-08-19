package app.picnic.player.data.tvprovider

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.picnic.player.BuildConfig
import app.picnic.player.MainActivity
import app.picnic.player.R
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.media.HomeContent
import app.picnic.player.data.media.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.ZoneId
import java.util.Date
import kotlin.time.Duration.Companion.minutes
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks

@SuppressLint("RestrictedApi")
@HiltWorker
class TvChannelSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        if (!isTvProviderAvailable()) {
            Log.w(TAG, "TV provider not available on this device; skipping")
            return Result.failure()
        }

        val session = authRepository.activeSession()
        if (session == null) {
            Log.d(TAG, "No active session; skipping channel sync")
            return Result.success()
        }

        val resumeItems = row("resumeItems") { mediaRepository.resumeItems(ROW_LIMIT) }
        val nextUpItems = row("nextUp") { mediaRepository.nextUp(ROW_LIMIT) }
        val latestMedia = row("latestMedia") {
            mediaRepository.latestMedia(
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                limit = ROW_LIMIT,
                groupItems = true
            )
        }
        val latestMovies = row("latestMovies") {
            mediaRepository.latestMedia(
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                limit = ROW_LIMIT
            )
        }
        val recommendations = row("suggestions") { mediaRepository.suggestions(ROW_LIMIT) }

        return try {
            if (resumeItems != null && nextUpItems != null) {
                updateWatchNext(session, HomeContent.combineContinueWatching(resumeItems, nextUpItems))
            }

            latestMedia?.let { updateChannel("latest_media", "Latest Media", session, it, defaultBrowsable = true) }
            latestMovies?.let { updateChannel("latest_movies", "Latest Movies", session, it) }
            recommendations?.let { updateChannel("recommendations", "Recommendations", session, it) }
            removeChannel("latest_episodes")

            val failed = listOf(resumeItems, nextUpItems, latestMedia, latestMovies, recommendations).count { it == null }
            if (failed > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish TV channels", e)
            Result.retry()
        }
    }

    private fun isTvProviderAvailable(): Boolean {
        if (!context.packageManager.hasSystemFeature("android.software.leanback")) return false
        return context.packageManager.resolveContentProvider(TvContractCompat.AUTHORITY, 0) != null
    }

    private suspend fun row(step: String, block: suspend () -> List<BaseItemDto>): List<BaseItemDto>? {
        if (!BuildConfig.DEBUG) return runCatching { block() }.getOrNull()
        val start = SystemClock.elapsedRealtime()
        return runCatching { block() }
            .onSuccess { Log.i(TAG, "$step took ${SystemClock.elapsedRealtime() - start}ms") }
            .onFailure { Log.w(TAG, "$step failed after ${SystemClock.elapsedRealtime() - start}ms", it) }
            .getOrNull()
    }

    private fun updateWatchNext(session: UserSession, items: List<BaseItemDto>) {
        val currentItems = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            generateSequence { if (cursor.moveToNext()) WatchNextProgram.fromCursor(cursor) else null }.toList()
        } ?: emptyList()

        currentItems.forEach {
            context.contentResolver.delete(
                TvContractCompat.buildWatchNextProgramUri(it.id),
                null,
                null
            )
        }

        if (items.isEmpty()) return

        val programs = items
            .map { buildWatchNextProgram(session, it).toContentValues() }
            .toTypedArray()
        context.contentResolver.bulkInsert(TvContractCompat.WatchNextPrograms.CONTENT_URI, programs)
    }

    private fun updateChannel(
        channelKey: String,
        title: String,
        session: UserSession,
        items: List<BaseItemDto>,
        defaultBrowsable: Boolean = false
    ) {
        val channelUri = getOrCreateChannel(channelKey, title, defaultBrowsable) ?: return
        context.contentResolver.delete(
            TvContractCompat.buildPreviewProgramsUriForChannel(ContentUris.parseId(channelUri)),
            null,
            null
        )
        if (items.isEmpty()) return

        val programs = items
            .map { buildPreviewProgram(channelUri, session, it).toContentValues() }
            .toTypedArray()
        context.contentResolver.bulkInsert(TvContractCompat.PreviewPrograms.CONTENT_URI, programs)
    }

    private fun removeChannel(key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uri = prefs.getString(key, null)?.toUri() ?: return
        runCatching { context.contentResolver.delete(uri, null, null) }
        prefs.edit { remove(key) }
    }

    private fun getOrCreateChannel(key: String, title: String, defaultBrowsable: Boolean): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(title)
            .setAppLinkIntent(Intent(context, MainActivity::class.java))
            .build()

        var uri = prefs.getString(key, null)?.toUri()
        if (uri != null) {
            val updated = context.contentResolver.update(uri, channel.toContentValues(), null, null)
            if (updated != 1) uri = null
        }

        if (uri == null) {
            uri = context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
            )
            if (uri != null) {
                prefs.edit { putString(key, uri.toString()) }
                if (defaultBrowsable) {
                    TvContractCompat.requestChannelBrowsable(context, ContentUris.parseId(uri))
                }
            }
        }

        if (uri != null) {
            storeChannelLogo(ContentUris.parseId(uri))
        }
        return uri
    }

    private fun storeChannelLogo(channelId: Long) {
        ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_launcher, context.theme)?.let { drawable ->
            val size = (80 * context.resources.displayMetrics.density).toInt()
            ChannelLogoUtils.storeChannelLogo(context, channelId, drawable.toBitmap(size, size))
        }
    }

    private fun buildWatchNextProgram(session: UserSession, item: BaseItemDto): WatchNextProgram {
        val builder = WatchNextProgram.Builder()
        populateProgram(builder, session, item)

        val resumePosition = item.userData?.playbackPositionTicks?.ticks
        if (resumePosition != null && resumePosition >= 2.minutes) {
            builder.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            builder.setLastPlaybackPositionMillis(resumePosition.inWholeMilliseconds.toInt())
        } else {
            builder.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
        }

        val engagementMillis = item.userData?.lastPlayedDate
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
            ?: Date().time
        builder.setLastEngagementTimeUtcMillis(engagementMillis)

        return builder.build()
    }

    private fun buildPreviewProgram(
        channelUri: Uri,
        session: UserSession,
        item: BaseItemDto
    ): PreviewProgram {
        val builder = PreviewProgram.Builder()
        builder.setChannelId(ContentUris.parseId(channelUri))
        populateProgram(builder, session, item)
        return builder.build()
    }

    private fun populateProgram(
        builder: androidx.tvprovider.media.tv.BasePreviewProgram.Builder<*>,
        session: UserSession,
        item: BaseItemDto
    ) {
        builder.setInternalProviderId(item.id.toString())
        builder.setTitle(item.seriesName ?: item.name)
        builder.setDescription(item.overview)

        val type = when (item.type) {
            BaseItemKind.SERIES -> TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
            BaseItemKind.SEASON -> TvContractCompat.PreviewPrograms.TYPE_TV_SEASON
            BaseItemKind.EPISODE -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
            BaseItemKind.MOVIE -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
            else -> TvContractCompat.PreviewPrograms.TYPE_CLIP
        }
        builder.setType(type)

        item.runTimeTicks?.ticks?.inWholeMilliseconds?.toInt()?.let { builder.setDurationMillis(it) }

        if (item.type == BaseItemKind.EPISODE) {
            builder.setEpisodeTitle(item.name)
            item.indexNumber?.let { builder.setEpisodeNumber(it) }
            item.parentIndexNumber?.let { builder.setSeasonNumber(it) }
        }

        builder.setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
        val imageUri = JellyfinImages.thumb(session, item)
            ?: JellyfinImages.primary(session, item)
            ?: JellyfinImages.backdrop(session, item)
        imageUri?.let { builder.setPosterArtUri(it.toUri()) }

        val intentUri = when {
            item.type == BaseItemKind.EPISODE && item.seriesId != null -> {
                val season = item.seasonId?.let { "?seasonId=$it" }.orEmpty()
                "picnic://series/${item.seriesId}/episode/${item.id}$season".toUri()
            }
            else -> "picnic://item/${item.id}".toUri()
        }
        builder.setIntent(Intent(Intent.ACTION_VIEW, intentUri))
    }

    companion object {
        private const val TAG = "TvChannelSyncWorker"
        private const val PREFS_NAME = "tv_channels"
        private const val ROW_LIMIT = 15
        const val WORK_NAME = "TvChannelSync"
    }
}
