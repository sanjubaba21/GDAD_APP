package com.gdad.bags.ui.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.gdad.bags.domain.finance.*
import com.gdad.bags.domain.model.*
import com.gdad.bags.ui.components.ContentState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinanceScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun salesmanDenied() {
        render(UserRole.SALESMAN)
        compose.onNodeWithText("Owner finance access is required.").assertIsDisplayed()
        compose.onAllNodesWithText("New expense").assertCountEquals(0)
    }

    @Test fun ownerSeesBalancesAndActions() {
        render(UserRole.OWNER)
        compose.onNodeWithText("Cash").assertIsDisplayed()
        compose.onNodeWithText("Cash - Rs 100.00").assertIsDisplayed()
        compose.onNodeWithText("Transfer").assertIsDisplayed()
        compose.onNodeWithText("Reverse transaction").assertIsDisplayed()
    }

    @Test fun transferReceiptUsesServerBalances() {
        render(UserRole.OWNER, FinanceReceipt.Transfer(PostedTransfer(EVENT, 2_500, 7_500, 12_500)))
        compose.onNodeWithText("Server-authoritative result").assertIsDisplayed()
        compose.onNodeWithText("Transfer Rs 25.00").assertIsDisplayed()
        compose.onNodeWithText("From Rs 75.00 - To Rs 125.00").assertIsDisplayed()
    }

    @Test fun retryableFailureExposesExactOperationRetry() {
        var retries = 0
        render(UserRole.OWNER, canRetry = true, onRetry = { retries++ })
        compose.onNodeWithText("Retry same operation").performClick()
        org.junit.Assert.assertEquals(1, retries)
    }

    private fun render(
        role: UserRole,
        receipt: FinanceReceipt? = null,
        canRetry: Boolean = false,
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                FinanceScreen(
                    UserSession(ACTOR, "Actor", role, SHOP),
                    FinanceUiState(ContentState.Ready(LEDGER), canRetry = canRetry, receipt = receipt),
                    {}, onRetry, {}, {}, {}, {}, {},
                )
            }
        }
    }

    companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val ACTOR = "22222222-2222-4222-8222-222222222222"
        const val CASH = "33333333-3333-4333-8333-333333333333"
        const val EVENT = "44444444-4444-4444-8444-444444444444"
        val LEDGER = FinanceLedger(
            listOf(FinanceAccount(CASH, "Cash", "cash", 10_000, true)),
            listOf(
                FinanceTransaction(
                    EVENT,
                    "expense",
                    "Supplies",
                    "2026-07-29",
                    "2026-07-29T00:00:00Z",
                    null,
                    false,
                    listOf(AccountEffect(CASH, 0, 1_000)),
                ),
            ),
        )
    }
}
