package app.picnic.player.data.playback.profile

import android.os.Build

/**
 * Device-specific codec defects that MediaCodec capability probes do not surface.
 */
internal object DeviceDefects {
    /** Fire TV models that crash or black-screen on HEVC Dolby Vision + HDR10+ hybrids. */
    private val modelsWithHevcDoviHdr10PlusBug = setOf(
        "AFTKRT", // Fire TV Stick 4K Max (2nd Gen)
        "AFTKA", // Fire TV Stick 4K Max (1st Gen)
        "AFTKM" // Fire TV Stick 4K (2nd Gen)
    )

    val hevcDoviHdr10PlusBug: Boolean
        get() = Build.MODEL in modelsWithHevcDoviHdr10PlusBug
}
