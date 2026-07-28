package com.gdad.bags.ui.purchase

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseAccount
import com.gdad.bags.domain.purchase.PurchaseDirectory
import com.gdad.bags.domain.purchase.Vendor
import com.gdad.bags.ui.components.ContentState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PurchaseManagementScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun ownerSeesVendorAndPurchaseControls() { render(UserRole.OWNER); compose.onNodeWithText("New purchase").assertIsDisplayed(); compose.onNodeWithText("Create vendor").assertIsDisplayed(); compose.onNodeWithText("Due Rs 250.00").assertIsDisplayed() }
    @Test fun salesmanCannotRevealPurchasing() { render(UserRole.SALESMAN); compose.onNodeWithText("Owner purchasing access is required.").assertIsDisplayed(); compose.onAllNodesWithText("New purchase").assertCountEquals(0) }
    @Test fun successUsesServerAuthoritativeTotals() { render(UserRole.OWNER, PostedPurchase(BILL, RECEIPT, null, 12345, 345, 12000, 1)); compose.onNodeWithText("Server-authoritative totals").assertIsDisplayed(); compose.onNodeWithText("Total Rs 123.45").assertIsDisplayed(); compose.onNodeWithText("Due Rs 120.00").assertIsDisplayed() }
    private fun render(role: UserRole, posted: PostedPurchase? = null) { compose.setContent { MaterialTheme { PurchaseManagementScreen(UserSession(ACTOR,"Actor",role,SHOP), PurchaseManagementUiState(ContentState.Ready(WORKSPACE), postedPurchase=posted), {}, {_,_->}, {}, {}) } } }
    companion object {
        const val ACTOR="10000000-0000-4000-8000-000000000001"; const val SHOP="20000000-0000-4000-8000-000000000002"; const val VENDOR="30000000-0000-4000-8000-000000000003"; const val PRODUCT="40000000-0000-4000-8000-000000000004"; const val BILL="50000000-0000-4000-8000-000000000005"; const val RECEIPT="60000000-0000-4000-8000-000000000006"
        val WORKSPACE=PurchaseWorkspace(PurchaseDirectory(listOf(Vendor(VENDOR,"Vendor","9800","PAN",null,25000,true)),listOf(PurchaseAccount("70000000-0000-4000-8000-000000000007","Cash","cash",500000,true))),listOf(CatalogProduct(PRODUCT,"Bag","B-1",null,100000,2,3,200000,true)))
    }
}
