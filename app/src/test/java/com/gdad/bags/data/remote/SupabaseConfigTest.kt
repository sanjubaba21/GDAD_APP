package com.gdad.bags.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {
    private val safeKey = "sb_publishable_ABCDEFGHIJKLMNOPQRSTUVWXYZ_1234567890"

    @Test
    fun acceptsAndNormalizesHttpsOriginAndPublishableKey() {
        val config = SupabaseConfig(
            url = "  https://project-ref.supabase.co/  ",
            publishableKey = "  $safeKey  ",
        )

        assertTrue(config.isConfigured)
        assertNull(config.validationError)
        assertEquals("https://project-ref.supabase.co", config.normalizedUrl)
        assertEquals(safeKey, config.normalizedPublishableKey)
    }

    @Test
    fun rejectsBlankOrMalformedConfiguration() {
        val cases = listOf(
            SupabaseConfig("", ""),
            SupabaseConfig("not a URL", safeKey),
            SupabaseConfig("https://", safeKey),
        )

        cases.forEach { config ->
            assertFalse(config.isConfigured)
            assertTrue(config.validationError!!.isNotBlank())
        }
    }

    @Test
    fun rejectsCleartextAndNonOriginUrls() {
        val urls = listOf(
            "http://project-ref.supabase.co",
            "https://user:password@project-ref.supabase.co",
            "https://project-ref.supabase.co/rest/v1",
            "https://project-ref.supabase.co?debug=true",
            "https://project-ref.supabase.co#fragment",
        )

        urls.forEach { candidate ->
            assertFalse(SupabaseConfig(candidate, safeKey).isConfigured)
        }
    }

    @Test
    fun rejectsSecretLegacyAndMalformedKeys() {
        val keys = listOf(
            "sb_secret_ABCDEFGHIJKLMNOPQRSTUVWXYZ_1234567890",
            "service_role",
            "legacy-anon-jwt",
            "sb_publishable_short",
            "sb_publishable_ABCDEFGHIJKLMNOPQRSTUVWXYZ/123456",
        )

        keys.forEach { candidate ->
            val config = SupabaseConfig("https://project-ref.supabase.co", candidate)
            assertFalse(config.isConfigured)
            assertTrue(config.validationError!!.contains("client-safe"))
        }
    }
}
