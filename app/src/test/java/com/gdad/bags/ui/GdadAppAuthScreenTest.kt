package com.gdad.bags.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.notification.AppNotification
import com.gdad.bags.domain.notification.NotificationCenter
import com.gdad.bags.ui.auth.AuthUiState
import com.gdad.bags.ui.components.ContentState
import com.gdad.bags.ui.notification.NotificationUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GdadAppAuthScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun initializationIsTruthful() {
        compose.setContent {
            GdadApp(AuthUiState(), onLogin = { _, _ -> }, onInputChanged = {}, onLogout = {})
        }
        compose.onNodeWithText("Checking your secure session…").assertIsDisplayed()
    }

    @Test
    fun signedOutFailureIsTruthful() {
        compose.setContent {
            GdadApp(
                AuthUiState(isInitializing = false, errorMessage = "Session expired safely"),
                onLogin = { _, _ -> },
                onInputChanged = {},
                onLogout = {},
            )
        }
        compose.onNodeWithText("Session expired safely").assertIsDisplayed()
    }

    @Test
    fun loginSubmitsEnteredIdAndDigitLimitedPin() {
        var submitted: Pair<String, String>? = null
        compose.setContent {
            GdadApp(
                AuthUiState(isInitializing = false),
                onLogin = { id, pin -> submitted = id to pin },
                onInputChanged = {},
                onLogout = {},
            )
        }

        compose.onNodeWithText("User ID").performTextInput("owner.one")
        compose.onNodeWithText("PIN").performTextInput("12a34567890")
        compose.onNode(hasText("Sign in") and hasClickAction()).performClick()

        assertEquals("owner.one" to "12345678", submitted)
    }

    @Test
    fun authenticatedDashboardShowsIdentityUnreadBadgeAndConfirmedLogout() {
        var logoutCalls = 0
        compose.setContent {
            GdadApp(
                AuthUiState(session = OWNER, isInitializing = false),
                notificationUiState = NotificationUiState(
                    content = ContentState.Ready(NotificationCenter(NOTIFICATIONS)),
                ),
                onLogin = { _, _ -> },
                onInputChanged = {},
                onLogout = { logoutCalls += 1 },
            )
        }

        compose.onNodeWithText("Namaste, Owner One").assertIsDisplayed()
        compose.onNodeWithText("Owner dashboard • Nepal time").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("Log out").performClick()
        compose.onNodeWithText("Offline data for this account will be removed from this device.")
            .assertIsDisplayed()
        compose.onAllNodesWithText("Log out")[1].performClick()
        assertEquals(1, logoutCalls)
    }

    private companion object {
        val OWNER = UserSession(
            userId = "10000000-0000-4000-8000-000000000001",
            displayName = "Owner One",
            role = UserRole.OWNER,
            shopId = "20000000-0000-4000-8000-000000000002",
        )
        val NOTIFICATIONS = List(3) { index ->
            AppNotification(
                id = "30000000-0000-4000-8000-00000000000$index",
                shopId = requireNotNull(OWNER.shopId),
                category = "system",
                title = "Notice $index",
                body = "Body",
                recordType = "system",
                recordId = null,
                isRead = false,
                createdAtEpochMillis = 1,
                expiresAtEpochMillis = Long.MAX_VALUE,
            )
        }
    }
}
