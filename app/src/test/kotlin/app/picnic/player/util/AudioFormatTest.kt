package app.picnic.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFormatTest {

    @Test
    fun namesDolbyCodecsTheWayAViewerWould() {
        assertEquals("DD", AudioFormat(codec = "ac3").codecLabel())
        assertEquals("DD+", AudioFormat(codec = "eac3").codecLabel())
        assertEquals("TrueHD", AudioFormat(codec = "truehd").codecLabel())
        assertEquals("DTS-HD", AudioFormat(codec = "dtshd_ma").codecLabel())
        assertEquals("AAC", AudioFormat(codec = "aac").codecLabel())
    }

    @Test
    fun passesAnUnknownCodecThroughUppercased() {
        assertEquals("VORBIS", AudioFormat(codec = "vorbis").codecLabel())
        assertEquals(null, AudioFormat(codec = " ").codecLabel())
    }

    @Test
    fun readsSpatialFormatFromWhicheverFieldCarriesIt() {
        assertEquals("Atmos", AudioFormat(spatialFormat = "DolbyAtmos").spatialLabel())
        assertEquals("Atmos", AudioFormat(profile = "Dolby Atmos").spatialLabel())
        assertEquals("Atmos", AudioFormat(displayTitle = "English - TrueHD Atmos 7.1").spatialLabel())
        assertEquals("DTS:X", AudioFormat(profile = "DTS:X").spatialLabel())
        assertEquals(null, AudioFormat(spatialFormat = "None", profile = "DTS-HD MA").spatialLabel())
    }

    @Test
    fun namesChannelsNumericallyAboveStereo() {
        assertEquals("Mono", AudioFormat(channels = 1).channelLabel())
        assertEquals("Stereo", AudioFormat(channels = 2).channelLabel())
        assertEquals("5.1", AudioFormat(channels = 6).channelLabel())
        assertEquals("7.1", AudioFormat(channels = 8).channelLabel())
        assertEquals("5.1", AudioFormat(channelLayout = "5.1").channelLabel())
        assertEquals("4ch", AudioFormat(channels = 4, channelLayout = "quad").channelLabel())
    }

    @Test
    fun spatialFormatReplacesTheChannelCount() {
        assertEquals(
            "TrueHD Atmos",
            AudioFormat("truehd", 8, "7.1", spatialFormat = "DolbyAtmos").label()
        )
        assertEquals(
            "DD+ Atmos",
            AudioFormat("eac3", 6, "5.1", profile = "Dolby Digital+ Atmos").label()
        )
    }

    @Test
    fun keepsTheChannelCountWithoutASpatialFormat() {
        assertEquals("DD 5.1", AudioFormat("ac3", 6, "5.1").label())
        assertEquals("AAC Stereo", AudioFormat("aac", 2, "stereo").label())
        assertEquals("FLAC 7.1", AudioFormat("flac", 8).label())
    }

    @Test
    fun namesWhicheverHalfIsKnown() {
        assertEquals("DTS", AudioFormat(codec = "dts").label())
        assertEquals("Stereo", AudioFormat(channels = 2).label())
        assertEquals(null, AudioFormat().label())
    }
}
