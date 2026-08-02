package com.gdad.bags.ui.report

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import com.gdad.bags.data.report.ProductionReportRepositoryTest
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.report.DashboardSummary
import com.gdad.bags.ui.components.ContentState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dashboardUsesRealZeroStateAndShowsCacheAge() {
        compose.setContent {
            MaterialTheme {
                DashboardReportSection(
                    UserRole.OWNER,
                    ReportUiState(
                        dashboard = ContentState.Ready(DashboardSummary(0, 0, 0, 0, 0, System.currentTimeMillis())),
                    ),
                    {},
                )
            }
        }
        compose.onNodeWithText("Today's sales").assertIsDisplayed()
        compose.onAllNodesWithText("NPR 0.00").assertCountEquals(4)
        compose.onNodeWithText("Updated", substring = true).assertIsDisplayed()
    }

    @Test fun salesmanReportNeverRendersOwnerFinancialFields() {
        val report = ProductionReportRepositoryTest.report(UserRole.SALESMAN)
        render(UserRole.SALESMAN, report)
        compose.onNodeWithText("Net sales").assertExists()
        compose.onAllNodesWithText("Gross profit").assertCountEquals(0)
        compose.onAllNodesWithText("Vendor dues").assertCountEquals(0)
        compose.onAllNodesWithText("Cash and bank").assertCountEquals(0)
    }

    @Test fun emptyOwnerReportShowsTruthfulEmptyDetails() {
        val report = ProductionReportRepositoryTest.report(UserRole.OWNER).copy(
            salesPaisa = 0,
            returnsPaisa = 0,
            netSalesPaisa = 0,
            stockQuantity = 0,
            lowStockCount = 0,
            lowStockProducts = emptyList(),
            vendorDues = emptyList(),
            accountBalances = emptyList(),
        )
        render(UserRole.OWNER, report)
        val reportList = compose.onNodeWithTag("report-content")
        reportList.performScrollToNode(hasText("No low-stock products."))
        compose.onNodeWithText("No low-stock products.").assertIsDisplayed()
        reportList.performScrollToNode(hasText("No vendor dues."))
        compose.onNodeWithText("No vendor dues.").assertIsDisplayed()
        reportList.performScrollToNode(hasText("No active cash or bank accounts."))
        compose.onNodeWithText("No active cash or bank accounts.").assertIsDisplayed()
    }

    @Test fun reportDateControlsRemainUsableAtTwoHundredPercentFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    ReportScreen(
                        ProductionReportRepositoryTest.OWNER,
                        ReportUiState(
                            dateFrom = "2026-07-01",
                            dateTo = "2026-07-29",
                        ),
                        {}, {}, {},
                    )
                }
            }
        }

        compose.onNodeWithText("From date (Nepal) — YYYY-MM-DD").assertIsDisplayed()
        compose.onNodeWithText("To date (Nepal) — YYYY-MM-DD").assertIsDisplayed()
        compose.onNodeWithText("Load report").assertIsDisplayed()
    }

    private fun render(role: UserRole, report: com.gdad.bags.domain.report.BusinessReport) {
        compose.setContent {
            MaterialTheme {
                ReportScreen(
                    ProductionReportRepositoryTest.OWNER.copy(role = role),
                    ReportUiState(
                        dateFrom = report.dateFrom,
                        dateTo = report.dateTo,
                        period = ContentState.Ready(report),
                    ),
                    {}, {}, {},
                )
            }
        }
    }
}
