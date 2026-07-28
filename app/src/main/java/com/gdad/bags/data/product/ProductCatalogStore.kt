package com.gdad.bags.data.product

import androidx.room.withTransaction
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedProductEntity
import com.gdad.bags.data.local.CachedStockSummaryEntity
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.domain.product.CatalogProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ProductCatalogStore(private val database: RoomCacheDatabase) {
    fun observe(owner: CacheOwner): Flow<List<CatalogProduct>> = combine(
        database.readDao().observeProducts(owner.userId, owner.tenantKey),
        database.readDao().observeStock(owner.userId, owner.tenantKey),
    ) { products, stock ->
        val byProduct = stock.associateBy { it.productId }
        products.map { product ->
            val summary = byProduct[product.id]
            CatalogProduct(
                product.id, product.name, product.sku, product.barcode,
                product.sellingPricePaisa, product.lowStockThreshold,
                summary?.quantityOnHand ?: 0, summary?.stockValuePaisa, product.active,
            )
        }
    }

    suspend fun replace(
        owner: CacheOwner,
        products: List<CachedProductEntity>,
        stock: List<CachedStockSummaryEntity>,
    ) = database.withTransaction {
        val identity = database.identityDao().get()
        require(identity?.userId == owner.userId && identity.tenantKey == owner.tenantKey)
        require((products + stock).all { it.ownerUserId == owner.userId && it.ownerTenantKey == owner.tenantKey })
        database.writeDao().clearStock()
        database.writeDao().clearProducts()
        database.writeDao().putProducts(products)
        database.writeDao().putStock(stock)
    }
}
