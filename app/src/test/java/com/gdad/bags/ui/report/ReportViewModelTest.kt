package com.gdad.bags.ui.report

import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.data.report.ProductionReportRepositoryTest
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.report.*
import com.gdad.bags.ui.components.ContentState
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test
    fun cachedDashboardRemainsVisibleWhenOfflineRefreshFails() = runTest(dispatcher) {
        val repository = Repository(refreshFails = true)
        val viewModel = ReportViewModel(repository) { LocalDate.of(2026, 7, 29) }

        viewModel.activate(ProductionReportRepositoryTest.OWNER)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.dashboard is ContentState.Ready)
        assertEquals("2026-06-30", viewModel.state.value.dateFrom)
        assertEquals("2026-07-29", viewModel.state.value.dateTo)
        assertTrue(viewModel.state.value.dashboardMessage!!.contains("offline", ignoreCase = true))
    }

    @Test
    fun periodUsesSelectedDatesAndPublishesTrustedResult() = runTest(dispatcher) {
        val repository = Repository()
        val viewModel = ReportViewModel(repository) { LocalDate.of(2026, 7, 29) }
        viewModel.activate(ProductionReportRepositoryTest.OWNER)
        advanceUntilIdle()

        viewModel.setDateFrom("2026-07-01")
        viewModel.loadPeriod()
        advanceUntilIdle()

        assertEquals("2026-07-01", repository.lastFrom)
        assertTrue(viewModel.state.value.period is ContentState.Ready)
    }

    private class Repository(private val refreshFails: Boolean = false) : ReportRepository {
        var lastFrom: String? = null
        private val summary = DashboardSummary(0, 0, 0, 0, 0, 1_000)
        override fun observeDashboard(session: UserSession): Flow<DashboardSummary?> = flowOf(summary)
        override suspend fun refreshDashboard(session: UserSession): ReportResult<Unit> =
            if (refreshFails) {
                ReportResult.Failure(
                    RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
                    "You are offline. Saved dashboard values remain available.",
                )
            } else {
                ReportResult.Success(Unit, "refreshed")
            }
        override suspend fun loadPeriod(
            session: UserSession,
            dateFrom: String,
            dateTo: String,
        ): ReportResult<BusinessReport> {
            lastFrom = dateFrom
            return ReportResult.Success(
                ProductionReportRepositoryTest.report(session.role).copy(
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                ),
                "loaded",
            )
        }
    }
}
