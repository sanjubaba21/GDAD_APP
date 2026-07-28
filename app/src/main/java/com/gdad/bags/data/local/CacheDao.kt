package com.gdad.bags.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(value: OutboxEntity): Long

    @Query(
        "SELECT * FROM mutation_outbox WHERE owner_user_id = :userId " +
            "AND owner_tenant_key = :tenantKey ORDER BY created_at_epoch_ms",
    )
    fun observe(userId: String, tenantKey: String): Flow<List<OutboxEntity>>

    @Query(
        "SELECT * FROM mutation_outbox WHERE owner_user_id = :userId " +
            "AND owner_tenant_key = :tenantKey AND state IN ('PENDING','RETRY_WAIT') " +
            "AND next_attempt_at_epoch_ms <= :now ORDER BY created_at_epoch_ms LIMIT 1",
    )
    suspend fun nextReady(userId: String, tenantKey: String, now: Long): OutboxEntity?

    @Query(
        "UPDATE mutation_outbox SET state = 'IN_FLIGHT', attempt_count = attempt_count + 1, " +
            "updated_at_epoch_ms = :now WHERE idempotency_key = :key AND owner_user_id = :userId " +
            "AND owner_tenant_key = :tenantKey AND state IN ('PENDING','RETRY_WAIT')",
    )
    suspend fun claim(key: String, userId: String, tenantKey: String, now: Long): Int

    @Query("SELECT * FROM mutation_outbox WHERE idempotency_key = :key")
    suspend fun get(key: String): OutboxEntity?

    @Query(
        "UPDATE mutation_outbox SET state = 'RETRY_WAIT', next_attempt_at_epoch_ms = :next, " +
            "updated_at_epoch_ms = :now, last_error_kind = :error WHERE idempotency_key = :key",
    )
    suspend fun retry(key: String, next: Long, now: Long, error: String)

    @Query(
        "UPDATE mutation_outbox SET state = 'PERMANENT_FAILURE', updated_at_epoch_ms = :now, " +
            "last_error_kind = :error WHERE idempotency_key = :key",
    )
    suspend fun failPermanently(key: String, now: Long, error: String)

    @Query("DELETE FROM mutation_outbox WHERE idempotency_key = :key")
    suspend fun delete(key: String)

    @Query(
        "UPDATE mutation_outbox SET state = 'RETRY_WAIT', next_attempt_at_epoch_ms = :now, " +
            "updated_at_epoch_ms = :now WHERE state = 'IN_FLIGHT' AND updated_at_epoch_ms <= :staleBefore",
    )
    suspend fun recoverStale(now: Long, staleBefore: Long)

    @Query("DELETE FROM mutation_outbox")
    suspend fun clearAll()

    @Transaction
    suspend fun claimNext(owner: CacheOwner, now: Long): OutboxEntity? {
        val candidate = nextReady(owner.userId, owner.tenantKey, now) ?: return null
        if (claim(candidate.idempotencyKey, owner.userId, owner.tenantKey, now) != 1) return null
        return get(candidate.idempotencyKey)
    }
}

@Dao
interface AccountDirectoryDao {
    @Query(
        "SELECT * FROM cached_managed_accounts WHERE owner_user_id = :userId " +
            "AND owner_tenant_key = :tenantKey ORDER BY display_name COLLATE NOCASE, target_user_id",
    )
    fun observeAccounts(userId: String, tenantKey: String): Flow<List<CachedManagedAccountEntity>>

    @Query(
        "SELECT * FROM cached_managed_shops WHERE owner_user_id = :userId " +
            "AND owner_tenant_key = :tenantKey ORDER BY display_name COLLATE NOCASE, shop_id",
    )
    fun observeShops(userId: String, tenantKey: String): Flow<List<CachedManagedShopEntity>>

    @Query(
        "SELECT * FROM cached_managed_accounts WHERE owner_user_id = :userId " +
            "AND owner_tenant_key = :tenantKey AND target_user_id = :targetUserId LIMIT 1",
    )
    suspend fun getAccount(userId: String, tenantKey: String, targetUserId: String): CachedManagedAccountEntity?

    @Query(
        "SELECT * FROM cached_managed_shops WHERE owner_user_id = :userId " +
            "AND owner_tenant_key = :tenantKey AND shop_id = :shopId LIMIT 1",
    )
    suspend fun getShop(userId: String, tenantKey: String, shopId: String): CachedManagedShopEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAccounts(values: List<CachedManagedAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putShops(values: List<CachedManagedShopEntity>)

    @Query("DELETE FROM cached_managed_accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM cached_managed_shops")
    suspend fun clearShops()
}
