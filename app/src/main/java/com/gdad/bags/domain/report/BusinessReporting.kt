package com.gdad.bags.domain.report

import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

data class LowStockReportItem(
    val productId: String,
    val sku: String,
    val name: String,
    val currentStock: Long,
    val threshold: Int,
)

data class VendorDueReportItem(val vendorId: String, val name: String, val duePaisa: Long)

data class AccountBalanceReportItem(
    val accountId: String,
    val name: String,
    val type: String,
    val balancePaisa: Long,
)

data class BusinessReport(
    val shopId: String,
    val role: UserRole,
    val dateFrom: String,
    val dateTo: String,
    val salesPaisa: Long,
    val returnsPaisa: Long,
    val netSalesPaisa: Long,
    val stockQuantity: Long,
    val lowStockCount: Int,
    val lowStockProducts: List<LowStockReportItem>,
    val costOfGoodsSoldPaisa: Long?,
    val grossProfitPaisa: Long?,
    val stockValuePaisa: Long?,
    val vendorDueTotalPaisa: Long?,
    val vendorDues: List<VendorDueReportItem>,
    val accountBalances: List<AccountBalanceReportItem>,
    val expensesPaisa: Long?,
    val generatedAtEpochMillis: Long,
)

data class DashboardSummary(
    val salesPaisa: Long,
    val profitPaisa: Long?,
    val vendorDuePaisa: Long?,
    val cashBankPaisa: Long?,
    val lowStockCount: Int,
    val generatedAtEpochMillis: Long,
)

sealed interface ReportResult<out T> {
    data class Success<T>(val value: T, val safeMessage: String) : ReportResult<T>
    data class Failure(val error: RemoteFailure?, val safeMessage: String) : ReportResult<Nothing>
}

interface ReportRepository {
    fun observeDashboard(session: UserSession): Flow<DashboardSummary?>
    suspend fun refreshDashboard(session: UserSession): ReportResult<Unit>
    suspend fun loadPeriod(
        session: UserSession,
        dateFrom: String,
        dateTo: String,
    ): ReportResult<BusinessReport>
}
