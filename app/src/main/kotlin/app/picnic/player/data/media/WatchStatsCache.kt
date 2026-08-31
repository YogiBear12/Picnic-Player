package app.picnic.player.data.media

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Singleton
class WatchStatsCache @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    private val byUser = MutableStateFlow<Map<UUID, WatchStats>>(emptyMap())

    fun statsFor(userId: UUID): Flow<WatchStats?> = byUser.map { it[userId] }

    suspend fun refresh(userId: UUID) {
        val stats = try {
            mediaRepository.watchStats()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(WATCH_STATS_LOG_TAG, "watch stats load failed", failure)
            return
        }
        byUser.update { it + (userId to stats) }
    }

    private companion object {
        const val WATCH_STATS_LOG_TAG = "PicnicAccount"
    }
}
