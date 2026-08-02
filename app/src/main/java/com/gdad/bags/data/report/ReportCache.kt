package com.gdad.bags.data.report

import androidx.room.withTransaction
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedDashboardSummaryEntity
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.report.DashboardSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ReportCache {
    fun observe(owner: CacheOwner, role: UserRole): Flow<DashboardSummary?>
    suspend fun replace(owner: CacheOwner, summary: DashboardSummary)
}

class RoomReportCache(private val database: RoomCacheDatabase) : ReportCache {
    override fun observe(owner: CacheOwner, role: UserRole): Flow<DashboardSummary?> =
        database.readDao().observeDashboard(owner.userId, owner.tenantKey).map { cached ->
            cached?.let {
                DashboardSummary(
                    salesPaisa = it.salesPaisa,
                    profitPaisa = it.profitPaisa.takeIf { role == UserRole.OWNER },
                    vendorDuePaisa = it.vendorDuePaisa.takeIf { role == UserRole.OWNER },
                    cashBankPaisa = it.cashBankPaisa.takeIf { role == UserRole.OWNER },
                    lowStockCount = it.lowStockCount,
                    generatedAtEpochMillis = it.generatedAtEpochMillis,
                )
            }
        }

    override suspend fun replace(owner: CacheOwner, summary: DashboardSummary) =
        database.withTransaction {
            val identity = database.identityDao().get()
            require(identity?.userId == owner.userId && identity.tenantKey == owner.tenantKey)
            database.writeDao().putDashboard(
                CachedDashboardSummaryEntity(
                    ownerUserId = owner.userId,
                    ownerTenantKey = owner.tenantKey,
                    salesPaisa = summary.salesPaisa,
                    profitPaisa = summary.profitPaisa,
                    receivablesPaisa = 0,
                    vendorDuePaisa = summary.vendorDuePaisa,
                    cashBankPaisa = summary.cashBankPaisa,
                    lowStockCount = summary.lowStockCount,
                    generatedAtEpochMillis = summary.generatedAtEpochMillis,
                ),
            )
        }
}
