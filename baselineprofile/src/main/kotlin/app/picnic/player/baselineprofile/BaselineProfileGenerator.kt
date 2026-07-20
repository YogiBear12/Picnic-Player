package app.picnic.player.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures a baseline profile for app startup and the initial home browse journey — the code
 * paths behind the cold-start jank (RenderThread first-draw + interpreted startup). The release
 * build AOT-compiles these ahead of first launch instead of JIT-warming them on device.
 *
 * Runs against the release applicationId (no `.debug` suffix) via the plugin's
 * nonMinifiedRelease variant. Generate with `./gradlew :app:generateBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "app.picnic.player",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // Let the home screen settle (backdrop, hero, first rows) so first-draw paths are covered.
        device.waitForIdle()

        // Light D-pad journey so list fling / focus-move paths land in the profile.
        repeat(4) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(4) {
            device.pressDPadUp()
            device.waitForIdle()
        }
    }
}
