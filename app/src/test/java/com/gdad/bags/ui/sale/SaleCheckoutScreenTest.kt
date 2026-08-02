package com.gdad.bags.ui.sale

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.sale.PostedSale
import com.gdad.bags.ui.components.ContentState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SaleCheckoutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun salesmanCannotRevealOwnerPricingOrCredit() {
        render(UserRole.SALESMAN)
        compose.onAllNodesWithText("Override price").assertCountEquals(0)
        compose.onAllNodesWithText("Make credit sale").assertCountEquals(0)
        compose.onNodeWithText("Full payment").assertIsDisplayed()
    }

    @Test
    fun ownerSeesPricingAndCreditControls() {
        render(UserRole.OWNER)
        compose.onNodeWithText("Override price").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Make credit sale").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun receiptDisplaysServerTotals() {
        render(UserRole.OWNER, PostedSale(SALE, 12_345, 10_000, 2_345, 5_000, 1, 2))
        compose.onNodeWithText("Server-authoritative FIFO receipt").assertIsDisplayed()
        compose.onNodeWithText("Total NPR 123.45").assertIsDisplayed()
    }

    @Test
    fun checkoutActionRemainsReachableAtTwoHundredPercentFontScale() {
        render(UserRole.OWNER, fontScale = 2f)
        compose.onNodeWithText("Confirm and post once").performScrollTo().assertIsDisplayed()
    }

    private fun render(
        role: UserRole,
        posted: PostedSale? = null,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    SaleCheckoutScreen(
                        UserSession(ACTOR, "Actor", role, SHOP),
                        SaleUiState(ContentState.Ready(listOf(PRODUCT)), posted = posted),
                        {},
                        {},
                        {},
                    )
                }
            }
        }
    }

    private companion object {
        const val ACTOR = "10000000-0000-4000-8000-000000000001"
        const val SHOP = "20000000-0000-4000-8000-000000000002"
        const val SALE = "30000000-0000-4000-8000-000000000003"
        val PRODUCT = CatalogProduct(
            "40000000-0000-4000-8000-000000000004",
            "Bag",
            "B-1",
            null,
            1_000,
            1,
            2,
            1_000,
            true,
        )
    }
}
