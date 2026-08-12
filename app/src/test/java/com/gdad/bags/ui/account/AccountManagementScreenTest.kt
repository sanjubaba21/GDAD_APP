package com.gdad.bags.ui.account

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.gdad.bags.BuildConfig
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.ManagedAccount
import com.gdad.bags.domain.account.ManagedShop
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.ui.components.ContentState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountManagementScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun ownerSeesOnlySameShopSalesmenAndOwnerActionsStayHidden() {
        render(UserRole.OWNER, SHOP)

        compose.onNodeWithText("Create Salesman").assertIsDisplayed()
        compose.onNodeWithText("Same Shop Salesman").assertIsDisplayed()
        compose.onAllNodesWithText("Owner Target").assertCountEquals(0)
        compose.onAllNodesWithText("Other Shop Salesman").assertCountEquals(0)
    }

    @Test
    fun superAdminSeesOwnersAndShopsButNotSalesmen() {
        render(UserRole.SUPER_ADMIN, null)

        compose.onNodeWithText("Create Shop").assertIsDisplayed()
        compose.onNodeWithText("Create Owner").assertIsDisplayed()
        compose.onNodeWithText("Owner Target").assertIsDisplayed()
        compose.onAllNodesWithText("Same Shop Salesman").assertCountEquals(0)
        compose.onNodeWithText("Main Shop").assertIsDisplayed()
        compose.onNodeWithText(
            "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        ).assertIsDisplayed()
    }

    @Test
    fun ownerFormExplainsAndRejectsLoginIdOutsideHostedContract() {
        render(UserRole.SUPER_ADMIN, null)

        compose.onNodeWithText("Create Owner").performClick()
        compose.onNodeWithText(
            "3–64 lowercase letters, numbers, dots, underscores, or hyphens; start with a letter or number.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("account-display-name").performTextInput("Owner Name")
        compose.onNodeWithTag("account-new-pin").performTextInput("826491")
        compose.onNodeWithTag("account-login-id").performTextInput("owner name")
        compose.onNodeWithTag("account-create-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("account-login-id").performTextClearance()
        compose.onNodeWithTag("account-login-id").performTextInput("owner.name")
        compose.onNodeWithTag("account-create-confirm").assertIsEnabled()
    }

    @Test
    fun salesmanCannotRevealAnyAdministrationControl() {
        render(UserRole.SALESMAN, SHOP)

        compose.onNodeWithText("You are not allowed to manage accounts.").assertIsDisplayed()
        compose.onAllNodesWithText("Create Salesman").assertCountEquals(0)
        compose.onAllNodesWithText("Disable").assertCountEquals(0)
    }

    private fun render(role: UserRole, shopId: String?) {
        compose.setContent {
            MaterialTheme {
                AccountManagementScreen(
                    session = UserSession(ACTOR, "Actor", role, shopId),
                    state = AccountManagementUiState(role, ContentState.Ready(DIRECTORY)),
                    onRefresh = {}, onCreateShop = {}, onCreate = {}, onAdminister = {},
                )
            }
        }
    }

    private companion object {
        const val ACTOR = "10000000-0000-4000-8000-000000000001"
        const val SHOP = "20000000-0000-4000-8000-000000000002"
        const val OTHER_SHOP = "30000000-0000-4000-8000-000000000003"
        val DIRECTORY = AccountDirectory(
            accounts = listOf(
                ManagedAccount("40000000-0000-4000-8000-000000000004", SHOP, "owner.one", "Owner Target", UserRole.OWNER, false, true),
                ManagedAccount("50000000-0000-4000-8000-000000000005", SHOP, "sales.one", "Same Shop Salesman", UserRole.SALESMAN, false, true),
                ManagedAccount("60000000-0000-4000-8000-000000000006", OTHER_SHOP, "sales.two", "Other Shop Salesman", UserRole.SALESMAN, false, true),
            ),
            shops = listOf(ManagedShop(SHOP, "main-shop", "Main Shop", true)),
        )
    }
}
