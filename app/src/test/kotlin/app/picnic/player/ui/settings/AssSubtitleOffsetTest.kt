package app.picnic.player.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AssSubtitleOffsetTest {
    @Test
    fun labelUsesSignedMilliseconds() {
        assertEquals("0 ms", assSubtitleOffsetLabel(0))
        assertEquals("+250 ms", assSubtitleOffsetLabel(250))
        assertEquals("-100 ms", assSubtitleOffsetLabel(-100))
    }

    @Test
    fun pickerCoversRangeInFiftyMillisecondSteps() {
        val selectedValues = mutableListOf<Int>()
        val picker = assSubtitleOffsetPicker(0, selectedValues::add)

        assertEquals("Default ASS subtitle offset", picker.title)
        assertEquals(81, picker.options.size)
        assertEquals("-2000 ms", picker.options.first().label)
        assertEquals("+2000 ms", picker.options.last().label)
        assertEquals("0 ms", picker.options.single { it.selected }.label)
        picker.options.forEach { it.onSelect() }
        assertEquals(-2000, selectedValues.first())
        assertEquals(2000, selectedValues.last())
        assertEquals(0, selectedValues[40])
        assertEquals(setOf(50), selectedValues.zipWithNext { a, b -> b - a }.toSet())
        assertEquals(selectedValues.map(::assSubtitleOffsetLabel), picker.options.map { it.label })
    }

    @Test
    fun pickerSelectsCurrentOffset() {
        listOf(-2000, -100, 0, 250, 2000).forEach { current ->
            val picker = assSubtitleOffsetPicker(current) {}
            assertEquals(assSubtitleOffsetLabel(current), picker.options.single { it.selected }.label)
        }
    }
}
