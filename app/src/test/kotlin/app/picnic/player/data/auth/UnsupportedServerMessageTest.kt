package app.picnic.player.data.auth

import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.ServerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnsupportedServerMessageTest {

    @Test
    fun aServerBelowTheFloorIsNamedWithItsVersion() {
        val old = candidate(RecommendedServerIssue.UnsupportedServerVersion(ServerVersion(10, 11, 5, 0)))

        assertEquals(
            "This server runs Jellyfin 10.11.5. Picnic Player needs 12.0 or newer.",
            unsupportedServerMessage(listOf(candidate(RecommendedServerIssue.MissingVersion), old))
        )
    }

    @Test
    fun anOutdatedButSupportedServerIsNotRefused() {
        val current = candidate(RecommendedServerIssue.OutdatedServerVersion(ServerVersion(12, 0, 0, 0)))

        assertNull(unsupportedServerMessage(listOf(current)))
    }

    private fun candidate(issue: RecommendedServerIssue) = RecommendedServerInfo(
        address = "https://example.test",
        responseTime = 10,
        score = RecommendedServerInfoScore.OK,
        issues = listOf(issue),
        systemInfo = Result.failure(IllegalStateException())
    )
}
