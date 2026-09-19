@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.media3.extractor.ExtractorsFactory
import app.picnic.player.data.playback.profile.DeviceCapabilities
import com.suyashbelekar.exoplayerhdrutils.video.transformers.DoviStrategy

internal fun dolbyVisionExtractors(context: Context, extractors: ExtractorsFactory): ExtractorsFactory {
    val capabilities = DeviceCapabilities.fromDevice
    val strategy = doviProfile7Strategy(
        dolbyVisionOutputAvailable = displaySupportsDolbyVision(context),
        supportsNativeProfile7 = capabilities.supportsHevcDolbyVisionProfile7(),
        supportsProfile8 = capabilities.supportsHevcDolbyVisionProfile8()
    )
    return if (strategy == DoviStrategy.KEEP) {
        extractors
    } else {
        DolbyVisionExtractorsFactory(strategy, extractors)
    }
}

internal fun doviProfile7Strategy(
    dolbyVisionOutputAvailable: Boolean,
    supportsNativeProfile7: Boolean,
    supportsProfile8: Boolean
): DoviStrategy = when {
    !dolbyVisionOutputAvailable -> DoviStrategy.DISCARD
    supportsNativeProfile7 -> DoviStrategy.KEEP
    supportsProfile8 -> DoviStrategy.CONVERT_TO_P8
    else -> DoviStrategy.DISCARD
}

internal fun displaySupportsDolbyVision(context: Context): Boolean {
    val displayManager = context.getSystemService(DisplayManager::class.java) ?: return false
    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false

    @Suppress("DEPRECATION")
    val enabledHdrTypes = display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
    val physicallySupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        display.supportedModes.any { mode ->
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in mode.supportedHdrTypes
        }
    return physicallySupported && Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in enabledHdrTypes
}
