package app.picnic.player.ui.onboarding

import app.picnic.player.data.auth.ServerConnection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory handoff for the onboarding wizard: the server the Login
 * screen should sign in to is held here, not passed as a route query param and
 * not persisted to disk until login succeeds. Both entry points into Login —
 * the server-entry step (first use / add server) and the Profile Picker (a
 * stored user without a cached token) — set [pendingServer] before navigating.
 *
 * Discarded on back-out from Login; cleared once login completes.
 */
@Singleton
class OnboardingSession @Inject constructor() {
    /** The server the Login screen will authenticate against. */
    var pendingServer: ServerConnection? = null

    /** Optional username to prefill on the password panel (from Profile Picker). */
    var prefillUsername: String? = null

    fun clear() {
        pendingServer = null
        prefillUsername = null
    }
}
