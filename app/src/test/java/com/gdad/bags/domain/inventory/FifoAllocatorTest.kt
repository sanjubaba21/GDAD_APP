package com.gdad.bags.domain.inventory

import com.gdad.bags.domain.model.InventoryLot
import com.gdad.bags.domain.model.Money
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FifoAllocatorTest {
    private val oldLot = InventoryLot(
        id = "old", shopId = "shop", skuId = "sku", sourceId = "bill-1",
        receivedAt = Instant.parse("2026-01-01T00:00:00Z"),
        unitCost = Money(100_00), originalQuantity = 5, remainingQuantity = 5,
    )
    private val newLot = InventoryLot(
        id = "new", shopId = "shop", skuId = "sku", sourceId = "bill-2",
        receivedAt = Instant.parse("2026-02-01T00:00:00Z"),
        unitCost = Money(120_00), originalQuantity = 5, remainingQuantity = 5,
    )

    @Test
    fun oldestStockIsConsumedFirst() {
        val result = FifoAllocator.allocate(listOf(newLot, oldLot), 7)
        assertEquals(listOf("old", "new"), result.allocations.map { it.lotId })
        assertEquals(listOf(5, 2), result.allocations.map { it.quantity })
        assertEquals(0, result.shortage)
        assertEquals(0, result.updatedLots.first { it.id == "old" }.remainingQuantity)
        assertEquals(3, result.updatedLots.first { it.id == "new" }.remainingQuantity)
    }

    @Test
    fun negativeStockSaleReportsShortageWithoutInventingCost() {
        val result = FifoAllocator.allocate(listOf(oldLot), 8)
        assertEquals(3, result.shortage)
        assertEquals(5, result.allocations.single().quantity)
    }

    @Test
    fun productReturnRestoresItsOriginalAllocation() {
        val sale = FifoAllocator.allocate(listOf(oldLot, newLot), 7)
        val restored = FifoAllocator.restore(sale.updatedLots, sale.allocations, 2)
        assertEquals(0, restored.first { it.id == "old" }.remainingQuantity)
        assertEquals(5, restored.first { it.id == "new" }.remainingQuantity)
        assertTrue(restored.all { it.remainingQuantity <= it.originalQuantity })
    }
}
