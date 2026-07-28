package com.gdad.bags.ui.sale
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.sale.PostedSale
import com.gdad.bags.ui.components.ContentState
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)@Config(sdk=[35])class SaleCheckoutScreenTest{@get:Rule val compose=createComposeRule();@Test fun salesmanCannotRevealOwnerPricingOrCredit(){render(UserRole.SALESMAN);compose.onAllNodesWithText("Override price").assertCountEquals(0);compose.onAllNodesWithText("Make credit sale").assertCountEquals(0);compose.onNodeWithText("Full payment").assertIsDisplayed()};@Test fun ownerSeesPricingAndCreditControls(){render(UserRole.OWNER);compose.onNodeWithText("Override price").assertIsDisplayed();compose.onNodeWithText("Make credit sale").assertIsDisplayed()};@Test fun receiptDisplaysServerTotals(){render(UserRole.OWNER,PostedSale(SALE,12345,10000,2345,5000,1,2));compose.onNodeWithText("Server-authoritative FIFO receipt").assertIsDisplayed();compose.onNodeWithText("Total Rs 123.45").assertIsDisplayed()}
private fun render(role:UserRole,posted:PostedSale?=null){compose.setContent{MaterialTheme{SaleCheckoutScreen(UserSession(ACTOR,"Actor",role,SHOP),SaleUiState(ContentState.Ready(listOf(PRODUCT)),posted=posted),{},{},{})}}}companion object{const val ACTOR="10000000-0000-4000-8000-000000000001";const val SHOP="20000000-0000-4000-8000-000000000002";const val SALE="30000000-0000-4000-8000-000000000003";val PRODUCT=CatalogProduct("40000000-0000-4000-8000-000000000004","Bag","B-1",null,1000,1,2,1000,true)}}
