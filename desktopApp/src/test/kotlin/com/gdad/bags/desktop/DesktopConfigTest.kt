package com.gdad.bags.desktop

import com.gdad.bags.domain.model.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopConfigTest {
    @Test
    fun acceptsOnlyClientSafeSupabaseConfiguration() {
        assertTrue(
            DesktopConfig.fromValues(
                "https://exampleproject.supabase.co",
                "sb_publishable_abcdefghijklmnopqrstuvwxyz",
            ).supabase.isConfigured,
        )
        assertFalse(
            DesktopConfig.fromValues(
                "http://exampleproject.supabase.co",
                "sb_secret_abcdefghijklmnopqrstuvwxyz",
            ).supabase.isConfigured,
        )
    }

    @Test
    fun roleNavigationIsFailClosed() {
        assertEquals(listOf(DesktopFeature.ACCOUNTS), featuresFor(UserRole.SUPER_ADMIN))
        assertEquals(
            listOf(
                DesktopFeature.DASHBOARD,
                DesktopFeature.SALES,
                DesktopFeature.PRODUCTS,
                DesktopFeature.REPORTS,
            ),
            featuresFor(UserRole.SALESMAN),
        )
        assertTrue(DesktopFeature.ACCOUNTS in featuresFor(UserRole.OWNER))
    }
}
