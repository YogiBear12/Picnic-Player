package app.picnic.player.data.syncplay

import app.picnic.player.data.media.SessionApi
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.jellyfin.sdk.api.client.extensions.syncPlayApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.SyncPlayApi
import org.jellyfin.sdk.model.api.BufferRequestDto
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.ReadyRequestDto
import org.jellyfin.sdk.model.api.SeekRequestDto
import org.jellyfin.sdk.model.api.SyncPlayUserAccessType

@Singleton
class SyncPlayRepository @Inject constructor(
    private val api: SessionApi,
    private val clock: SyncPlayClock
) {
    private suspend fun <T> call(block: suspend SyncPlayApi.() -> T): T = api.onIo { api.client().syncPlayApi.block() }

    suspend fun access(): SyncPlayUserAccessType = api.onIo {
        api.client().userApi.getCurrentUser().content.policy?.syncPlayAccess ?: SyncPlayUserAccessType.NONE
    }

    suspend fun groups(): List<GroupInfoDto> = call { syncPlayGetGroups().content }

    suspend fun create(name: String): GroupInfoDto = call { syncPlayCreateGroup(NewGroupRequestDto(name)).content }

    suspend fun join(groupId: UUID) {
        call { syncPlayJoinGroup(JoinGroupRequestDto(groupId)) }
    }

    suspend fun leave() {
        call { syncPlayLeaveGroup() }
    }

    suspend fun setNewQueue(itemId: UUID, startTicks: Long) {
        call { syncPlaySetNewQueue(PlayRequestDto(listOf(itemId), 0, startTicks)) }
    }

    suspend fun pause() {
        call { syncPlayPause() }
    }

    suspend fun unpause() {
        call { syncPlayUnpause() }
    }

    suspend fun seek(positionTicks: Long) {
        call { syncPlaySeek(SeekRequestDto(positionTicks)) }
    }

    suspend fun buffering(positionTicks: Long, isPlaying: Boolean, playlistItemId: UUID) {
        call { syncPlayBuffering(BufferRequestDto(serverNow(), positionTicks, isPlaying, playlistItemId)) }
    }

    suspend fun ready(positionTicks: Long, isPlaying: Boolean, playlistItemId: UUID) {
        call { syncPlayReady(ReadyRequestDto(serverNow(), positionTicks, isPlaying, playlistItemId)) }
    }

    // The server reads `When` on its own clock; the SDK writes a LocalDateTime in the device's zone.
    private fun serverNow(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.serverNowMs()), ZoneId.systemDefault())
}
