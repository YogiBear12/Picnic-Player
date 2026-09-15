package app.picnic.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFormatLabelTest {

    @Test
    fun namesDolbyCodecsTheWayAViewerWould() {
        assertEquals("DD", audioCodecLabel("ac3"))
        assertEquals("DD+", audioCodecLabel("eac3"))
        assertEquals("TrueHD", audioCodecLabel("truehd"))
        assertEquals("DTS-HD", audioCodecLabel("dtshd_ma"))
        assertEquals("AAC", audioCodecLabel("aac"))
    }

    @Test
    fun passesAnUnknownCodecThroughUppercased() {
        assertEquals("VORBIS", audioCodecLabel("vorbis"))
        assertEquals(null, audioCodecLabel(" "))
    }

    @Test
    fun readsSpatialFormatFromWhicheverFieldCarriesIt() {
        assertEquals("Atmos", audioSpatialLabel("DolbyAtmos", null, null))
        assertEquals("Atmos", audioSpatialLabel(null, "Dolby Atmos", null))
        assertEquals("Atmos", audioSpatialLabel(null, null, "English - TrueHD Atmos 7.1"))
        assertEquals("DTS:X", audioSpatialLabel(null, "DTS:X", null))
        assertEquals(null, audioSpatialLabel("None", "DTS-HD MA", null))
    }

    @Test
    fun namesChannelsNumericallyAboveStereo() {
        assertEquals("Mono", audioChannelLabel(1, null))
        assertEquals("Stereo", audioChannelLabel(2, null))
        assertEquals("5.1", audioChannelLabel(6, null))
        assertEquals("7.1", audioChannelLabel(8, null))
        assertEquals("5.1", audioChannelLabel(null, "5.1"))
        assertEquals("4ch", audioChannelLabel(4, "quad"))
    }

    @Test
    fun spatialFormatReplacesTheChannelCount() {
        assertEquals("TrueHD Atmos", audioFormatLabel("truehd", 8, "7.1", "DolbyAtmos", null, null))
        assertEquals("DD+ Atmos", audioFormatLabel("eac3", 6, "5.1", null, "Dolby Digital+ Atmos", null))
    }

    @Test
    fun keepsTheChannelCountWithoutASpatialFormat() {
        assertEquals("DD 5.1", audioFormatLabel("ac3", 6, "5.1", null, null, null))
        assertEquals("AAC Stereo", audioFormatLabel("aac", 2, "stereo", null, null, null))
        assertEquals("FLAC 7.1", audioFormatLabel("flac", 8, null, null, null, null))
    }

    @Test
    fun namesWhicheverHalfIsKnown() {
        assertEquals("DTS", audioFormatLabel("dts", null, null, null, null, null))
        assertEquals("Stereo", audioFormatLabel(null, 2, null, null, null, null))
        assertEquals(null, audioFormatLabel(null, null, null, null, null, null))
    }
}
