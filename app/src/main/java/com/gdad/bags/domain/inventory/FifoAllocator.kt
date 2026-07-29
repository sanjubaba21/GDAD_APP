package com.gdad.bags.domain.inventory

import com.gdad.bags.domain.model.InventoryLot
import com.gdad.bags.domain.model.LotAllocation

data class FifoResult(
    val allocations: List<LotAllocation>,
    val updatedLots: List<InventoryLot>,
    val shortage: Int,
)

/** Allocates oldest stock first. A non-zero shortage must notify the Owner. */
object FifoAllocator {
    fun allocate(lots: List<InventoryLot>, requestedQuantity: Int): FifoResult {
        require(requestedQuantity > 0) { "Requested quantity must be positive" }
        require(lots.map { it.shopId }.distinct().size <= 1) { "FIFO lots must belong to one shop" }
        require(lots.map { it.skuId }.distinct().size <= 1) { "FIFO lots must belong to one product" }
        var remaining = requestedQuantity
        val allocations = mutableListOf<LotAllocation>()
        val sorted = lots.sortedWith(compareBy<InventoryLot> { it.receivedAt }.thenBy { it.id })
        val updated = sorted.map { lot ->
            if (remaining == 0 || lot.remainingQuantity == 0) return@map lot
            val taken = minOf(remaining, lot.remainingQuantity)
            remaining -= taken
            allocations += LotAllocation(lot.id, taken, lot.unitCost)
            lot.copy(remainingQuantity = lot.remainingQuantity - taken)
        }
        return FifoResult(allocations, updated, remaining)
    }

    fun restore(
        lots: List<InventoryLot>,
        originalAllocations: List<LotAllocation>,
        returnQuantity: Int,
    ): List<InventoryLot> {
        require(returnQuantity > 0) { "Return quantity must be positive" }
        require(originalAllocations.isNotEmpty()) { "Original allocations are required" }
        require(originalAllocations.all { it.quantity > 0 }) { "Allocation quantities must be positive" }
        require(originalAllocations.distinctBy { it.lotId }.size == originalAllocations.size) {
            "Each original lot may appear only once"
        }
        val lotsById = lots.associateBy { it.id }
        require(lotsById.size == lots.size) { "FIFO lot identifiers must be unique" }
        require(originalAllocations.all { allocation ->
            lotsById[allocation.lotId]?.let { lot ->
                allocation.unitCost == lot.unitCost && allocation.quantity <= lot.originalQuantity
            } == true
        }) { "Original allocations must match available FIFO lots" }
        val allocatedQuantity = originalAllocations.fold(0) { total, allocation ->
            Math.addExact(total, allocation.quantity)
        }
        require(returnQuantity <= allocatedQuantity) {
            "Cannot return more units than were originally allocated"
        }
        var remaining = returnQuantity
        val restoreByLot = mutableMapOf<String, Int>()
        for (allocation in originalAllocations.asReversed()) {
            if (remaining == 0) break
            val restored = minOf(remaining, allocation.quantity)
            restoreByLot[allocation.lotId] = restored
            remaining -= restored
        }
        return lots.map { lot ->
            val restored = restoreByLot[lot.id] ?: 0
            require(lot.remainingQuantity + restored <= lot.originalQuantity) {
                "Restoring return would exceed original lot quantity"
            }
            lot.copy(remainingQuantity = lot.remainingQuantity + restored)
        }
    }
}
