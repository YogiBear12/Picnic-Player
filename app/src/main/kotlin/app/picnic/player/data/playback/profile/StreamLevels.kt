package app.picnic.player.data.playback.profile

import android.media.MediaCodecInfo.CodecProfileLevel

internal const val UNKNOWN_STREAM_LEVEL = 0

private val avcStreamLevels = listOf(
    CodecProfileLevel.AVCLevel1 to 10,
    CodecProfileLevel.AVCLevel1b to 9,
    CodecProfileLevel.AVCLevel11 to 11,
    CodecProfileLevel.AVCLevel12 to 12,
    CodecProfileLevel.AVCLevel13 to 13,
    CodecProfileLevel.AVCLevel2 to 20,
    CodecProfileLevel.AVCLevel21 to 21,
    CodecProfileLevel.AVCLevel22 to 22,
    CodecProfileLevel.AVCLevel3 to 30,
    CodecProfileLevel.AVCLevel31 to 31,
    CodecProfileLevel.AVCLevel32 to 32,
    CodecProfileLevel.AVCLevel4 to 40,
    CodecProfileLevel.AVCLevel41 to 41,
    CodecProfileLevel.AVCLevel42 to 42,
    CodecProfileLevel.AVCLevel5 to 50,
    CodecProfileLevel.AVCLevel51 to 51,
    CodecProfileLevel.AVCLevel52 to 52,
    CodecProfileLevel.AVCLevel6 to 60,
    CodecProfileLevel.AVCLevel61 to 61,
    CodecProfileLevel.AVCLevel62 to 62
)

private val hevcStreamLevels = listOf(
    CodecProfileLevel.HEVCMainTierLevel1 to 30,
    CodecProfileLevel.HEVCHighTierLevel1 to 30,
    CodecProfileLevel.HEVCMainTierLevel2 to 60,
    CodecProfileLevel.HEVCHighTierLevel2 to 60,
    CodecProfileLevel.HEVCMainTierLevel21 to 63,
    CodecProfileLevel.HEVCHighTierLevel21 to 63,
    CodecProfileLevel.HEVCMainTierLevel3 to 90,
    CodecProfileLevel.HEVCHighTierLevel3 to 90,
    CodecProfileLevel.HEVCMainTierLevel31 to 93,
    CodecProfileLevel.HEVCHighTierLevel31 to 93,
    CodecProfileLevel.HEVCMainTierLevel4 to 120,
    CodecProfileLevel.HEVCHighTierLevel4 to 120,
    CodecProfileLevel.HEVCMainTierLevel41 to 123,
    CodecProfileLevel.HEVCHighTierLevel41 to 123,
    CodecProfileLevel.HEVCMainTierLevel5 to 150,
    CodecProfileLevel.HEVCHighTierLevel5 to 150,
    CodecProfileLevel.HEVCMainTierLevel51 to 153,
    CodecProfileLevel.HEVCHighTierLevel51 to 153,
    CodecProfileLevel.HEVCMainTierLevel52 to 156,
    CodecProfileLevel.HEVCHighTierLevel52 to 156,
    CodecProfileLevel.HEVCMainTierLevel6 to 180,
    CodecProfileLevel.HEVCHighTierLevel6 to 180,
    CodecProfileLevel.HEVCMainTierLevel61 to 183,
    CodecProfileLevel.HEVCHighTierLevel61 to 183,
    CodecProfileLevel.HEVCMainTierLevel62 to 186,
    CodecProfileLevel.HEVCHighTierLevel62 to 186
)

internal fun avcStreamLevel(codecLevel: Int): Int = highestStreamLevelWithin(avcStreamLevels, codecLevel)

internal fun hevcStreamLevel(codecLevel: Int): Int = highestStreamLevelWithin(hevcStreamLevels, codecLevel)

private fun highestStreamLevelWithin(streamLevels: List<Pair<Int, Int>>, codecLevel: Int): Int = streamLevels
    .lastOrNull { (decoderLevel, _) -> decoderLevel <= codecLevel }
    ?.second
    ?: UNKNOWN_STREAM_LEVEL
