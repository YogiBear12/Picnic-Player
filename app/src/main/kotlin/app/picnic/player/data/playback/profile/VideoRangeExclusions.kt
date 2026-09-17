package app.picnic.player.data.playback.profile

import org.jellyfin.sdk.model.api.VideoRangeType

/**
 * Builds the VideoRangeType values the device profile must reject for a codec family.
 *
 * Jellyfin's stream builder only remuxes/transcodes these ranges when a codec profile
 * is active whose condition fails for that range (see [excludeUnsupportedVideoRanges]).
 */
internal fun unsupportedHevcRangeTypes(
    supportsHevcDolbyVision: Boolean,
    supportsHevcDolbyVisionProfile7: Boolean,
    supportsHevcHDR10: Boolean,
    supportsHevcHDR10Plus: Boolean,
    forceDoviProfile7: Boolean,
    hevcDoviHdr10PlusBug: Boolean = DeviceDefects.hevcDoviHdr10PlusBug
): Set<String> = buildSet {
    add(VideoRangeType.DOVI_INVALID.serialName)

    if (!supportsHevcDolbyVisionProfile7 && !forceDoviProfile7) {
        add(VideoRangeType.DOVI_WITH_EL.serialName)
        if (!supportsHevcHDR10Plus && !hevcDoviHdr10PlusBug) {
            add(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName)
        }

        // Single-layer DV / HDR fallbacks are only rejected when there is no DV decoder.
        // Profile 7 EL exclusion above still applies when DV exists but EL does not.
        if (!supportsHevcDolbyVision) {
            add(VideoRangeType.DOVI.serialName)
            if (!supportsHevcHDR10) add(VideoRangeType.DOVI_WITH_HDR10.serialName)
            if (!supportsHevcHDR10Plus && !hevcDoviHdr10PlusBug) {
                add(VideoRangeType.DOVI_WITH_HDR10_PLUS.serialName)
            }
        }
    }

    if (!supportsHevcHDR10Plus) {
        add(VideoRangeType.HDR10_PLUS.serialName)
        if (!supportsHevcHDR10) add(VideoRangeType.HDR10.serialName)
    }

    if (hevcDoviHdr10PlusBug) {
        add(VideoRangeType.DOVI_WITH_HDR10_PLUS.serialName)
        add(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName)
    }
}

internal fun unsupportedAv1RangeTypes(
    supportsAV1DolbyVision: Boolean,
    supportsAV1HDR10: Boolean,
    supportsAV1HDR10Plus: Boolean
): Set<String> = buildSet {
    add(VideoRangeType.DOVI_INVALID.serialName)

    if (!supportsAV1DolbyVision) {
        add(VideoRangeType.DOVI.serialName)
        if (!supportsAV1HDR10) add(VideoRangeType.DOVI_WITH_HDR10.serialName)
        if (!supportsAV1HDR10Plus) add(VideoRangeType.DOVI_WITH_HDR10_PLUS.serialName)
    }

    if (!supportsAV1HDR10Plus) {
        add(VideoRangeType.HDR10_PLUS.serialName)
        if (!supportsAV1HDR10) add(VideoRangeType.HDR10.serialName)
    }
}
