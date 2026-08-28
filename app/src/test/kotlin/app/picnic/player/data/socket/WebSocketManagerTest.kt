package app.picnic.player.data.socket

import app.picnic.player.data.auth.ServerConnection
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.playback.PlayerCommand
import app.picnic.player.data.playback.PlayerCommandBus
import java.util.UUID
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.LibraryUpdateInfo
import org.jellyfin.sdk.model.api.OutboundWebSocketMessage
import org.jellyfin.sdk.model.api.PlayCommand
import org.jellyfin.sdk.model.api.PlayMessage
import org.jellyfin.sdk.model.api.PlayRequest
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.PlaystateRequest
import org.jellyfin.sdk.model.api.ServerShuttingDownMessage
import org.jellyfin.sdk.model.api.UserDataChangeInfo
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fan-out + lifecycle tests for [WebSocketManager], driven through a fake [SessionSocket]
 * over the SDK message flows. Everything runs on [Dispatchers.Unconfined] so the manager's
 * coroutines execute in-line; a small [delay] after each emit drains the event loop.
 *
 * Covers Slice 1 (library / user-data → [LibraryChangeBus], lifecycle) and Slice 2 (remote
 * control: PlayState / Play / GeneralCommand → the player / message / remote-control buses,
 * plus the volume-ignored and server-lifecycle rules).
 */
class WebSocketManagerTest {

    private class FakeSessionSocket : SessionSocket {
        override val state = MutableStateFlow<SocketApiState>(SocketApiState.Connecting)
        val libraryMessages = MutableSharedFlow<LibraryChangedMessage>(extraBufferCapacity = 16)
        val userDataMessages = MutableSharedFlow<UserDataChangedMessage>(extraBufferCapacity = 16)
        val playstateMessages = MutableSharedFlow<PlaystateMessage>(extraBufferCapacity = 16)
        val playMessages = MutableSharedFlow<PlayMessage>(extraBufferCapacity = 16)
        val generalMessages = MutableSharedFlow<GeneralCommandMessage>(extraBufferCapacity = 16)
        val shutdownMessages = MutableSharedFlow<ServerShuttingDownMessage>(extraBufferCapacity = 16)
        var capabilitiesRegistered = 0

        @Suppress("UNCHECKED_CAST")
        override fun <T : OutboundWebSocketMessage> messages(type: KClass<T>): Flow<T> = when (type) {
            LibraryChangedMessage::class -> libraryMessages as Flow<T>
            UserDataChangedMessage::class -> userDataMessages as Flow<T>
            PlaystateMessage::class -> playstateMessages as Flow<T>
            PlayMessage::class -> playMessages as Flow<T>
            GeneralCommandMessage::class -> generalMessages as Flow<T>
            ServerShuttingDownMessage::class -> shutdownMessages as Flow<T>
            else -> emptyFlow()
        }

        override suspend fun registerCapabilities() {
            capabilitiesRegistered++
        }
    }

    private class FakeSessionSocketFactory : SessionSocketFactory {
        val sockets = mutableListOf<FakeSessionSocket>()
        override fun open(session: UserSession): SessionSocket = FakeSessionSocket().also { sockets.add(it) }
    }

    private class FakeForegroundState(resumed: Boolean) : AppForegroundState {
        override val isResumed = MutableStateFlow(resumed)
        override val isVisible = MutableStateFlow(resumed)
    }

    private class Harness(foreground: Boolean = true) {
        val activeSession = MutableStateFlow<UserSession?>(null)
        val factory = FakeSessionSocketFactory()
        val foreground = FakeForegroundState(foreground)
        val bus = LibraryChangeBus()
        val playerBus = PlayerCommandBus()
        val messageBus = ServerMessageBus()
        val remoteBus = RemoteControlBus()

        val received = mutableListOf<LibraryChange>()
        val playerCommands = mutableListOf<PlayerCommand>()
        val notices = mutableListOf<ServerNotice>()
        val remoteActions = mutableListOf<RemoteControlAction>()
        private val collectJobs = mutableListOf<Job>()

        val manager = WebSocketManager(
            activeSession,
            factory,
            this.foreground,
            bus,
            playerBus,
            messageBus,
            remoteBus,
            Dispatchers.Unconfined
        )

        fun startCollecting(scope: CoroutineScope) {
            collectJobs += scope.launch(Dispatchers.Unconfined) { bus.changes().collect { received += it } }
            collectJobs += scope.launch(Dispatchers.Unconfined) { playerBus.commands.collect { playerCommands += it } }
            collectJobs += scope.launch(Dispatchers.Unconfined) { messageBus.messages.collect { notices += it } }
            collectJobs += scope.launch(Dispatchers.Unconfined) { remoteBus.actions.collect { remoteActions += it } }
            manager.start()
        }

