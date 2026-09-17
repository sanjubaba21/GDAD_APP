package com.gdad.bags.desktop

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyPolicyTest {
    @Test
    fun privacyPolicyUsesPublicHttpsLocation() {
        val uri = URI(PRIVACY_POLICY_URL)

        assertEquals("https", uri.scheme)
        assertEquals("github.com", uri.host)
        assertEquals("/sanjubaba21/GDAD_APP/blob/main/docs/privacy-policy.md", uri.path)
    }

    @Test
    fun privacyPolicyOpensTheReviewedUri() {
        var opened: URI? = null

        assertTrue(openPrivacyPolicy { opened = it })
        assertEquals(URI(PRIVACY_POLICY_URL), opened)
    }

    @Test
    fun privacyPolicyBrowserFailureIsContained() {
        assertFalse(openPrivacyPolicy { error("Browser unavailable") })
    }
}
