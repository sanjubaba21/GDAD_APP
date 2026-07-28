package com.gdad.bags.ui.stock

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.stock.*
import com.gdad.bags.ui.components.ContentState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35]) class StockManagementScreenTest{@get:Rule val compose=createComposeRule()
@Test fun ownerSeesCostLotsHistoryAndAdjustment(){render(UserRole.OWNER);compose.onNodeWithText("Adjust stock").assertIsDisplayed();compose.onNodeWithText("Stock value Rs 350.00").assertIsDisplayed();compose.onNodeWithText("FIFO lots").assertIsDisplayed()}
@Test fun salesmanSeesOnHandButNoCostOrMutation(){render(UserRole.SALESMAN);compose.onNodeWithText("SKU B-1 • On hand 7").assertIsDisplayed();compose.onAllNodesWithText("Adjust stock").assertCountEquals(0);compose.onAllNodesWithText("Stock value Rs 350.00").assertCountEquals(0)}
private fun render(role:UserRole){compose.setContent{MaterialTheme{StockManagementScreen(UserSession(ACTOR,"Actor",role,SHOP),StockUiState(ContentState.Ready(WORKSPACE)),{},{},{},{},{})}}}
companion object{const val ACTOR="10000000-0000-4000-8000-000000000001";const val SHOP="20000000-0000-4000-8000-000000000002";const val PRODUCT="30000000-0000-4000-8000-000000000003";const val LOT="40000000-0000-4000-8000-000000000004";val WORKSPACE=StockWorkspace(listOf(CatalogProduct(PRODUCT,"Bag","B-1",null,10000,3,7,35000,true)),StockHistory(listOf(StockLot(LOT,PRODUCT,"purchase_receipt","date",5000,10,7)),listOf(StockMovement("m",PRODUCT,LOT,"purchase",10,5000,null,"2026-07-28","time"))))}}
