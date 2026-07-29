package com.gdad.bags.data.report

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.report.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProductionReportRepositoryTest {
    @Test
    fun ownerRefreshCachesOnlyTrustedDashboardAndLoadsPeriod() = runBlocking {
        val cache = Cache()
        val remote = Remote(report(UserRole.OWNER))
        val repository = ProductionReportRepository(remote, cache)

        assertTrue(repository.refreshDashboard(OWNER) is ReportResult.Success)
        val summary = repository.observeDashboard(OWNER).first()
        assertEquals(10_000L, summary?.salesPaisa)
        assertEquals(4_000L, summary?.profitPaisa)
        assertEquals(1_500L, summary?.cashBankPaisa)
        val period = repository.loadPeriod(OWNER, "2026-07-01", "2026-07-29")
        assertEquals(10_000L, (period as ReportResult.Success).value.salesPaisa)
    }

    @Test
    fun salesmanResponseIsDefensivelyStrippedBeforeCachingOrDisplay() = runBlocking {
        val cache = Cache()
        val remote = Remote(report(UserRole.SALESMAN).copy(
            costOfGoodsSoldPaisa = 6_000,
            grossProfitPaisa = 4_000,
            stockValuePaisa = 20_000,
            vendorDueTotalPaisa = 3_000,
            vendorDues = listOf(VendorDueReportItem(VENDOR, "Vendor", 3_000)),
            accountBalances = listOf(AccountBalanceReportItem(CASH, "Cash", "cash", 1_000)),
            expensesPaisa = 500,
        ))
        val repository = ProductionReportRepository(remote, cache)

        assertTrue(repository.refreshDashboard(SALESMAN) is ReportResult.Success)
        assertNull(cache.value.value?.profitPaisa)
        assertNull(cache.value.value?.vendorDuePaisa)
        val loaded = repository.loadPeriod(SALESMAN, "2026-07-01", "2026-07-29")
            as ReportResult.Success
        assertNull(loaded.value.grossProfitPaisa)
        assertTrue(loaded.value.vendorDues.isEmpty())
        assertTrue(loaded.value.accountBalances.isEmpty())
    }

    @Test
    fun invalidRangeAndSuperAdminNeverCallRemote() = runBlocking {
        val remote = Remote(report(UserRole.OWNER))
        val repository = ProductionReportRepository(remote, Cache())

        assertTrue(repository.loadPeriod(OWNER, "2026-07-29", "2026-07-01") is ReportResult.Failure)
        assertTrue(repository.refreshDashboard(OWNER.copy(role = UserRole.SUPER_ADMIN)) is ReportResult.Failure)
        assertEquals(0, remote.calls)
    }

    @Test
    fun mismatchedShopResponseFailsClosed() = runBlocking {
        val remote = Remote(report(UserRole.OWNER).copy(shopId = OTHER_SHOP))
        val cache = Cache()
        val repository = ProductionReportRepository(remote, cache)

        val result = repository.refreshDashboard(OWNER)
        assertTrue(result is ReportResult.Failure)
        assertNull(cache.value.value)
    }

    private class Remote(private val result: BusinessReport) : ReportRemoteDataSource {
        var calls = 0
        override suspend fun dashboard(owner: CacheOwner): RemoteResult<BusinessReport> {
            calls++
            return RemoteResult.Success(result)
        }
        override suspend fun period(
            owner: CacheOwner,
            dateFrom: String,
            dateTo: String,
        ): RemoteResult<BusinessReport> {
            calls++
            return RemoteResult.Success(result.copy(dateFrom = dateFrom, dateTo = dateTo))
        }
    }

    private class Cache : ReportCache {
        val value = MutableStateFlow<DashboardSummary?>(null)
        override fun observe(owner: CacheOwner, role: UserRole): Flow<DashboardSummary?> = value
        override suspend fun replace(owner: CacheOwner, summary: DashboardSummary) {
            value.value = summary
        }
    }

    companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val OTHER_SHOP = "99999999-9999-4999-8999-999999999999"
        const val ACTOR = "22222222-2222-4222-8222-222222222222"
        const val PRODUCT = "33333333-3333-4333-8333-333333333333"
        const val VENDOR = "44444444-4444-4444-8444-444444444444"
        const val CASH = "55555555-5555-4555-8555-555555555555"
        val OWNER = UserSession(ACTOR, "Owner", UserRole.OWNER, SHOP)
        val SALESMAN = OWNER.copy(role = UserRole.SALESMAN)

        fun report(role: UserRole) = BusinessReport(
            shopId = SHOP,
            role = role,
            dateFrom = "2026-07-29",
            dateTo = "2026-07-29",
            salesPaisa = 10_000,
            returnsPaisa = 1_000,
            netSalesPaisa = 9_000,
            stockQuantity = 12,
            lowStockCount = 1,
            lowStockProducts = listOf(LowStockReportItem(PRODUCT, "BAG-1", "Bag", 2, 3)),
            costOfGoodsSoldPaisa = 5_000L.takeIf { role == UserRole.OWNER },
            grossProfitPaisa = 4_000L.takeIf { role == UserRole.OWNER },
            stockValuePaisa = 20_000L.takeIf { role == UserRole.OWNER },
            vendorDueTotalPaisa = 3_000L.takeIf { role == UserRole.OWNER },
            vendorDues = if (role == UserRole.OWNER) listOf(VendorDueReportItem(VENDOR, "Vendor", 3_000)) else emptyList(),
            accountBalances = if (role == UserRole.OWNER) listOf(
                AccountBalanceReportItem(CASH, "Cash", "cash", 1_000),
                AccountBalanceReportItem("66666666-6666-4666-8666-666666666666", "Bank", "bank", 500),
            ) else emptyList(),
            expensesPaisa = 500L.takeIf { role == UserRole.OWNER },
            generatedAtEpochMillis = 1_000,
        )
    }
}
