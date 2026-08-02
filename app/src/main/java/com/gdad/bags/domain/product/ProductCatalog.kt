package com.gdad.bags.domain.product

import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

data class CatalogProduct(
    val id: String,
    val name: String,
    val sku: String,
    val barcode: String?,
    val sellingPricePaisa: Long,
    val lowStockThreshold: Int,
    val quantityOnHand: Long,
    val stockValuePaisa: Long?,
    val active: Boolean,
)

data class ProductDraft(
    val productId: String? = null,
    val name: String,
    val sku: String,
    val barcode: String?,
    val sellingPricePaisa: Long,
    val lowStockThreshold: Int,
)

enum class ProductMutation { CREATE, UPDATE, ARCHIVE }

sealed interface ProductResult {
    data class Success(val safeMessage: String) : ProductResult
    data class Failure(val error: RemoteFailure?, val safeMessage: String) : ProductResult
}

interface ProductCatalogRepository {
    fun observe(session: UserSession): Flow<List<CatalogProduct>>
    suspend fun refresh(session: UserSession): ProductResult
    suspend fun mutate(
        session: UserSession,
        requestId: String,
        mutation: ProductMutation,
        draft: ProductDraft,
    ): ProductResult
}
