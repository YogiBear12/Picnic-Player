package app.picnic.player.data.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeerrCredentialsTest {

    @Test
    fun legacyLinkWithoutMethod_readsAsJellyfin() {
        assertEquals(SeerrAuthMethod.JELLYFIN, parseSeerrAuthMethod(null))
        assertEquals(SeerrCredentials.Jellyfin("pw"), seerrCredentials(SeerrLastLogin(), password = "pw"))
    }

    @Test
    fun localLink_carriesEmailAndPassword() {
        assertEquals(
            SeerrCredentials.Local("a@b.c", "pw"),
            seerrCredentials(SeerrLastLogin(SeerrAuthMethod.LOCAL, "a@b.c"), password = "pw")
        )
    }

    @Test
    fun localLinkMissingEmail_isNull() {
        assertNull(seerrCredentials(SeerrLastLogin(SeerrAuthMethod.LOCAL), password = "pw"))
    }

    @Test
    fun linkWithoutPassword_isNull() {
        assertNull(seerrCredentials(SeerrLastLogin(), password = null))
        assertNull(seerrCredentials(SeerrLastLogin(SeerrAuthMethod.LOCAL, "a@b.c"), password = null))
    }
}
