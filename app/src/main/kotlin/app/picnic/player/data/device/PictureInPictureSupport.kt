package app.picnic.player.data.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device Picture-in-Picture capability. Primary gate:
 * [PackageManager.FEATURE_PICTURE_IN_PICTURE]. Used to hide PiP Settings / OSD
 * rows on devices that do not advertise support (Android TV / Fire TV majority).
 *
 * Rare false positives are acceptable (e.g. TV 14+ reporting the feature while
 * media PiP is policy-blocked) — enter already no-ops on failure.
 */
@Singleton
class PictureInPictureSupport @Inject constructor(
    @ApplicationContext context: Context
) {
    val isSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}
