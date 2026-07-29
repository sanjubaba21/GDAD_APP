package com.gdad.bags.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedStatesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingStateExposesAccessibleLabel() {
        compose.setContent {
            MaterialTheme {
                ContentStateHost<Unit>(ContentState.Loading, onRetry = {}) { }
            }
        }
        compose.onNodeWithContentDescription("Loading state").assertIsDisplayed()
    }

    @Test
    fun emptyStateExposesAccessibleRefresh() {
        var refreshes = 0
        compose.setContent {
            MaterialTheme {
                ContentStateHost<Unit>(
                    ContentState.Empty("No records are available yet."),
                    onRetry = { refreshes += 1 },
                ) { }
            }
        }
        compose.onNodeWithContentDescription("Empty state").assertIsDisplayed()
        compose.onNodeWithText("Refresh").performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun errorStateProvidesSafeRetry() {
        var retries = 0
        compose.setContent {
            MaterialTheme {
                ContentStateHost<Unit>(
                    ContentState.Error("Unable to load this screen."),
                    onRetry = { retries += 1 },
                ) { }
            }
        }
        compose.onNodeWithContentDescription("Error state").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun errorsAndOperationMessagesExposeLiveRegions() {
        compose.setContent {
            MaterialTheme {
                Column {
                    ContentStateHost<Unit>(ContentState.Error("Unable to load."), onRetry = {}) { }
                    StatusMessage("Saved safely")
                }
            }
        }

        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Assertive,
            ),
        ).assertIsDisplayed()
        compose.onNodeWithText("Saved safely").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ),
        )
    }

    @Test
    fun businessDateFieldExplainsInvalidIsoDate() {
        compose.setContent {
            MaterialTheme {
                Column {
                    BusinessDateField("2026-02-29", onValueChange = {})
                    BusinessDateField("2026-07-29", onValueChange = {})
                }
            }
        }

        compose.onAllNodesWithText("Use a real Nepal business date in YYYY-MM-DD format.")
            .assertCountEquals(1)
    }

    @Test
    fun confirmationCanBeCancelled() {
        var dismissals = 0
        compose.setContent {
            MaterialTheme {
                ConfirmationDialog(
                    title = "Continue?",
                    message = "Review this action.",
                    confirmLabel = "Continue",
                    onConfirm = {},
                    onDismiss = { dismissals += 1 },
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissals)
    }
}
