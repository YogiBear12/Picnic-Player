package app.picnic.player.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures a baseline profile for the journeys behind cold-start jank (RenderThread first-draw +
 * interpreted startup). The release build AOT-compiles these ahead of first launch instead of
 * JIT-warming them on device.
 *
 * Runs against the release applicationId (no `.debug` suffix) via the plugin's nonMinifiedRelease
 * variant. Generate with `./gradlew :app:generateBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start to first frame. Kept narrow — this is the only journey in the startup profile. */
    @Test
    fun startup() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    /** Home rows, then into a detail screen and back. */
    @Test
    fun browseAndDetail() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()

        // Let the home screen settle (backdrop, hero, first rows) so first-draw paths are covered.
        device.waitForIdle()

        // Vertical row traversal, then back to the top.
        repeat(4) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(4) {
            device.pressDPadUp()
            device.waitForIdle()
        }

        // Fling along a row so lazy-row scroll and card image loading land in the profile.
        device.pressDPadDown()
        repeat(5) {
            device.pressDPadRight()
            device.waitForIdle()
        }

        // Open the focused card's detail screen, scroll its rows, then return home.
        device.pressDPadCenter()
        device.waitForIdle()
        repeat(2) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        device.pressBack()
        device.waitForIdle()
    }

    /** Navigation drawer into a library grid, which exercises paging and grid layout. */
    @Test
    fun library() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Left off the content area opens the drawer, focused on the current destination (Home).
        device.pressDPadLeft()
        device.waitForIdle()

        // Home -> Anime Movies -> Anime TV -> Movies.
        repeat(3) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        device.pressDPadCenter()
        device.waitForIdle()

        // Scroll the grid so paging and off-screen card binding are captured.
        repeat(4) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(3) {
            device.pressDPadRight()
            device.waitForIdle()
        }

        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "app.picnic.player"
    }
}
