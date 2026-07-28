package com.gdad.bags.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

data class CacheSnapshot(
    val owner: CacheOwner,
    val profile: CachedProfileEntity? = null,
    val memberships: List<CachedMembershipEntity> = emptyList(),
    val products: List<CachedProductEntity> = emptyList(),
    val stock: List<CachedStockSummaryEntity> = emptyList(),
    val vendors: List<CachedVendorEntity> = emptyList(),
    val recentSales: List<CachedRecentSaleEntity> = emptyList(),
    val accounts: List<CachedAccountEntity> = emptyList(),
    val dashboard: CachedDashboardSummaryEntity? = null,
    val notifications: List<CachedNotificationEntity> = emptyList(),
)

interface SessionCache {
    suspend fun activate(owner: CacheOwner)
    suspend fun purge()
}

class RoomCacheStore(private val database: RoomCacheDatabase) : SessionCache {
    private val identityDao get() = database.identityDao()
    private val readDao get() = database.readDao()
    private val writeDao get() = database.writeDao()
    private val outboxDao get() = database.outboxDao()

    override suspend fun activate(owner: CacheOwner) = database.withTransaction {
        val existing = identityDao.get()
        if (existing == null || !existing.matches(owner)) {
            clearAllOwnedState()
        }
        identityDao.put(owner.toEntity())
    }

    override suspend fun purge() = database.withTransaction {
        clearAllOwnedState()
        identityDao.clear()
    }

    /** Replaces every offline read model as one transaction; partial snapshots never publish. */
    suspend fun replaceSnapshot(snapshot: CacheSnapshot) = database.withTransaction {
        clearRows()

        snapshot.requireSingleOwner()
        identityDao.put(snapshot.owner.toEntity())
        snapshot.profile?.let { writeDao.putProfile(it) }
        writeDao.putMemberships(snapshot.memberships)
        writeDao.putProducts(snapshot.products)
        writeDao.putStock(snapshot.stock)
        writeDao.putVendors(snapshot.vendors)
        writeDao.putRecentSales(snapshot.recentSales)
        writeDao.putAccounts(snapshot.accounts)
        snapshot.dashboard?.let { writeDao.putDashboard(it) }
        writeDao.putNotifications(snapshot.notifications)
    }

    fun observeProfile(owner: CacheOwner): Flow<CachedProfileEntity?> =
        readDao.observeProfile(owner.userId, owner.tenantKey)

    fun observeMemberships(owner: CacheOwner): Flow<List<CachedMembershipEntity>> =
        readDao.observeMemberships(owner.userId, owner.tenantKey)

    fun observeProducts(owner: CacheOwner): Flow<List<CachedProductEntity>> =
        readDao.observeProducts(owner.userId, owner.tenantKey)

    fun observeStock(owner: CacheOwner): Flow<List<CachedStockSummaryEntity>> =
        readDao.observeStock(owner.userId, owner.tenantKey)

    fun observeVendors(owner: CacheOwner): Flow<List<CachedVendorEntity>> =
        readDao.observeVendors(owner.userId, owner.tenantKey)

    fun observeRecentSales(owner: CacheOwner): Flow<List<CachedRecentSaleEntity>> =
        readDao.observeRecentSales(owner.userId, owner.tenantKey)

    fun observeAccounts(owner: CacheOwner): Flow<List<CachedAccountEntity>> =
        readDao.observeAccounts(owner.userId, owner.tenantKey)

    fun observeDashboard(owner: CacheOwner): Flow<CachedDashboardSummaryEntity?> =
        readDao.observeDashboard(owner.userId, owner.tenantKey)

    fun observeNotifications(owner: CacheOwner): Flow<List<CachedNotificationEntity>> =
        readDao.observeNotifications(owner.userId, owner.tenantKey)

    private suspend fun clearRows() {
        writeDao.clearNotifications()
        writeDao.clearDashboards()
        writeDao.clearAccounts()
        writeDao.clearRecentSales()
        writeDao.clearVendors()
        writeDao.clearStock()
        writeDao.clearProducts()
        writeDao.clearMemberships()
        writeDao.clearProfiles()
    }

    private suspend fun clearAllOwnedState() {
        outboxDao.clearAll()
        clearRows()
    }

    private fun CacheSnapshot.requireSingleOwner() {
        val rows = buildList<OwnedCacheRow> {
            profile?.let(::add)
            addAll(memberships)
            addAll(products)
            addAll(stock)
            addAll(vendors)
            addAll(recentSales)
            addAll(accounts)
            dashboard?.let(::add)
            addAll(notifications)
        }
        require(rows.all {
            it.ownerUserId == owner.userId && it.ownerTenantKey == owner.tenantKey
        }) {
            "Cache snapshot contains data for another identity or tenant"
        }
    }

    private fun CacheIdentityEntity.matches(owner: CacheOwner): Boolean =
        userId == owner.userId && tenantKey == owner.tenantKey && shopId == owner.shopId

    private fun CacheOwner.toEntity(): CacheIdentityEntity = CacheIdentityEntity(
        userId = userId,
        shopId = shopId,
        tenantKey = tenantKey,
        activatedAtEpochMillis = System.currentTimeMillis(),
    )
}
