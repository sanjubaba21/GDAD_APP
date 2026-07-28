package com.gdad.bags.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheIdentityDao {
    @Query("SELECT * FROM cache_identity WHERE slot = 1")
    suspend fun get(): CacheIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(identity: CacheIdentityEntity)

    @Query("DELETE FROM cache_identity")
    suspend fun clear()
}

@Dao
interface CacheReadDao {
    @Query(
        "SELECT * FROM cached_profiles " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey",
    )
    fun observeProfile(userId: String, tenantKey: String): Flow<CachedProfileEntity?>

    @Query(
        "SELECT * FROM cached_memberships " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey",
    )
    fun observeMemberships(userId: String, tenantKey: String): Flow<List<CachedMembershipEntity>>

    @Query(
        "SELECT * FROM cached_products " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey " +
            "ORDER BY name COLLATE NOCASE, id",
    )
    fun observeProducts(userId: String, tenantKey: String): Flow<List<CachedProductEntity>>

    @Query(
        "SELECT * FROM cached_stock_summaries " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey " +
            "ORDER BY is_low_stock DESC, product_id",
    )
    fun observeStock(userId: String, tenantKey: String): Flow<List<CachedStockSummaryEntity>>

    @Query(
        "SELECT * FROM cached_vendors " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey " +
            "ORDER BY name COLLATE NOCASE, id",
    )
    fun observeVendors(userId: String, tenantKey: String): Flow<List<CachedVendorEntity>>

    @Query(
        "SELECT * FROM cached_recent_sales " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey " +
            "ORDER BY sold_at_epoch_ms DESC, id DESC",
    )
    fun observeRecentSales(userId: String, tenantKey: String): Flow<List<CachedRecentSaleEntity>>

    @Query(
        "SELECT * FROM cached_accounts " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey " +
            "ORDER BY code, id",
    )
    fun observeAccounts(userId: String, tenantKey: String): Flow<List<CachedAccountEntity>>

    @Query(
        "SELECT * FROM cached_dashboard_summaries " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey",
    )
    fun observeDashboard(
        userId: String,
        tenantKey: String,
    ): Flow<CachedDashboardSummaryEntity?>

    @Query(
        "SELECT * FROM cached_notifications " +
            "WHERE owner_user_id = :userId AND owner_tenant_key = :tenantKey " +
            "ORDER BY created_at_epoch_ms DESC, id DESC",
    )
    fun observeNotifications(
        userId: String,
        tenantKey: String,
    ): Flow<List<CachedNotificationEntity>>
}

@Dao
interface CacheWriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProfile(value: CachedProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMemberships(values: List<CachedMembershipEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProducts(values: List<CachedProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStock(values: List<CachedStockSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putVendors(values: List<CachedVendorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecentSales(values: List<CachedRecentSaleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAccounts(values: List<CachedAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDashboard(value: CachedDashboardSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putNotifications(values: List<CachedNotificationEntity>)

    @Query("DELETE FROM cached_profiles")
    suspend fun clearProfiles()

    @Query("DELETE FROM cached_memberships")
    suspend fun clearMemberships()

    @Query("DELETE FROM cached_products")
    suspend fun clearProducts()

    @Query("DELETE FROM cached_stock_summaries")
    suspend fun clearStock()

    @Query("DELETE FROM cached_vendors")
    suspend fun clearVendors()

    @Query("DELETE FROM cached_recent_sales")
    suspend fun clearRecentSales()

    @Query("DELETE FROM cached_accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM cached_dashboard_summaries")
    suspend fun clearDashboards()

    @Query("DELETE FROM cached_notifications")
    suspend fun clearNotifications()
}
