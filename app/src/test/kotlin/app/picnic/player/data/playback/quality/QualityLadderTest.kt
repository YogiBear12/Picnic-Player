package app.picnic.player.data.playback.quality

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityLadderTest {

    @Test
    fun sourceQuality_prefersReportedStreamBitrate() {
        val source = SourceQuality.of(10_000_000, streams(videoBitrate = 8_500_000, audioBitrates = listOf(640_000)))
        assertEquals(8_500_000, source.videoBitrate)
        assertEquals(10_000_000, source.totalBitrate)
    }

    @Test
    fun sourceQuality_discardsStreamBitrateEqualToContainerTotal() {
        val source = SourceQuality.of(10_000_000, streams(videoBitrate = 10_000_000, audioBitrates = listOf(640_000)))
        assertEquals(9_360_000, source.videoBitrate)
    }

    @Test
    fun sourceQuality_derivesWhenStreamBitrateMissing() {
        val source = SourceQuality.of(30_000_000, streams(videoBitrate = null, audioBitrates = listOf(6_000_000)))
        assertEquals(24_000_000, source.videoBitrate)
    }

    @Test
    fun sourceQuality_fallsBackToTotalWhenAudioUnknown() {
        val source = SourceQuality.of(12_000_000, streams(videoBitrate = null, audioBitrates = listOf(null)))
        assertEquals(12_000_000, source.videoBitrate)
    }

    @Test
    fun sourceQuality_nullWhenNothingReported() {
        assertNull(SourceQuality.of(null, streams(null, emptyList())).videoBitrate)
    }

    @Test
    fun rungsFor_hidesRungsAtOrAboveSource() {
        val rungs = rungsFor(source(videoBitrate = 9_200_000, totalBitrate = 9_800_000), ceiling = null)
        assertEquals(
            listOf(QualityRung.P1080_6, QualityRung.P720_4, QualityRung.P720_3, QualityRung.P480_2),
            rungs
        )
    }

    @Test
    fun rungsFor_dropsRungWithinFifteenPercentOfSource() {
        val rungs = rungsFor(source(videoBitrate = 9_000_000, totalBitrate = 9_600_000), ceiling = null)
        assertTrue(QualityRung.P1080_8 !in rungs)
        assertTrue(QualityRung.P1080_6 in rungs)
    }

    @Test
    fun rungsFor_widensComparisonForEfficientCodecs() {
        val hevc = rungsFor(source(8_000_000, 8_600_000, "hevc"), ceiling = null)
        val h264 = rungsFor(source(8_000_000, 8_600_000, "h264"), ceiling = null)
        assertTrue(QualityRung.P1080_8 in hevc)
        assertTrue(QualityRung.P1080_8 !in h264)
    }

    @Test
    fun rungsFor_appliesCeilingIndependentlyOfSource() {
        val rungs = rungsFor(source(40_000_000, 42_000_000), ceiling = QualityRung.P1080_8)
        assertTrue(QualityRung.P1080_12 !in rungs)
        assertEquals(QualityRung.P1080_8, rungs.first())
    }

    @Test
    fun rungsFor_neverOffersARungThatCostsMoreThanTheFile() {
        val rungs = rungsFor(source(9_400_000, 10_000_000, "hevc"), ceiling = null)
        assertTrue(QualityRung.P1080_12 !in rungs)
        assertEquals(QualityRung.P1080_8, rungs.first())
    }

    @Test
    fun qualityOptions_offersOriginalWhenSourceFitsCeiling() {
        val options = qualityOptions(source(8_500_000, 9_200_000), ceiling = null)
        assertEquals(QualityOption.Original, options.first())
    }

    @Test
    fun qualityOptions_withholdsOriginalWhenSourceExceedsCeiling() {
        val options = qualityOptions(source(38_000_000, 40_000_000), ceiling = QualityRung.P1080_8)
        assertTrue(options.none { it is QualityOption.Original })
        assertEquals(QualityOption.Transcode(QualityRung.P1080_8), options.first())
    }

    @Test
    fun qualityOptions_onlyOriginalWhenSourceIsBelowEveryRung() {
        val options = qualityOptions(source(900_000, 1_200_000), ceiling = null)
        assertEquals(listOf(QualityOption.Original), options)
    }

    @Test
    fun automaticRung_picksBestPictureUnderSourceAndCeiling() {
        assertEquals(QualityRung.P1080_12, automaticRung(source(60_000_000, 62_000_000, "hevc"), ceiling = null))
    }

    @Test
    fun automaticRung_respectsCeiling() {
        assertEquals(
            QualityRung.P1080_8,
            automaticRung(source(60_000_000, 62_000_000), ceiling = QualityRung.P1080_8)
        )
    }

    @Test
    fun automaticRung_staysBelowASmallSource() {
        assertEquals(QualityRung.P720_3, automaticRung(source(3_500_000, 3_800_000), ceiling = null))
    }

    @Test
    fun automaticRung_fallsBackToLowestRungWhenNothingFits() {
        assertEquals(QualityRung.P480_2, automaticRung(source(400_000, 600_000), ceiling = null))
    }

    @Test
    fun rungLabels_readAsResolutionAndQualifier() {
        assertEquals("1080p (High)", QualityRung.P1080_12.label)
        assertEquals("720p (Medium)", QualityRung.P720_3.label)
        assertEquals("480p", QualityRung.P480_2.label)
        assertEquals("12 Mbps", QualityRung.P1080_12.bitrateLabel)
        assertEquals("2 Mbps", QualityRung.P480_2.bitrateLabel)
    }

    @Test
    fun rungSizeHints_areRoundedToLegibleFigures() {
        assertEquals("about 5.5 GB/hr", QualityRung.P1080_12.sizeHint)
        assertEquals("about 3.5 GB/hr", QualityRung.P1080_8.sizeHint)
        assertEquals("about 900 MB/hr", QualityRung.P480_2.sizeHint)
    }

    @Test
    fun ceilingLabel_namesTheRungOrMaximum() {
        assertEquals("Maximum", qualityCeilingLabel(null))
        assertEquals("1080p (Medium) · 8 Mbps", qualityCeilingLabel(QualityRung.P1080_8))
    }

    @Test
    fun rungsAreRestoredByName() {
        assertEquals(QualityRung.P720_4, QualityRung.named("P720_4"))
        assertNull(QualityRung.named("P2160_40"))
        assertNull(QualityRung.named(null))
    }

    private fun source(
        videoBitrate: Int?,
        totalBitrate: Int?,
        videoCodec: String = "h264"
    ) = SourceQuality(videoBitrate, totalBitrate, videoCodec)

    private fun streams(
        videoBitrate: Int?,
        audioBitrates: List<Int?>,
        videoCodec: String = "h264"
    ): List<MediaStream> = buildList {
        add(stream(MediaStreamType.VIDEO, videoBitrate, videoCodec, channels = null))
        audioBitrates.forEach { rate ->
            add(stream(MediaStreamType.AUDIO, rate, codec = "eac3", channels = 6))
        }
    }

    private fun stream(
        type: MediaStreamType,
        bitRate: Int?,
        codec: String,
        channels: Int?
    ) = MediaStream(
        type = type,
        bitRate = bitRate,
        codec = codec,
        channels = channels,
        index = 0,
        isInterlaced = false,
        isDefault = true,
        isForced = false,
        isHearingImpaired = false,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false
    )
}
