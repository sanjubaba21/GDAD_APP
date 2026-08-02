package com.gdad.bags.ui.notification

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.gdad.bags.data.notification.ProductionNotificationRepositoryTest
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.notification.NotificationCenter
import com.gdad.bags.ui.components.ContentState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listShowsUnreadRetentionAndOpensDetail() {
        var selected: String? = null
        render(onSelect = { selected = it })
        compose.onNodeWithText("1 unread").assertIsDisplayed()
        compose.onNodeWithText("rolling 90-day", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Low stock")[1].performClick()
        assertEquals(ProductionNotificationRepositoryTest.NOTIFICATION, selected)
    }

    @Test fun ownerProductDetailOffersAuthorizedRelatedRoute() {
        render(selected = true)
        compose.onNodeWithText("Back to notifications").assertIsDisplayed()
        compose.onNodeWithText("Open related record").assertIsDisplayed()
    }

    @Test fun salesmanFinanceDetailHidesUnauthorizedRelatedRoute() {
        val finance = ProductionNotificationRepositoryTest.active().copy(
            recordType = "expense",
            recordId = ProductionNotificationRepositoryTest.PRODUCT,
        )
        render(role = UserRole.SALESMAN, selected = true, notification = finance)
        compose.onAllNodesWithText("Open related record").assertCountEquals(0)
    }

    @Test fun emptyCategoryHasTruthfulState() {
        render(category = "expense")
        compose.onNodeWithText("No notifications in this category.").assertIsDisplayed()
    }

    private fun render(
        role: UserRole = UserRole.OWNER,
        selected: Boolean = false,
        category: String? = null,
        notification: com.gdad.bags.domain.notification.AppNotification =
            ProductionNotificationRepositoryTest.active(),
        onSelect: (String) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                NotificationScreen(
                    session = ProductionNotificationRepositoryTest.OWNER.copy(role = role),
                    state = NotificationUiState(
                        content = ContentState.Ready(NotificationCenter(listOf(notification))),
                        selectedId = notification.id.takeIf { selected },
                        category = category,
                    ),
                    onRefresh = {},
                    onCategory = {},
                    onSelect = onSelect,
                    onCloseDetail = {},
                    onOpenRelated = {},
                )
            }
        }
    }
}
