package app.picnic.player.data.syncplay

import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.media.SessionApi
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.extensions.syncPlayApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.SyncPlayApi
import org.jellyfin.sdk.model.api.BufferRequestDto
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.NextItemRequestDto
import org.jellyfin.sdk.model.api.PingRequestDto
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.ReadyRequestDto
import org.jellyfin.sdk.model.api.SeekRequestDto
import org.jellyfin.sdk.model.api.SyncPlayUserAccessType

@Singleton
class SyncPlayRepository @Inject constructor(
    private val api: SessionApi,
    private val clock: SyncPlayClock,
    private val session: SyncPlaySession
) : SyncPlayRoomApi {
    private suspend fun <T> call(block: suspend SyncPlayApi.() -> T): T = api.onIo { api.client().syncPlayApi.block() }

    private val _access = MutableStateFlow(SyncPlayUserAccessType.NONE)

    /** The current user's access as of the last [access] read; Home refreshes it on every load. */
    val currentAccess: StateFlow<SyncPlayUserAccessType> = _access.asStateFlow()

    suspend fun access(): SyncPlayUserAccessType = api.onIo {
        api.client().userApi.getCurrentUser().content.policy?.syncPlayAccess ?: SyncPlayUserAccessType.NONE
    }.also { _access.value = it }

    suspend fun groups(): List<GroupInfoDto> = call { syncPlayGetGroups().content }

    /**
     * Picture URLs keyed by lowercased user name: a group names its members but carries no ids, and
     * the server lets any user list the others.
     */
    suspend fun memberPictures(): Map<String, String> = api.onIo {
        val baseUrl = api.session().server.baseUrl
        api.client().userApi.getUsers().content
            .filter { it.primaryImageTag != null }
            .associate { user ->
                user.name.orEmpty().lowercase() to JellyfinImages.userPrimary(baseUrl, user.id.toString(), user.primaryImageTag)
            }
    }

    // Leave is launched undispatched after a screen closes, so it holds this lock before the next join can ask.
    private val membership = Mutex()

    suspend fun create(name: String): GroupInfoDto = membership.withLock { call { syncPlayCreateGroup(NewGroupRequestDto(name)).content } }

    override suspend fun join(groupId: UUID) {
        membership.withLock { call { syncPlayJoinGroup(JoinGroupRequestDto(groupId)) } }
    }

    /** Forgets the room at once, so a quick re-join of the same room cannot be confirmed by the old state. */
    override suspend fun leave() {
        membership.withLock {
            session.onLeft()
            call { syncPlayLeaveGroup() }
        }
    }

    override suspend fun setNewQueue(itemIds: List<UUID>, startTicks: Long) {
        call { syncPlaySetNewQueue(PlayRequestDto(itemIds, 0, startTicks)) }
    }

    override suspend fun nextItem(playlistItemId: UUID) {
        call { syncPlayNextItem(NextItemRequestDto(playlistItemId)) }
    }

    override suspend fun pause() {
        call { syncPlayPause() }
    }

    override suspend fun unpause() {
        call { syncPlayUnpause() }
    }

    override suspend fun seek(positionTicks: Long) {
        call { syncPlaySeek(SeekRequestDto(positionTicks)) }
    }

    override suspend fun buffering(positionTicks: Long, isPlaying: Boolean, playlistItemId: UUID) {
        call { syncPlayBuffering(BufferRequestDto(serverNow(), positionTicks, isPlaying, playlistItemId)) }
    }

    override suspend fun ready(positionTicks: Long, isPlaying: Boolean, playlistItemId: UUID) {
        call { syncPlayReady(ReadyRequestDto(serverNow(), positionTicks, isPlaying, playlistItemId)) }
    }

    // A quick fix on entry, then a slow resync; the server schedules each `When` from the members' pings.
    override suspend fun keepClockInSync(): Nothing {
        var taken = 0
        while (true) {
            runCatching { clock.sample() }.onSuccess { sample ->
                taken++
                runCatching { call { syncPlayPing(PingRequestDto(sample.roundTripMs / 2)) } }
            }
            delay(if (taken < ENTRY_SAMPLES) ENTRY_SPACING else RESYNC_SPACING)
        }
    }

    override suspend fun awaitClockSynced() = clock.awaitSynced()

    override fun toLocalMs(serverMs: Long): Long = clock.toLocalMs(serverMs)

    // The server reads `When` on its own clock; the SDK writes a LocalDateTime in the device's zone.
    private fun serverNow(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.serverNowMs()), ZoneId.systemDefault())

    private companion object {
        const val ENTRY_SAMPLES = 3
        val ENTRY_SPACING = 1.seconds
        val RESYNC_SPACING = 60.seconds
    }
}
