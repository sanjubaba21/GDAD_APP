package com.gdad.bags.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class UserRole { SUPER_ADMIN, OWNER, SALESMAN }

data class UserSession(
    val userId: String,
    val displayName: String,
    val role: UserRole,
    val shopId: String?,
    val authenticatedAt: Instant = Instant.now(),
)

@JvmInline
value class Money(val paisa: Long) {
    init { require(paisa >= 0) { "Money cannot be negative" } }
    operator fun plus(other: Money) = Money(Math.addExact(paisa, other.paisa))
    operator fun times(quantity: Int) = Money(Math.multiplyExact(paisa, quantity.toLong()))
}

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val shopId: String,
    val skuCode: String,
    val name: String,
    val currentStock: Int,
    val lowStockThreshold: Int,
    val defaultSellingPrice: Money,
)

data class InventoryLot(
    val id: String = UUID.randomUUID().toString(),
    val shopId: String,
    val skuId: String,
    val sourceId: String,
    val receivedAt: Instant,
    val unitCost: Money,
    val originalQuantity: Int,
    val remainingQuantity: Int,
) {
    init {
        require(originalQuantity > 0)
        require(remainingQuantity in 0..originalQuantity)
    }
}

data class LotAllocation(val lotId: String, val quantity: Int, val unitCost: Money) {
    val totalCost: Money get() = unitCost * quantity
}

data class SaleLine(
    val skuId: String,
    val quantity: Int,
    val sellingPrice: Money,
    val costAllocations: List<LotAllocation>,
)

data class ProductReturn(
    val id: String = UUID.randomUUID().toString(),
    val shopId: String,
    val originalSaleId: String,
    val skuId: String,
    val quantity: Int,
    val refundAmount: Money,
    val returnedOn: LocalDate,
    val processedBy: String,
)
