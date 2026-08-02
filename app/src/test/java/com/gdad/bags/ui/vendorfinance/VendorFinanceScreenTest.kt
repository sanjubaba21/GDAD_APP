package com.gdad.bags.ui.vendorfinance
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.purchase.Vendor
import com.gdad.bags.domain.vendorfinance.*
import com.gdad.bags.ui.components.ContentState
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)@Config(sdk=[35])class VendorFinanceScreenTest{@get:Rule val compose=createComposeRule();@Test fun salesmanDenied(){render(UserRole.SALESMAN);compose.onNodeWithText("Owner vendor-finance access is required.").assertIsDisplayed();compose.onAllNodesWithText("Pay bills").assertCountEquals(0)};@Test fun ownerSeesDueAndActions(){render(UserRole.OWNER);compose.onNodeWithText("Total NPR 100.00 • Due NPR 100.00").assertIsDisplayed();compose.onNodeWithText("Pay bills").assertIsDisplayed();compose.onNodeWithText("Return stock").assertIsDisplayed()};@Test fun receiptUsesServerValues(){render(UserRole.OWNER,VendorFinanceReceipt.Payment(PostedVendorPayment(EVENT,VENDOR,4000,1,6000)));compose.onNodeWithText("Server-authoritative result").assertIsDisplayed();compose.onNodeWithText("Payment NPR 40.00").assertIsDisplayed();compose.onNodeWithText("Vendor due NPR 60.00").assertIsDisplayed()}
 private fun render(role:UserRole,receipt:VendorFinanceReceipt?=null){compose.setContent{MaterialTheme{VendorFinanceScreen(UserSession(ACTOR,"Actor",role,SHOP),listOf(Vendor(VENDOR,"Vendor",null,null,null,10000,true)),VendorFinanceUiState(ContentState.Ready(LEDGER),receipt=receipt),{},{},{},{},{})}}}
 companion object{const val SHOP="11111111-1111-4111-8111-111111111111";const val ACTOR="22222222-2222-4222-8222-222222222222";const val VENDOR="33333333-3333-4333-8333-333333333333";const val BILL="44444444-4444-4444-8444-444444444444";const val LINE="55555555-5555-4555-8555-555555555555";const val EVENT="66666666-6666-4666-8666-666666666666";val LEDGER=VendorLedger(listOf(VendorBill(BILL,VENDOR,"received","INV","2026-07-29","2026-07-29T00:00:00Z",10000,10000,listOf(VendorReceiptLine(LINE,"77777777-7777-4777-8777-777777777777","Bag","B-1",2,5000,0,2)))))} }
