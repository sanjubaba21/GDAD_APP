package com.gdad.bags.ui.product

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.ui.components.ContentState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductCatalogScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ownerSeesCostAndManagementControls() {
        render(UserRole.OWNER)
        compose.onNodeWithText("Create product").assertIsDisplayed()
        compose.onNodeWithText("Stock value NPR 6400.00").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertIsDisplayed()
    }

    @Test fun salesmanCannotSeeCostOrManagementControls() {
        render(UserRole.SALESMAN)
        compose.onAllNodesWithText("Create product").assertCountEquals(0)
        compose.onAllNodesWithText("Stock value NPR 6400.00").assertCountEquals(0)
        compose.onAllNodesWithText("Edit").assertCountEquals(0)
    }

    @Test fun archivedProductRemainsVisibleWithoutMutationControls() {
        render(UserRole.OWNER, PRODUCTS.map { it.copy(active = false) })
        compose.onNodeWithText("Archived — historical use only").assertIsDisplayed()
        compose.onAllNodesWithText("Edit").assertCountEquals(0)
        compose.onAllNodesWithText("Archive").assertCountEquals(0)
    }

    private fun render(role: UserRole, products: List<CatalogProduct> = PRODUCTS) {
        compose.setContent {
            MaterialTheme {
                ProductCatalogScreen(
                    UserSession(ACTOR, "Actor", role, SHOP),
                    ProductCatalogUiState(role, ContentState.Ready(products)),
                    {}, {}, { _, _ -> },
                )
            }
        }
    }

    private companion object {
        const val ACTOR = "10000000-0000-4000-8000-000000000001"
        const val SHOP = "20000000-0000-4000-8000-000000000002"
        val PRODUCTS = listOf(CatalogProduct("30000000-0000-4000-8000-000000000003", "Travel Bag", "TB-1", "890123", 150000, 4, 8, 640000, true))
    }
}