        fun stopCollecting() = collectJobs.forEach { it.cancel() }
    }

    private fun session(userId: String) = UserSession(
        server = ServerConnection(id = "srv", baseUrl = "http://host", name = "Host"),
        userId = userId,
        username = "user",
        accessToken = "token"
    )

    private fun libraryMessage(
        itemsAdded: List<String> = emptyList(),
        itemsRemoved: List<String> = emptyList(),
        itemsUpdated: List<String> = emptyList()
    ) = LibraryChangedMessage(
        data = LibraryUpdateInfo(
            foldersAddedTo = emptyList(),
            foldersRemovedFrom = emptyList(),
            itemsAdded = itemsAdded,
            itemsRemoved = itemsRemoved,
            itemsUpdated = itemsUpdated,
            collectionFolders = emptyList(),
            isEmpty = itemsAdded.isEmpty() && itemsRemoved.isEmpty() && itemsUpdated.isEmpty()
        ),
        messageId = UUID.randomUUID()
    )

    private fun userDataMessage(userId: UUID, itemIds: List<UUID>) = UserDataChangedMessage(
        data = UserDataChangeInfo(
            userId = userId,
            userDataList = itemIds.map { itemData(it) }
        ),
        messageId = UUID.randomUUID()
    )

    private fun itemData(itemId: UUID) = UserItemDataDto(
        playbackPositionTicks = 0,
        playCount = 0,
        isFavorite = false,
        played = false,
        key = itemId.toString(),
        itemId = itemId
    )

    private fun playstate(command: PlaystateCommand, seekTicks: Long? = null) = PlaystateMessage(
        data = PlaystateRequest(command = command, seekPositionTicks = seekTicks, controllingUserId = null),
        messageId = UUID.randomUUID()
    )

    private fun generalCommand(
        name: GeneralCommandType,
        arguments: Map<String, String> = emptyMap()
    ) = GeneralCommandMessage(
        data = GeneralCommand(name = name, controllingUserId = UUID.randomUUID(), arguments = arguments),
        messageId = UUID.randomUUID()
    )

    // --- Slice 1 ------------------------------------------------------------------------------

