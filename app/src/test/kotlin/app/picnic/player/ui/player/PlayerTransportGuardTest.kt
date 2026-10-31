package app.picnic.player.ui.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTransportGuardTest {

    private val playerDir = File("src/main/kotlin/app/picnic/player/ui/player")

    // shortcut: matches the receiver name `player` only; an alias like `exo.pause()` slips through. Upgrade to a lint rule if aliases appear.
    private val transportWrite =
        Regex("""\bplayer\.(play\(\)|pause\(\)|stop\(\)|prepare\(\)|seekTo|setPlayWhenReady\(|setMediaItem|playWhenReady\s*=[^=])""")

    @Test
    fun onlyPlayerTransportWritesThePlayersTransport() {
        assertTrue("run from the app module: ${playerDir.absolutePath}", playerDir.isDirectory)
        val offenders = playerDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "PlayerTransport.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    "${file.name}:${i + 1}: ${line.trim()}".takeIf { transportWrite.containsMatchIn(line) }
                }
            }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun theGuardPatternCatchesEachWriteForm() {
        listOf(
            "player.play()",
            "player.pause()",
            "player.stop()",
            "player.prepare()",
            "player.seekTo(0)",
            "player.seekToDefaultPosition()",
            "player.setPlayWhenReady(true)",
            "player.setMediaItem(item, 0)",
            "player.playWhenReady = true"
        )
            .forEach { assertTrue(it, transportWrite.containsMatchIn(it)) }
        listOf("player.playWhenReady == true", "player.isPlaying", "transport.play()")
            .forEach { assertTrue(it, !transportWrite.containsMatchIn(it)) }
    }
}
