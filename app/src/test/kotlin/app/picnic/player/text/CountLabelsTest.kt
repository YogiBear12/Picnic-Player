package app.picnic.player.text

import org.junit.Assert.assertEquals
import org.junit.Test

class CountLabelsTest {

    @Test
    fun singularAndPlural() {
        assertEquals("1 item", countLabel(1, "item"))
        assertEquals("3 items", countLabel(3, "item"))
        assertEquals("0 items", countLabel(0, "item"))
    }

    @Test
    fun irregularPluralIsExplicit() {
        assertEquals("2 series", countLabel(2, "series", "series"))
    }
}
