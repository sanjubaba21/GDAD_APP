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
