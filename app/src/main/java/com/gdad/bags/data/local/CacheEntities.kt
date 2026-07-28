package com.gdad.bags.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

data class CacheOwner(val userId: String, val shopId: String?) {
    init {
        require(userId.isNotBlank())
        require(shopId == null || shopId.isNotBlank())
    }

    val tenantKey: String get() = shopId ?: PLATFORM_TENANT_KEY

    companion object {
        const val PLATFORM_TENANT_KEY = "__platform__"
    }
}

interface OwnedCacheRow {
    val ownerUserId: String
    val ownerTenantKey: String
}

@Entity(tableName = "cache_identity", primaryKeys = ["slot"])
data class CacheIdentityEntity(
    val slot: Int = SINGLETON_SLOT,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "shop_id") val shopId: String?,
    @ColumnInfo(name = "tenant_key") val tenantKey: String,
    @ColumnInfo(name = "activated_at_epoch_ms") val activatedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_SLOT = 1
    }
}

@Entity(
    tableName = "cached_profiles",
    primaryKeys = ["owner_user_id", "owner_tenant_key"],
)
data class CachedProfileEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val role: String,
    val disabled: Boolean,
    @ColumnInfo(name = "refreshed_at_epoch_ms") val refreshedAtEpochMillis: Long,
) : OwnedCacheRow

@Entity(
    tableName = "cached_memberships",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "shop_id"],
    indices = [Index(value = ["owner_user_id", "owner_tenant_key"])],
)
data class CachedMembershipEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    @ColumnInfo(name = "shop_id") val shopId: String,
    val role: String,
    val active: Boolean,
) : OwnedCacheRow

@Entity(
    tableName = "cached_products",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "id"],
    indices = [
        Index(value = ["owner_user_id", "owner_tenant_key", "name"]),
        Index(value = ["owner_user_id", "owner_tenant_key", "sku"]),
    ],
)
data class CachedProductEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    val id: String,
    val name: String,
    val sku: String,
    val barcode: String?,
    @ColumnInfo(name = "selling_price_paisa") val sellingPricePaisa: Long,
    @ColumnInfo(name = "low_stock_threshold") val lowStockThreshold: Int,
    val active: Boolean,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMillis: Long,
) : OwnedCacheRow

@Entity(
    tableName = "cached_stock_summaries",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "product_id"],
    indices = [Index(value = ["owner_user_id", "owner_tenant_key", "is_low_stock"])],
)
data class CachedStockSummaryEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "quantity_on_hand") val quantityOnHand: Long,
    @ColumnInfo(name = "stock_value_paisa") val stockValuePaisa: Long?,
    @ColumnInfo(name = "is_low_stock") val isLowStock: Boolean,
    @ColumnInfo(name = "refreshed_at_epoch_ms") val refreshedAtEpochMillis: Long,
) : OwnedCacheRow

@Entity(
    tableName = "cached_vendors",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "id"],
    indices = [Index(value = ["owner_user_id", "owner_tenant_key", "name"])],
)
data class CachedVendorEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    val id: String,
    val name: String,
    val phone: String?,
    @ColumnInfo(name = "tax_reference") val taxReference: String?,
    val notes: String?,
    @ColumnInfo(name = "due_paisa") val duePaisa: Long,
    val active: Boolean,
) : OwnedCacheRow

@Entity(
    tableName = "cached_recent_sales",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "id"],
    indices = [Index(value = ["owner_user_id", "owner_tenant_key", "sold_at_epoch_ms"])],
)
data class CachedRecentSaleEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    val id: String,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String,
    @ColumnInfo(name = "customer_name") val customerName: String?,
    @ColumnInfo(name = "total_paisa") val totalPaisa: Long,
    @ColumnInfo(name = "paid_paisa") val paidPaisa: Long,
    @ColumnInfo(name = "due_paisa") val duePaisa: Long,
    val status: String,
    @ColumnInfo(name = "sold_at_epoch_ms") val soldAtEpochMillis: Long,
) : OwnedCacheRow

@Entity(
    tableName = "cached_accounts",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "id"],
    indices = [Index(value = ["owner_user_id", "owner_tenant_key", "code"], unique = true)],
)
data class CachedAccountEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    val id: String,
    val code: String,
    val name: String,
    val type: String,
    @ColumnInfo(name = "balance_paisa") val balancePaisa: Long,
    val active: Boolean,
) : OwnedCacheRow

@Entity(
    tableName = "cached_dashboard_summaries",
    primaryKeys = ["owner_user_id", "owner_tenant_key"],
)
data class CachedDashboardSummaryEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    @ColumnInfo(name = "sales_paisa") val salesPaisa: Long,
    @ColumnInfo(name = "profit_paisa") val profitPaisa: Long?,
    @ColumnInfo(name = "receivables_paisa") val receivablesPaisa: Long,
    @ColumnInfo(name = "vendor_due_paisa") val vendorDuePaisa: Long?,
    @ColumnInfo(name = "cash_bank_paisa") val cashBankPaisa: Long?,
    @ColumnInfo(name = "low_stock_count") val lowStockCount: Int,
    @ColumnInfo(name = "generated_at_epoch_ms") val generatedAtEpochMillis: Long,
) : OwnedCacheRow

@Entity(
    tableName = "cached_notifications",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "id"],
    indices = [
        Index(value = ["owner_user_id", "owner_tenant_key", "created_at_epoch_ms"]),
        Index(value = ["owner_user_id", "owner_tenant_key", "is_read"]),
    ],
)
data class CachedNotificationEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    val id: String,
    val category: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "expires_at_epoch_ms") val expiresAtEpochMillis: Long,
) : OwnedCacheRow
