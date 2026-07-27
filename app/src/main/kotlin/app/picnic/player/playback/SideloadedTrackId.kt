package app.picnic.player.playback

/**
 * Track ids for subtitles the player loads as their own file. The server's stream index travels in
 * the id so a chosen subtitle can be matched back to the player's track.
 */
object SideloadedTrackId {
    private const val PREFIX = "e:"

    fun of(streamIndex: Int): String = "$PREFIX$streamIndex"

    fun isSideloaded(formatId: String?): Boolean = indexOf(formatId) != null

    fun indexOf(formatId: String?): Int? = formatId?.substringAfterLast(PREFIX, missingDelimiterValue = "")?.toIntOrNull()
        ?.takeIf { formatId.contains(PREFIX) }
}
