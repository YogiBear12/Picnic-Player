package app.picnic.player.data.socket

import org.jellyfin.sdk.model.api.GeneralCommandType

internal object SocketCapabilities {
    val SUPPORTED_COMMANDS: List<GeneralCommandType> = listOf(
        GeneralCommandType.SET_AUDIO_STREAM_INDEX,
        GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
        GeneralCommandType.DISPLAY_MESSAGE,
        GeneralCommandType.SEND_STRING,
        GeneralCommandType.DISPLAY_CONTENT,
        GeneralCommandType.GO_HOME,
        GeneralCommandType.GO_TO_SETTINGS,
        GeneralCommandType.MOVE_UP,
        GeneralCommandType.MOVE_DOWN,
        GeneralCommandType.MOVE_LEFT,
        GeneralCommandType.MOVE_RIGHT,
        GeneralCommandType.SELECT,
        GeneralCommandType.BACK,
        GeneralCommandType.PAGE_UP,
        GeneralCommandType.PAGE_DOWN
    )

    val VOLUME_COMMANDS: Set<GeneralCommandType> = setOf(
        GeneralCommandType.SET_VOLUME,
        GeneralCommandType.VOLUME_UP,
        GeneralCommandType.VOLUME_DOWN,
        GeneralCommandType.MUTE,
        GeneralCommandType.UNMUTE,
        GeneralCommandType.TOGGLE_MUTE
    )
}
