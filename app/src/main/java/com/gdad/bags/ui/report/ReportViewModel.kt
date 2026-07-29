package com.gdad.bags.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.report.BusinessReport
import com.gdad.bags.domain.report.DashboardSummary
import com.gdad.bags.domain.report.ReportRepository
import com.gdad.bags.domain.report.ReportResult
import com.gdad.bags.ui.components.ContentState
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val dashboard: ContentState<DashboardSummary> = ContentState.Loading,
    val isDashboardRefreshing: Boolean = false,
    val dashboardMessage: String? = null,
    val dateFrom: String = "",
    val dateTo: String = "",
    val period: ContentState<BusinessReport> = ContentState.Empty("Load a date-range report."),
    val isPeriodLoading: Boolean = false,
    val periodMessage: String? = null,
)

class ReportViewModel(
    private val repository: ReportRepository,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Kathmandu")) },
) : ViewModel() {
    private val mutable = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = mutable.asStateFlow()
    private var session: UserSession? = null
    private var dashboardJob: Job? = null

    fun activate(active: UserSession?) {
        if (session == active) return
        session = active
        dashboardJob?.cancel()
        if (active == null || active.role == UserRole.SUPER_ADMIN) {
            mutable.value = ReportUiState(
                dashboard = ContentState.Empty("Business reports are not available for this account."),
            )
            return
        }
        val end = today()
        mutable.value = ReportUiState(
            dateFrom = end.minusDays(29).toString(),
            dateTo = end.toString(),
        )
        dashboardJob = viewModelScope.launch {
            repository.observeDashboard(active).collect { summary ->
                if (summary != null) {
                    mutable.update { it.copy(dashboard = ContentState.Ready(summary)) }
                }
            }
        }
        refreshDashboard()
    }

    fun refreshDashboard() {
        val active = session ?: return
        if (active.role == UserRole.SUPER_ADMIN || mutable.value.isDashboardRefreshing) return
        mutable.update { it.copy(isDashboardRefreshing = true, dashboardMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshDashboard(active)) {
                is ReportResult.Success -> mutable.update {
                    it.copy(
                        isDashboardRefreshing = false,
                        dashboardMessage = result.safeMessage,
                    )
                }
                is ReportResult.Failure -> mutable.update {
                    it.copy(
                        dashboard = if (it.dashboard is ContentState.Ready) {
                            it.dashboard
                        } else {
                            ContentState.Error(result.safeMessage)
                        },
                        isDashboardRefreshing = false,
                        dashboardMessage = result.safeMessage,
                    )
                }
            }
        }
    }

    fun setDateFrom(value: String) = mutable.update {
        it.copy(dateFrom = value.filterDateInput(), periodMessage = null)
    }

    fun setDateTo(value: String) = mutable.update {
        it.copy(dateTo = value.filterDateInput(), periodMessage = null)
    }

    fun loadPeriod() {
        val active = session ?: return
        if (active.role == UserRole.SUPER_ADMIN || mutable.value.isPeriodLoading) return
        val from = mutable.value.dateFrom
        val to = mutable.value.dateTo
        mutable.update {
            it.copy(
                period = ContentState.Loading,
                isPeriodLoading = true,
                periodMessage = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.loadPeriod(active, from, to)) {
                is ReportResult.Success -> mutable.update {
                    it.copy(
                        period = ContentState.Ready(result.value),
                        isPeriodLoading = false,
                        periodMessage = result.safeMessage,
                    )
                }
                is ReportResult.Failure -> mutable.update {
                    it.copy(
                        period = ContentState.Error(result.safeMessage),
                        isPeriodLoading = false,
                        periodMessage = result.safeMessage,
                    )
                }
            }
        }
    }

    private fun String.filterDateInput() = filter { it.isDigit() || it == '-' }.take(10)

    class Factory(private val repository: ReportRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReportViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(repository) as T
        }
    }
}
