package com.gdad.bags.data.local

data class CachedProductEntity(
    val ownerUserId: String,
    val ownerTenantKey: String,
    val id: String,
    val name: String,
    val sku: String,
    val barcode: String?,
    val sellingPricePaisa: Long,
    val lowStockThreshold: Int,
    val active: Boolean,
    val updatedAtEpochMillis: Long,
    val minimumSellingPricePaisa: Long = 0,
)

data class CachedStockSummaryEntity(
    val ownerUserId: String,
    val ownerTenantKey: String,
    val productId: String,
    val quantityOnHand: Long,
    val stockValuePaisa: Long?,
    val isLowStock: Boolean,
    val refreshedAtEpochMillis: Long,
)

data class CachedVendorEntity(
    val ownerUserId: String,
    val ownerTenantKey: String,
    val id: String,
    val name: String,
    val phone: String?,
    val taxReference: String?,
    val notes: String?,
    val duePaisa: Long,
    val active: Boolean,
)

data class CachedAccountEntity(
    val ownerUserId: String,
    val ownerTenantKey: String,
    val id: String,
    val code: String,
    val name: String,
    val type: String,
    val balancePaisa: Long,
    val active: Boolean,
)
