package app.picnic.player.ui.watchtogether

import org.junit.Assert.assertEquals
import org.junit.Test

class LobbyPeopleTest {
    private fun line(vararg names: String) = lobbyPeople(names.toList(), you = "testerino", pictures = emptyMap()).line

    @Test
    fun namesUpToTwoOthersThenCounts() {
        assertEquals("Just you", line("Testerino"))
        assertEquals("You and Dale", line("Dale", "Testerino"))
        assertEquals("You, Dale and Sam", line("Dale", "Testerino", "Sam"))
        assertEquals("You and 3 others", line("Dale", "Testerino", "Sam", "Alan"))
        assertEquals("You and 4 others", line("Dale", "Testerino", "Sam", "Alan", "Zach"))
    }

    @Test
    fun putsYouFirstAndMatchesPicturesByName() {
        val people = lobbyPeople(listOf("Dale", "Testerino"), you = "testerino", pictures = mapOf("dale" to "d.jpg"))
        assertEquals(listOf(LobbyMember("Testerino", null), LobbyMember("Dale", "d.jpg")), people.members)
    }

    @Test
    fun webDefaultRoomNameReadsAsWatchParty() {
        assertEquals("Dale's watch party", watchPartyName("Dale's group"))
        assertEquals("Dale's watch party", watchPartyName("Dale's watch party"))
        assertEquals("Movie night", watchPartyName("Movie night"))
    }
}
