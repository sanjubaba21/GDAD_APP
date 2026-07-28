package com.gdad.bags.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteDtosTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun pinLoginRequestUsesHostedSnakeCaseContract() {
        val request = PinLoginRequestDto(
            loginId = "owner.synthetic",
            pin = SYNTHETIC_PIN,
            requestId = "10000000-0000-4000-8000-000000000001",
            deviceId = "installation-test",
        )

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"login_id\":\"owner.synthetic\""))
        assertTrue(encoded.contains("\"request_id\":"))
        assertTrue(encoded.contains("\"device_id\":"))
        assertFalse(encoded.contains("loginId"))
    }

    @Test
    fun pinLoginResponseDecodesTypedTokenContractAndIgnoresFutureFields() {
        val payload = """
            {
              "access_token": "synthetic-access",
              "refresh_token": "synthetic-refresh",
              "expires_in": 3600,
              "token_type": "bearer",
              "future_field": true
            }
        """.trimIndent()

        val decoded = json.decodeFromString<PinLoginResponseDto>(payload)

        assertEquals("synthetic-access", decoded.accessToken)
        assertEquals("synthetic-refresh", decoded.refreshToken)
        assertEquals(3600L, decoded.expiresIn)
        assertEquals("bearer", decoded.tokenType)
    }

    @Test
    fun authoritativeDtosDecodeExactDatabaseColumnNames() {
        val profile = json.decodeFromString<ProfileDto>(
            """{"user_id":"subject","display_name":"Owner","platform_role":"standard","disabled":false}""",
        )
        val membership = json.decodeFromString<MembershipDto>(
            """{"shop_id":"shop","role":"owner","active":true}""",
        )

        assertEquals("subject", profile.userId)
        assertEquals("Owner", profile.displayName)
        assertEquals("shop", membership.shopId)
        assertTrue(membership.active)
    }

    private companion object {
        const val SYNTHETIC_PIN = "482604"
    }
}
