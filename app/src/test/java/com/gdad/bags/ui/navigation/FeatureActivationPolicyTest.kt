package com.gdad.bags.ui.navigation

import com.gdad.bags.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureActivationPolicyTest {
    @Test
    fun dashboardActivatesOnlyVisibleSummaryData() {
        assertEquals(
            setOf(FeatureDataSlice.REPORTS, FeatureDataSlice.NOTIFICATIONS),
            FeatureActivationPolicy.requiredData(UserRole.OWNER, null),
        )
        assertEquals(
            setOf(FeatureDataSlice.NOTIFICATIONS),
            FeatureActivationPolicy.requiredData(UserRole.SUPER_ADMIN, null),
        )
    }

    @Test
    fun eachFeatureActivatesOnlyItsRequiredData() {
        val expected = mapOf(
            FeatureDestination.ACCOUNTS to setOf(FeatureDataSlice.ACCOUNTS),
            FeatureDestination.PRODUCTS to setOf(FeatureDataSlice.PRODUCTS),
            FeatureDestination.VENDORS to setOf(
                FeatureDataSlice.PURCHASES,
                FeatureDataSlice.VENDOR_FINANCE,
            ),
            FeatureDestination.STOCK_ADJUSTMENTS to setOf(FeatureDataSlice.STOCK),
            FeatureDestination.SALES to setOf(FeatureDataSlice.SALES),
            FeatureDestination.RETURNS to setOf(FeatureDataSlice.RETURNS),
            FeatureDestination.FINANCE to setOf(FeatureDataSlice.FINANCE),
            FeatureDestination.REPORTS to setOf(FeatureDataSlice.REPORTS),
            FeatureDestination.NOTIFICATIONS to setOf(FeatureDataSlice.NOTIFICATIONS),
        )

        expected.forEach { (destination, required) ->
            assertEquals(required, FeatureActivationPolicy.requiredData(UserRole.OWNER, destination))
        }
    }

    @Test
    fun unauthorizedDestinationActivatesNothing() {
        assertTrue(
            FeatureActivationPolicy.requiredData(UserRole.SALESMAN, FeatureDestination.FINANCE)
                .isEmpty(),
        )
        assertTrue(
            FeatureActivationPolicy.requiredData(UserRole.SUPER_ADMIN, FeatureDestination.SALES)
                .isEmpty(),
        )
    }
}