    @Test
    fun libraryChangedWithItems_emitsLibraryContentChanged() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())

        h.factory.sockets.single().libraryMessages.emit(libraryMessage(itemsAdded = listOf("a")))
        delay(SETTLE_MS)

        assertEquals(listOf(LibraryChange.LibraryContentChanged), h.received)
        h.stopCollecting()
    }

    @Test
    fun libraryChangedWithNoItems_isIgnored() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())

        h.factory.sockets.single().libraryMessages.emit(libraryMessage())
        delay(SETTLE_MS)

        assertTrue(h.received.isEmpty())
        h.stopCollecting()
    }

    @Test
    fun userDataForCurrentUser_emitsItemUpdatedPerItem() = runBlocking {
        val userId = UUID.randomUUID()
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(userId.toString())

        val itemA = UUID.randomUUID()
        val itemB = UUID.randomUUID()
        h.factory.sockets.single().userDataMessages.emit(userDataMessage(userId, listOf(itemA, itemB)))
        delay(SETTLE_MS)

        assertEquals(
            listOf(
                LibraryChange.ItemUpdated(itemA.toString()),
                LibraryChange.ItemUpdated(itemB.toString())
            ),
            h.received
        )
        h.stopCollecting()
    }

    @Test
    fun userDataForDifferentUser_isFilteredOut() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())

        val otherUser = UUID.randomUUID()
        h.factory.sockets.single().userDataMessages.emit(userDataMessage(otherUser, listOf(UUID.randomUUID())))
        delay(SETTLE_MS)

        assertTrue(h.received.isEmpty())
        h.stopCollecting()
    }

    @Test
    fun sessionBecomesActive_opensSocketAndRegistersCapabilities() = runBlocking {
        val h = Harness()
        h.startCollecting(this)

        h.activeSession.value = session(UUID.randomUUID().toString())
        delay(SETTLE_MS)

        assertEquals(1, h.factory.sockets.size)
        assertEquals(1, h.factory.sockets.single().capabilitiesRegistered)
        h.stopCollecting()
    }

    @Test
    fun logout_tearsDownSocketSoLaterMessagesAreIgnored() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        val socket = h.factory.sockets.single()

        h.activeSession.value = null
        delay(SETTLE_MS)
        socket.libraryMessages.emit(libraryMessage(itemsAdded = listOf("a")))
        delay(SETTLE_MS)

        assertTrue(h.received.isEmpty())
        h.stopCollecting()
    }

    @Test
    fun switchingSession_opensAFreshSocket() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        h.activeSession.value = session(UUID.randomUUID().toString())
        delay(SETTLE_MS)

        assertEquals(2, h.factory.sockets.size)
        assertEquals(1, h.factory.sockets.last().capabilitiesRegistered)
        h.stopCollecting()
    }

    @Test
    fun whileBackgrounded_messagesAreNotSubscribed() = runBlocking {
        val h = Harness(foreground = false)
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        delay(SETTLE_MS)

        h.factory.sockets.single().libraryMessages.emit(libraryMessage(itemsAdded = listOf("a")))
        delay(SETTLE_MS)
        assertTrue(h.received.isEmpty())

        h.foreground.isResumed.value = true
        delay(SETTLE_MS)
        h.factory.sockets.single().libraryMessages.emit(libraryMessage(itemsAdded = listOf("b")))
        delay(SETTLE_MS)
        assertEquals(listOf(LibraryChange.LibraryContentChanged), h.received)
        h.stopCollecting()
    }

    // --- Slice 2: PlayState → PlayerCommandBus ------------------------------------------------

    @Test
    fun playstateCommands_mapToPlayerCommands() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        val socket = h.factory.sockets.single()

        socket.playstateMessages.emit(playstate(PlaystateCommand.STOP))
        socket.playstateMessages.emit(playstate(PlaystateCommand.PAUSE))
        socket.playstateMessages.emit(playstate(PlaystateCommand.UNPAUSE))
        socket.playstateMessages.emit(playstate(PlaystateCommand.PLAY_PAUSE))
        socket.playstateMessages.emit(playstate(PlaystateCommand.NEXT_TRACK))
        socket.playstateMessages.emit(playstate(PlaystateCommand.PREVIOUS_TRACK))
        socket.playstateMessages.emit(playstate(PlaystateCommand.REWIND))
        socket.playstateMessages.emit(playstate(PlaystateCommand.FAST_FORWARD))
        delay(SETTLE_MS)

        assertEquals(
            listOf(
                PlayerCommand.Stop,
                PlayerCommand.Pause,
                PlayerCommand.Unpause,
                PlayerCommand.PlayPause,
                PlayerCommand.NextTrack,
                PlayerCommand.PreviousTrack,
                PlayerCommand.Rewind,
                PlayerCommand.FastForward
            ),
            h.playerCommands
        )
        h.stopCollecting()
    }

    @Test
    fun playstateSeek_convertsTicksToMs() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())

        // 50,000,000 ticks = 5000 ms.
        h.factory.sockets.single().playstateMessages.emit(
            playstate(PlaystateCommand.SEEK, seekTicks = 50_000_000)
        )
        delay(SETTLE_MS)

        assertEquals(listOf(PlayerCommand.Seek(5_000L)), h.playerCommands)
        h.stopCollecting()
    }

    @Test
    fun playstateSeekWithoutPosition_isIgnored() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())

        h.factory.sockets.single().playstateMessages.emit(playstate(PlaystateCommand.SEEK, seekTicks = null))
        delay(SETTLE_MS)

        assertTrue(h.playerCommands.isEmpty())
        h.stopCollecting()
    }

    // --- Slice 2: Play cast command → RemoteControlBus ----------------------------------------

    @Test
    fun playMessage_launchesPlaybackWithStartPosition() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())

        val item = UUID.randomUUID()
        h.factory.sockets.single().playMessages.emit(
            PlayMessage(
                data = PlayRequest(
                    itemIds = listOf(item),
                    startPositionTicks = 30_000_000,
                    playCommand = PlayCommand.PLAY_NOW,
                    controllingUserId = UUID.randomUUID(),
                    subtitleStreamIndex = null,
                    audioStreamIndex = null,
                    mediaSourceId = null,
                    startIndex = null
                ),
                messageId = UUID.randomUUID()
            )
        )
        delay(SETTLE_MS)

        // 30,000,000 ticks = 3000 ms.
        assertEquals(
            listOf(RemoteControlAction.Play(item.toString(), 3_000L)),
            h.remoteActions
        )
        h.stopCollecting()
    }

    // --- Slice 2: GeneralCommand subset -------------------------------------------------------

    @Test
    fun setAudioStreamIndex_mapsToPlayerCommand() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())

        h.factory.sockets.single().generalMessages.emit(
            generalCommand(GeneralCommandType.SET_AUDIO_STREAM_INDEX, mapOf("Index" to "2"))
        )
        delay(SETTLE_MS)

        assertEquals(listOf(PlayerCommand.SetAudioIndex(2)), h.playerCommands)
        h.stopCollecting()
    }

    @Test
    fun setSubtitleStreamIndex_negativeMeansOff() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        val socket = h.factory.sockets.single()

        socket.generalMessages.emit(
            generalCommand(GeneralCommandType.SET_SUBTITLE_STREAM_INDEX, mapOf("Index" to "3"))
        )
        socket.generalMessages.emit(
            generalCommand(GeneralCommandType.SET_SUBTITLE_STREAM_INDEX, mapOf("Index" to "-1"))
        )
        delay(SETTLE_MS)

        assertEquals(
            listOf(PlayerCommand.SetSubtitleIndex(3), PlayerCommand.SetSubtitleIndex(null)),
            h.playerCommands
        )
        h.stopCollecting()
    }

    @Test
    fun displayMessageAndSendString_emitServerNotices() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        val socket = h.factory.sockets.single()

        socket.generalMessages.emit(
            generalCommand(
                GeneralCommandType.DISPLAY_MESSAGE,
                mapOf("Header" to "Hi", "Text" to "Dinner is ready")
            )
        )
        socket.generalMessages.emit(
            generalCommand(GeneralCommandType.SEND_STRING, mapOf("String" to "hello"))
        )
        delay(SETTLE_MS)

        assertEquals(
            listOf(
                ServerNotice(text = "Dinner is ready", header = "Hi"),
                ServerNotice(text = "hello")
            ),
            h.notices
        )
        h.stopCollecting()
    }

    @Test
    fun displayContentAndNavigation_emitRemoteActions() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        val socket = h.factory.sockets.single()

        socket.generalMessages.emit(
            generalCommand(GeneralCommandType.DISPLAY_CONTENT, mapOf("ItemId" to "item-1"))
        )
        socket.generalMessages.emit(generalCommand(GeneralCommandType.GO_HOME))
        socket.generalMessages.emit(generalCommand(GeneralCommandType.GO_TO_SETTINGS))
        delay(SETTLE_MS)

        assertEquals(
            listOf(
                RemoteControlAction.ShowItem("item-1"),
                RemoteControlAction.GoHome,
                RemoteControlAction.GoToSettings
            ),
            h.remoteActions
        )
        h.stopCollecting()
    }

    @Test
    fun dpadProxy_dispatchesNavigationKey() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        val socket = h.factory.sockets.single()

        socket.generalMessages.emit(generalCommand(GeneralCommandType.MOVE_LEFT))
        socket.generalMessages.emit(generalCommand(GeneralCommandType.SELECT))
        socket.generalMessages.emit(generalCommand(GeneralCommandType.BACK))
        delay(SETTLE_MS)

        assertEquals(
            listOf(
                RemoteControlAction.DispatchKey(RemoteKey.LEFT),
                RemoteControlAction.DispatchKey(RemoteKey.SELECT),
                RemoteControlAction.DispatchKey(RemoteKey.BACK)
            ),
            h.remoteActions
        )
        h.stopCollecting()
    }

    @Test
    fun volumeGeneralCommands_areIgnored() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())
        val socket = h.factory.sockets.single()

        socket.generalMessages.emit(generalCommand(GeneralCommandType.SET_VOLUME, mapOf("Volume" to "50")))
        socket.generalMessages.emit(generalCommand(GeneralCommandType.VOLUME_UP))
        socket.generalMessages.emit(generalCommand(GeneralCommandType.MUTE))
        socket.generalMessages.emit(generalCommand(GeneralCommandType.TOGGLE_MUTE))
        delay(SETTLE_MS)

        assertTrue(h.playerCommands.isEmpty())
        assertTrue(h.remoteActions.isEmpty())
        assertTrue(h.notices.isEmpty())
        h.stopCollecting()
    }

    // --- Slice 2: server lifecycle ------------------------------------------------------------

    @Test
    fun serverShuttingDown_pausesAndNotifies() = runBlocking {
        val h = Harness()
        h.startCollecting(this)
        h.activeSession.value = session(UUID.randomUUID().toString())

        h.factory.sockets.single().shutdownMessages.emit(ServerShuttingDownMessage(messageId = UUID.randomUUID()))
        delay(SETTLE_MS)

        assertEquals(listOf(PlayerCommand.Pause), h.playerCommands)
        assertEquals(1, h.notices.size)
        h.stopCollecting()
    }

    private companion object {
        const val SETTLE_MS = 50L
    }
}
