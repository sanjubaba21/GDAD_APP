package com.gdad.bags.ui.returning

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.returning.PostedSaleReturn
import com.gdad.bags.domain.returning.SaleAllocation
import com.gdad.bags.domain.returning.SaleHistory
import com.gdad.bags.domain.returning.SaleHistoryEntry
import com.gdad.bags.domain.returning.SaleHistoryLine
import com.gdad.bags.ui.components.ContentState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SaleReturnScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun salesmanCanInspectHistoryButCannotSeeCostOrReturnAction() {
        render(UserRole.SALESMAN)
        compose.onNodeWithText("View details").performClick()
        compose.onNodeWithText("Returnable 1", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("FIFO allocations").assertCountEquals(0)
        compose.onAllNodesWithText("Return items").assertCountEquals(0)
    }

    @Test
    fun ownerSeesFifoDetailAndReturnAction() {
        render(UserRole.OWNER)
        compose.onNodeWithText("View details").performClick()
        compose.onNodeWithText("FIFO allocations").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Return items").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun receiptUsesAuthoritativeTotalsAndHidesCostFromSalesman() {
        render(UserRole.SALESMAN, POSTED)
        compose.onNodeWithText("Server-authoritative return receipt").assertIsDisplayed()
        compose.onNodeWithText("Return value Rs 100.00").assertIsDisplayed()
        compose.onNodeWithText("Refund Rs 80.00").assertIsDisplayed()
        compose.onAllNodesWithText("Restored FIFO cost", substring = true).assertCountEquals(0)
    }

    private fun render(role: UserRole, posted: PostedSaleReturn? = null) {
        compose.setContent {
            MaterialTheme {
                SaleReturnScreen(
                    UserSession(ACTOR, "Actor", role, SHOP),
                    SaleReturnUiState(
                        content = ContentState.Ready(HISTORY),
                        posted = posted,
                    ),
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
    }

    companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val ACTOR = "22222222-2222-4222-8222-222222222222"
        const val SALE = "33333333-3333-4333-8333-333333333333"
        const val LINE = "44444444-4444-4444-8444-444444444444"
        const val PRODUCT = "55555555-5555-4555-8555-555555555555"
        const val LOT = "66666666-6666-4666-8666-666666666666"
        const val RETURN = "77777777-7777-4777-8777-777777777777"
        val HISTORY = SaleHistory(
            listOf(
                SaleHistoryEntry(
                    SALE,
                    "partially_returned",
                    false,
                    "Customer",
                    "9800000000",
                    "2026-07-28",
                    "2026-07-28T10:00:00Z",
                    20000,
                    20000,
                    10000,
                    10000,
                    0,
                    listOf(
                        SaleHistoryLine(
                            LINE,
                            PRODUCT,
                            "Bag",
                            "B-1",
                            2,
                            10000,
                            20000,
                            1,
                            10000,
                            listOf(SaleAllocation(LOT, 2, 5000)),
                        ),
                    ),
                ),
            ),
        )
        val POSTED = PostedSaleReturn(RETURN, SALE, 10000, 8000, 0, 1, 5000, "returned")
    }
}
