package app.picnic.player.data.socket

import org.jellyfin.sdk.model.api.GeneralCommandType

/**
 * The remote-control capabilities this device advertises via `postCapabilities`.
 *
 * This device is a controllable target: [SUPPORTED_COMMANDS] lists exactly the
 * [GeneralCommandType]s [WebSocketManager] actually handles, so the server only ever offers
 * controls we honour. PlayState (pause/seek/…) and the Play cast command are separate message
 * types covered by `supportsMediaControl = true`, not [GeneralCommandType]s, so they are not
 * listed here.
 *
 * Per the fixed-output-TV decision the list **must never** contain a volume command
 * ([GeneralCommandType.SET_VOLUME], `VOLUME_UP`/`VOLUME_DOWN`, `MUTE`/`UNMUTE`/`TOGGLE_MUTE`);
 * [VOLUME_COMMANDS] guards that invariant in tests. Commands with no sensible TV target are
 * intentionally omitted (see WebSocketManager for the skip rationale): `GO_TO_SEARCH`,
 * `TOGGLE_OSD`/`TOGGLE_OSD_MENU`, `TOGGLE_CONTEXT_MENU`, `TOGGLE_FULLSCREEN`, `SET_REPEAT_MODE`,
 * `SET_SHUFFLE_QUEUE`, `PLAY_TRAILERS`.
 */
internal object SocketCapabilities {
    /** Remote [GeneralCommandType]s we handle. Exactly these are advertised (never volume). */
    val SUPPORTED_COMMANDS: List<GeneralCommandType> = listOf(
        // Media-track control on the active player.
        GeneralCommandType.SET_AUDIO_STREAM_INDEX,
        GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
        // Transient on-screen text.
        GeneralCommandType.DISPLAY_MESSAGE,
        GeneralCommandType.SEND_STRING,
        // Navigation that maps to an existing destination.
        GeneralCommandType.DISPLAY_CONTENT,
        GeneralCommandType.GO_HOME,
        GeneralCommandType.GO_TO_SETTINGS,
        // Phone-as-remote D-pad proxy.
        GeneralCommandType.MOVE_UP,
        GeneralCommandType.MOVE_DOWN,
        GeneralCommandType.MOVE_LEFT,
        GeneralCommandType.MOVE_RIGHT,
        GeneralCommandType.SELECT,
        GeneralCommandType.BACK,
        GeneralCommandType.PAGE_UP,
        GeneralCommandType.PAGE_DOWN
    )

    /** Volume/mute commands we never advertise — TV audio is fixed-output. */
    val VOLUME_COMMANDS: Set<GeneralCommandType> = setOf(
        GeneralCommandType.SET_VOLUME,
        GeneralCommandType.VOLUME_UP,
        GeneralCommandType.VOLUME_DOWN,
        GeneralCommandType.MUTE,
        GeneralCommandType.UNMUTE,
        GeneralCommandType.TOGGLE_MUTE
    )
}
