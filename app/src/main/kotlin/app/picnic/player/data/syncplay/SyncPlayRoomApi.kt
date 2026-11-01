package app.picnic.player.data.syncplay

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID

/** What a member's player asks of the server; a seam so the player's room logic can be unit-tested. */
interface SyncPlayRoomApi {
    suspend fun join(groupId: UUID)
    suspend fun leave()
    suspend fun pause()
    suspend fun unpause()
    suspend fun seek(positionTicks: Long)
    suspend fun buffering(positionTicks: Long, isPlaying: Boolean, playlistItemId: UUID)
    suspend fun ready(positionTicks: Long, isPlaying: Boolean, playlistItemId: UUID)
    suspend fun keepClockInSync(): Nothing
    suspend fun awaitClockSynced()
    fun toLocalMs(serverMs: Long): Long
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncPlayModule {
    @Binds
    abstract fun roomApi(impl: SyncPlayRepository): SyncPlayRoomApi
}
