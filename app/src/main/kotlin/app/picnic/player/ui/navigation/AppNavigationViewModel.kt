package app.picnic.player.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Hoists the application navigation stack out of the Compose UI layer.
 *
 * TV apps typically want navigation to reset when the app process is reclaimed
 * by the OS. A standard [ViewModel] naturally survives configuration changes
 * and fast UI recompositions, but is intentionally destroyed on process death.
 *
 * This guarantees a clean slate upon relaunch while keeping the [NavBackStack]
 * decoupled from the Compose Tree's strict Saveable scope.
 */
@HiltViewModel
class AppNavigationViewModel @Inject constructor() : ViewModel() {

    // The primary navigation stack.
    // Because this is a standard NavBackStack (not wrapped in rememberNavBackStack
    // which injects Nav 3's isPop internal trackers), we must manually track
    // pops in PicnicNavHost's transitionSpec.
    val backStack = NavBackStack<NavKey>(StartupKey)

    /** Deep link to apply once onboarding resolves to Browse (cold start). */
    var pendingDeepLink: NavKey? = null

    fun consumePendingDeepLink(): NavKey? {
        val key = pendingDeepLink
        pendingDeepLink = null
        return key
    }

    /**
     * Wipes the entire navigation history and navigates immediately to [key].
     */
    fun resetTo(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    /**
     * Pushes a new screen onto the stack.
     */
    fun push(key: NavKey) {
        backStack.add(key)
    }

    /**
     * Pops the top screen off the stack.
     */
    fun pop() {
        backStack.removeLastOrNull()
    }

    /**
     * Replaces the current top screen with a new one.
     */
    fun replaceTop(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }
}
