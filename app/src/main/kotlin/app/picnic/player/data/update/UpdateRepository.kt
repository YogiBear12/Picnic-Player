package app.picnic.player.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import app.picnic.player.BuildConfig
import app.picnic.player.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Download progress for the update dialog. [totalBytes] is -1 when unknown. */
data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
    val fraction: Float?
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

/**
 * In-app updater. Checks the configured release host, downloads the APK
 * into `cacheDir/updates/` (never the public Downloads dir), hands it to the
 * system installer via FileProvider, and self-cleans on boot: after a successful
 * update the app relaunches as the new version, so [bootCleanup] deletes the
 * installer APK on first start — no install-result receiver needed.
 *
 * Inert when [ReleaseSource.configured] is false (blank UPDATE_REPO) or on debug
 * builds — debug is signed with a different key and can never update a release
 * install. The exception is a debug build with an explicit local `updateRepo`,
 * which is exactly the device-test path.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val releaseSource: ReleaseSource,
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val _updateAvailable = MutableStateFlow<UpdateRelease?>(null)

    /** Non-null when a release newer than the installed version is known. Drives
     *  the About row's "Install update" state and the rail badge dots. */
    val updateAvailable: StateFlow<UpdateRelease?> = _updateAvailable.asStateFlow()

    val enabled: Boolean = releaseSource.configured

    private val updatesDir: File get() = File(context.cacheDir, "updates")

    private fun installedVersion(): UpdateVersion? = UpdateVersion.parse(BuildConfig.VERSION_NAME)

    /**
     * Check the release host. [force] skips the [CHECK_INTERVAL_MS] throttle (manual About check).
     * Returns the newer release, or null (up to date / not configured / failed —
     * manual callers distinguish via [enabled] and their own error surface).
     */
    suspend fun check(force: Boolean = false): UpdateRelease? {
        if (!enabled) return null
        val installed = installedVersion() ?: return null
        if (!force) {
            val last = dataStore.data.first()[LAST_CHECK_MS] ?: 0L
            if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return _updateAvailable.value
        }
        val latest = withContext(io) { releaseSource.latestRelease() } ?: return null
        dataStore.edit { it[LAST_CHECK_MS] = System.currentTimeMillis() }
        val newer = latest.takeIf { it.version > installed }
        _updateAvailable.value = newer
        return newer
    }

    /**
     * Download [release] into the cache, emitting progress. A previously
     * downloaded APK (abandoned install) is reused only when its size matches
     * the published asset — a re-uploaded asset under the same tag otherwise
     * serves a stale cached copy. Unknown asset size (0) always re-downloads.
     */
    fun download(release: UpdateRelease): Flow<DownloadProgress> = flow {
        val target = apkFile(release.version)
        if (target.exists() && release.apkSizeBytes > 0 && target.length() == release.apkSizeBytes) {
            emit(DownloadProgress(target.length(), target.length()))
            return@flow
        }
        target.delete()
        updatesDir.mkdirs()
        val tmp = File(updatesDir, "${target.name}.part")
        releaseSource.download(release.apkUrl).use { response ->
            val total = response.body?.contentLength() ?: -1L
            emit(DownloadProgress(0, total))
            var copied = 0L
            response.body!!.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        emit(DownloadProgress(copied, total))
                    }
                }
            }
        }
        check(tmp.renameTo(target)) { "Could not finalise downloaded APK" }
        emit(DownloadProgress(target.length(), target.length()))
    }.flowOn(io)

    /** Hand the downloaded APK to the system installer. */
    fun install(release: UpdateRelease) {
        val apk = apkFile(release.version)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    /**
     * Boot sweep: delete cached APKs at or below the installed version (the
     * post-update self-clean) and stray .part files. APKs *newer* than installed
     * are kept — an abandoned install can resume without re-downloading.
     */
    suspend fun bootCleanup() = withContext(io) {
        val installed = installedVersion() ?: return@withContext
        updatesDir.listFiles()?.forEach { file ->
            val stale = when {
                file.name.endsWith(".part") -> true
                file.extension == "apk" -> {
                    val version = UpdateVersion.parse(
                        file.nameWithoutExtension.removePrefix(APK_PREFIX)
                    )
                    version == null || version <= installed
                }
                else -> true
            }
            if (stale) file.delete()
        }
    }

    private fun apkFile(version: UpdateVersion): File = File(updatesDir, "$APK_PREFIX$version.apk")

    private companion object {
        val LAST_CHECK_MS = longPreferencesKey("update.lastCheckMs")
        const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val APK_PREFIX = "picnic-"
    }
}
