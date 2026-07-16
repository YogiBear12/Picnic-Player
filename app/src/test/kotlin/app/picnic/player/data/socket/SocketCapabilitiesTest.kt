package app.picnic.player.data.socket

import org.junit.Assert.assertTrue
import org.junit.Test

class SocketCapabilitiesTest {

    /** Fixed-output TV: no volume/mute command is ever advertised (v1 scope decision). */
    @Test
    fun supportedCommands_neverAdvertiseVolume() {
        val advertisedVolume = SocketCapabilities.SUPPORTED_COMMANDS.filter { it in SocketCapabilities.VOLUME_COMMANDS }
        assertTrue(
            "Volume commands must never be advertised, found: $advertisedVolume",
            advertisedVolume.isEmpty()
        )
    }
}
