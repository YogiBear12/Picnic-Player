package app.picnic.player.data.media

import android.content.Context
import app.picnic.player.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.api.MediaStream

/**
 * Persisted home snapshot backing stale-while-revalidate render. Season counts
 * are keyed by the series UUID as a string so the map serializes as a plain JSON object.
 */
@Serializable
data class HomeSnapshot(
    val rows: List<HomeRow>,
    val seasonCounts: Map<String, Int> = emptyMap(),
    val heroStreams: Map<String, List<MediaStream>> = emptyMap()
)

/**
 * Disk cache of the last-rendered home rows, per user session. The home screen paints this
 * instantly on launch (no blank spinner) and refreshes from the network in the background,
 * swapping the fresh rows in — the render model native streaming apps use.
 *
 * Serialization uses the Jellyfin SDK's own [ApiSerializer.json] so [BaseItemDto]'s UUID /
 * date-time fields round-trip exactly as they do over the wire. All I/O is on the IO
 * dispatcher and every failure is swallowed — a missing or corrupt cache just means the
 * cold-load spinner shows, never a crash.
 */
@Singleton
class HomeCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = ApiSerializer.json
    private val dir = File(context.filesDir, "home_cache")
    private val serializer = HomeSnapshot.serializer()

    private fun file(key: String) = File(dir, "${key.hashCode()}.json")

    /** Last-persisted snapshot for [key], or null when absent/unreadable. */
    suspend fun read(key: String): HomeSnapshot? = withContext(ioDispatcher) {
        runCatching {
            val f = file(key)
            if (!f.exists()) null else json.decodeFromString(serializer, f.readText())
        }.getOrNull()
    }

    /** Persists [snapshot] for [key], atomically (write-temp-then-rename). Failures are ignored. */
    suspend fun write(key: String, snapshot: HomeSnapshot) {
        withContext(ioDispatcher) {
            runCatching {
                dir.mkdirs()
                val target = file(key)
                val tmp = File(dir, "${target.name}.tmp")
                tmp.writeText(json.encodeToString(serializer, snapshot))
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }
}
