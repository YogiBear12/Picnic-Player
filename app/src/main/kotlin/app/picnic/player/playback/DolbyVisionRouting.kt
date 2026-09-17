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
    val strategy = doviProfile7Strategy(context)
    return if (strategy == DoviStrategy.KEEP) {
        extractors
    } else {
        DolbyVisionExtractorsFactory(strategy, extractors)
    }
}

internal fun doviProfile7Strategy(
    displaySupportsDolbyVision: Boolean,
    supportsNativeProfile7: Boolean,
    supportsProfile8: Boolean
): DoviStrategy = if (displaySupportsDolbyVision && !supportsNativeProfile7 && supportsProfile8) {
    DoviStrategy.CONVERT_TO_P8
} else {
    DoviStrategy.KEEP
}

internal fun doviProfile7Strategy(context: Context): DoviStrategy {
    val capabilities = DeviceCapabilities.fromDevice
    return doviProfile7Strategy(
        displaySupportsDolbyVision = displaySupportsDolbyVision(context),
        supportsNativeProfile7 = capabilities.supportsHevcDolbyVisionProfile7(),
        supportsProfile8 = capabilities.supportsHevcDolbyVisionProfile8()
    )
}

internal fun displaySupportsDolbyVision(context: Context): Boolean {
    return try {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            display.supportedModes.any { mode ->
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in mode.supportedHdrTypes
            }
        } else {
            @Suppress("DEPRECATION")
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in
                (display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0))
        }
    } catch (_: Exception) {
        false
    }
}
