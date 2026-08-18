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
    fun rungsFor_hidesOnlyRungsThatExceedTheSource() {
        val rungs = rungsFor(source(videoBitrate = 9_000_000, totalBitrate = 9_600_000), fourK)
        assertEquals(
            listOf(QualityRung.P1080_8, QualityRung.P1080_6, QualityRung.P720_4, QualityRung.P720_3, QualityRung.P480_2),
            rungs
        )
    }

    @Test
    fun rungsFor_widensComparisonForEfficientCodecs() {
        val hevc = rungsFor(source(8_000_000, 13_000_000, "hevc"), fourK)
        val h264 = rungsFor(source(8_000_000, 13_000_000, "h264"), fourK)
        assertTrue(QualityRung.P1080_12 in hevc)
        assertTrue(QualityRung.P1080_12 !in h264)
    }

    @Test
    fun rungsFor_neverOffersARungThatCostsMoreThanTheFile() {
        val rungs = rungsFor(source(9_400_000, 10_000_000, "hevc"), fourK)
        assertTrue(QualityRung.P1080_12 !in rungs)
        assertEquals(QualityRung.P1080_8, rungs.first())
    }

    @Test
    fun qualityOptions_offersOriginalAlongsideTheRungs() {
        val options = qualityOptions(source(8_500_000, 9_200_000), fourK)
        assertEquals(QualityOption.Original, options.first())
    }

    @Test
    fun rungsFor_hidesEveryFourKRungBelowTheCeiling() {
        val roomy = source(80_000_000, 82_000_000, width = 3840, height = 2160)
        assertTrue(QualityRung.P2160_40 in rungsFor(roomy, fourK))
        assertTrue(QualityRung.P2160_20 in rungsFor(roomy, fourK))
        assertTrue(rungsFor(roomy, capped).none { it.height >= 2160 })
        assertEquals(QualityRung.P1080_12, rungsFor(roomy, capped).first())
    }

    @Test
    fun rungsFor_croppedScopeStillCountsAsTenEighty() {
        val rungs = rungsFor(source(20_000_000, 21_000_000, width = 1920, height = 816), fourK)
        assertTrue(QualityRung.P1080_12 in rungs)
        assertTrue(rungs.none { it.height >= 2160 })
    }

    @Test
    fun rungsFor_pillarboxedFourThreeStillCountsAsTenEighty() {
        val rungs = rungsFor(source(20_000_000, 21_000_000, width = 1440, height = 1080), fourK)
        assertTrue(QualityRung.P1080_12 in rungs)
        assertTrue(rungs.none { it.height >= 2160 })
    }

    @Test
    fun rungsFor_fourThreeFourKStillCountsAsFourK() {
        val rungs = rungsFor(source(30_000_000, 32_000_000, width = 2880, height = 2160), fourK)
        assertTrue(QualityRung.P2160_20 in rungs)
    }

    @Test
    fun rungsFor_dciFourKStillCountsAsFourK() {
        val rungs = rungsFor(source(30_000_000, 32_000_000, width = 4096, height = 2160), fourK)
        assertTrue(QualityRung.P2160_20 in rungs)
    }

    @Test
    fun rungsFor_fiveFortyNeverReachesSevenTwenty() {
        val narrow = rungsFor(source(5_000_000, 5_500_000, width = 720, height = 540), fourK)
        val wide = rungsFor(source(5_000_000, 5_500_000, width = 960, height = 540), fourK)
        assertEquals(listOf(QualityRung.P480_2), narrow)
        assertEquals(listOf(QualityRung.P480_2), wide)
    }

    @Test
    fun rungsFor_clampsASourceSmallerThanEveryFrame() {
        val rungs = rungsFor(source(5_000_000, 5_500_000, width = 640, height = 360), fourK)
        assertEquals(listOf(QualityRung.P480_2), rungs)
    }

    @Test
    fun rungsFor_ignoresTheFrameRuleWhenDimensionsAreMissing() {
        val rungs = rungsFor(source(80_000_000, 82_000_000), fourK)
        assertTrue(QualityRung.P2160_40 in rungs)
    }

    @Test
    fun defaultQualityLabel_namesTheRungOrOriginal() {
        assertEquals("Original", defaultQualityLabel(null))
        assertEquals("1080p (Medium) · 8 Mbps", defaultQualityLabel(QualityRung.P1080_8))
    }

    @Test
    fun qualityOptions_alwaysOffersOriginal() {
        val options = qualityOptions(source(38_000_000, 40_000_000), fourK)
        assertEquals(QualityOption.Original, options.first())
    }

    @Test
    fun qualityOptions_onlyOriginalWhenSourceIsBelowEveryRung() {
        val options = qualityOptions(source(900_000, 1_200_000), fourK)
        assertEquals(listOf(QualityOption.Original), options)
    }

    @Test
    fun automaticRung_picksTheBestPictureTheSourceSupports() {
        assertEquals(QualityRung.P2160_40, automaticRung(source(60_000_000, 62_000_000, "hevc"), fourK))
    }

    @Test
    fun automaticRung_obeysTheCeiling() {
        assertEquals(QualityRung.P1080_12, automaticRung(source(60_000_000, 62_000_000, "hevc"), capped))
    }

    @Test
    fun automaticRung_staysBelowASmallSource() {
        assertEquals(QualityRung.P720_3, automaticRung(source(3_500_000, 3_800_000), fourK))
    }

    @Test
    fun automaticRung_fallsBackToLowestRungWhenNothingFits() {
        assertEquals(QualityRung.P480_2, automaticRung(source(400_000, 600_000), fourK))
    }

    @Test
    fun conversionPlan_neverRenegotiatesASourceGrantedDirectPlay() {
        val plan = conversionPlan(
            NegotiatedSource(supportsDirectPlay = true, transcodingUrl = "/videos/x/master.m3u8", quality = uhd),
            requested = null,
            ceiling = capped
        )
        assertEquals(ConversionPlan.AsNegotiated, plan)
    }

    @Test
    fun conversionPlan_neverRenegotiatesWithoutATranscodingUrl() {
        val plan = conversionPlan(
            NegotiatedSource(supportsDirectPlay = null, transcodingUrl = null, quality = uhd),
            requested = null,
            ceiling = capped
        )
        assertEquals(ConversionPlan.AsNegotiated, plan)
    }

    @Test
    fun conversionPlan_appliesAProfileToAServerChosenConversion() {
        val hd = source(8_770_000, 8_770_000, width = 1920, height = 816)
        val plan = conversionPlan(converting(hd), requested = null, ceiling = capped)
        assertEquals(ConversionPlan.Renegotiate(QualityRung.P1080_8), plan)
    }

    @Test
    fun conversionPlan_dropsAServerChosenFourKConversionToTheCeiling() {
        val plan = conversionPlan(converting(uhd), requested = null, ceiling = capped)
        assertEquals(ConversionPlan.Renegotiate(QualityRung.P1080_12), plan)
    }

    @Test
    fun conversionPlan_honoursAnExplicitRungWithinTheCeiling() {
        assertEquals(
            ConversionPlan.AsNegotiated,
            conversionPlan(converting(uhd), requested = QualityRung.P1080_8, ceiling = capped)
        )
        assertEquals(
            ConversionPlan.AsNegotiated,
            conversionPlan(converting(uhd), requested = QualityRung.P2160_20, ceiling = fourK)
        )
    }

    @Test
    fun conversionPlan_appliesAProfileWhenTheSourceHasNoDimensions() {
        val plan = conversionPlan(converting(source(80_000_000, 82_000_000)), requested = null, ceiling = capped)
        assertEquals(ConversionPlan.Renegotiate(QualityRung.P1080_12), plan)
    }

    @Test
    fun conversionPlan_appliesAProfileToAConversionThatAlreadyFits() {
        val hd = source(20_000_000, 21_000_000, width = 1920, height = 1080)
        assertEquals(
            ConversionPlan.Renegotiate(QualityRung.P1080_12),
            conversionPlan(converting(hd), requested = null, ceiling = capped)
        )
    }

    @Test
    fun conversionPlan_clampsAnExplicitRungAboveTheCeiling() {
        val plan = conversionPlan(converting(uhd), requested = QualityRung.P2160_20, ceiling = capped)
        assertEquals(ConversionPlan.Renegotiate(QualityRung.P1080_12), plan)
    }

    private fun converting(quality: SourceQuality) = NegotiatedSource(
        supportsDirectPlay = false,
        transcodingUrl = "/videos/x/master.m3u8",
        quality = quality
    )

    @Test
    fun rungLabels_readAsResolutionAndQualifier() {
        assertEquals("4K (High)", QualityRung.P2160_40.label)
        assertEquals("4K (Medium)", QualityRung.P2160_20.label)
        assertEquals("1080p (High)", QualityRung.P1080_12.label)
        assertEquals("720p (Medium)", QualityRung.P720_3.label)
        assertEquals("480p", QualityRung.P480_2.label)
        assertEquals("40 Mbps", QualityRung.P2160_40.bitrateLabel)
        assertEquals("12 Mbps", QualityRung.P1080_12.bitrateLabel)
        assertEquals("2 Mbps", QualityRung.P480_2.bitrateLabel)
    }

    @Test
    fun rungSizeHints_areRoundedToLegibleFigures() {
        assertEquals("about 18 GB/hr", QualityRung.P2160_40.sizeHint)
        assertEquals("about 9 GB/hr", QualityRung.P2160_20.sizeHint)
        assertEquals("about 5.5 GB/hr", QualityRung.P1080_12.sizeHint)
        assertEquals("about 3.5 GB/hr", QualityRung.P1080_8.sizeHint)
        assertEquals("about 900 MB/hr", QualityRung.P480_2.sizeHint)
    }

    @Test
    fun conversionCeiling_reachesFourKOnlyWhenAllowed() {
        assertEquals(QualityRung.P2160_40, fourK)
        assertEquals(QualityRung.P1080_12, capped)
    }

    @Test
    fun rungsAreRestoredByName() {
        assertEquals(QualityRung.P720_4, QualityRung.named("P720_4"))
        assertEquals(QualityRung.P2160_40, QualityRung.named("P2160_40"))
        assertNull(QualityRung.named("P4320_80"))
        assertNull(QualityRung.named(null))
    }

    @Test
    fun sourceQuality_carriesTheVideoStreamDimensions() {
        val source = SourceQuality.of(
            30_000_000,
            streams(videoBitrate = 28_000_000, audioBitrates = listOf(640_000), width = 3840, height = 2160)
        )
        assertEquals(3840, source.width)
        assertEquals(2160, source.height)
    }

    private val fourK = QualityRung.conversionCeiling(allowFourK = true)

    private val capped = QualityRung.conversionCeiling(allowFourK = false)

    private val uhd = SourceQuality(80_000_000, 82_000_000, "hevc", 3840, 2160)

    private fun source(
        videoBitrate: Int?,
        totalBitrate: Int?,
        videoCodec: String = "h264",
        width: Int? = null,
        height: Int? = null
    ) = SourceQuality(videoBitrate, totalBitrate, videoCodec, width, height)

    private fun streams(
        videoBitrate: Int?,
        audioBitrates: List<Int?>,
        videoCodec: String = "h264",
        width: Int? = null,
        height: Int? = null
    ): List<MediaStream> = buildList {
        add(stream(MediaStreamType.VIDEO, videoBitrate, videoCodec, channels = null, width = width, height = height))
        audioBitrates.forEach { rate ->
            add(stream(MediaStreamType.AUDIO, rate, codec = "eac3", channels = 6))
        }
    }

    private fun stream(
        type: MediaStreamType,
        bitRate: Int?,
        codec: String,
        channels: Int?,
        width: Int? = null,
        height: Int? = null
    ) = MediaStream(
        type = type,
        bitRate = bitRate,
        codec = codec,
        channels = channels,
        width = width,
        height = height,
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
