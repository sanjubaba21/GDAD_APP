package com.gdad.bags.data.report

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.report.*
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ProductionReportRepository(
    private val remote: ReportRemoteDataSource,
    private val cache: ReportCache,
) : ReportRepository {
    override fun observeDashboard(session: UserSession): Flow<DashboardSummary?> =
        cache.observe(session.owner(), session.role)

    override suspend fun refreshDashboard(session: UserSession): ReportResult<Unit> {
        if (!session.allowed()) return denied()
        return when (val result = remote.dashboard(session.owner())) {
            is RemoteResult.Failure -> result.error.failure("Unable to refresh dashboard data.")
            is RemoteResult.Success -> {
                val report = result.value.shapedFor(session) ?: return invalidResponse()
                cache.replace(session.owner(), report.summary())
                ReportResult.Success(Unit, "Dashboard refreshed from the trusted report.")
            }
        }
    }

    override suspend fun loadPeriod(
        session: UserSession,
        dateFrom: String,
        dateTo: String,
    ): ReportResult<BusinessReport> {
        if (!session.allowed()) return denied()
        if (!validRange(dateFrom, dateTo)) {
            return ReportResult.Failure(null, "Choose a valid date range of at most 367 days.")
        }
        return when (val result = remote.period(session.owner(), dateFrom, dateTo)) {
            is RemoteResult.Failure -> result.error.failure("Unable to load the business report.")
            is RemoteResult.Success -> result.value.shapedFor(session)?.let {
                ReportResult.Success(it, "Trusted business report loaded.")
            } ?: invalidResponse()
        }
    }

    private fun BusinessReport.shapedFor(session: UserSession): BusinessReport? {
        if (shopId != session.shopId || role != session.role) return null
        return if (session.role == UserRole.SALESMAN) {
            copy(
                costOfGoodsSoldPaisa = null,
                grossProfitPaisa = null,
                stockValuePaisa = null,
                vendorDueTotalPaisa = null,
                vendorDues = emptyList(),
                accountBalances = emptyList(),
                expensesPaisa = null,
            )
        } else {
            this
        }
    }

    private fun BusinessReport.summary() = DashboardSummary(
        salesPaisa = salesPaisa,
        profitPaisa = grossProfitPaisa,
        vendorDuePaisa = vendorDueTotalPaisa,
        cashBankPaisa = accountBalances.fold(0L) { total, account ->
            Math.addExact(total, account.balancePaisa)
        }.takeIf { role == UserRole.OWNER },
        lowStockCount = lowStockCount,
        generatedAtEpochMillis = generatedAtEpochMillis,
    )

    private fun validRange(from: String, to: String): Boolean = runCatching {
        val start = kotlinx.datetime.LocalDate.parse(from)
        val end = kotlinx.datetime.LocalDate.parse(to)
        end >= start && end.toEpochDays() - start.toEpochDays() <= 366
    }.getOrDefault(false)

    private fun RemoteFailure.failure(default: String) = ReportResult.Failure(
        this,
        when (kind) {
            RemoteErrorKind.UNAUTHORIZED -> "Business reports are not available for this account."
            RemoteErrorKind.VALIDATION -> "Choose a valid report date range."
            RemoteErrorKind.CONFLICT -> "Report data changed. Refresh and review it again."
            RemoteErrorKind.OFFLINE -> "You are offline. Saved dashboard values remain available."
            RemoteErrorKind.TIMEOUT -> "The report timed out. Check the connection and retry."
            RemoteErrorKind.RATE_LIMITED -> "Too many report requests. Wait before retrying."
            RemoteErrorKind.UNKNOWN -> default
        },
    )

    private fun denied() = ReportResult.Failure(
        null,
        "Business reports are not available for this account.",
    )

    private fun invalidResponse() = ReportResult.Failure(
        null,
        "The trusted report response did not match this session.",
    )

    private fun UserSession.allowed() =
        role in setOf(UserRole.OWNER, UserRole.SALESMAN) && shopId.isUuid()

    private fun UserSession.owner() = CacheOwner(userId, shopId)
    private fun String?.isUuid() = this != null && runCatching { UUID.fromString(this) }.isSuccess
}
