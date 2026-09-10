package com.gdad.bags.desktop

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.product.ProductRemoteDataSource
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.product.ProductResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DesktopProductCatalogRepository(
    private val remote: ProductRemoteDataSource,
) : ProductCatalogRepository {
    private val products = MutableStateFlow<List<CatalogProduct>>(emptyList())

    override fun observe(session: UserSession): Flow<List<CatalogProduct>> = products.asStateFlow()

    override suspend fun refresh(session: UserSession): ProductResult {
        if (session.role == UserRole.SUPER_ADMIN || !session.shopId.isUuid()) return denied()
        return when (val result = remote.load(session.owner(), session.role == UserRole.OWNER)) {
            is RemoteResult.Failure -> result.error.failure("Unable to refresh products.")
            is RemoteResult.Success -> {
                val stock = result.value.stock.associateBy { it.productId }
                products.value = result.value.products.map { product ->
                    val summary = stock[product.id]
                    CatalogProduct(
                        id = product.id,
                        name = product.name,
                        sku = product.sku,
                        barcode = product.barcode,
                        sellingPricePaisa = product.sellingPricePaisa,
                        lowStockThreshold = product.lowStockThreshold,
                        quantityOnHand = summary?.quantityOnHand ?: 0,
                        stockValuePaisa = summary?.stockValuePaisa,
                        active = product.active,
                    )
                }
                ProductResult.Success("Products refreshed.")
            }
        }
    }

    override suspend fun mutate(
        session: UserSession,
        requestId: String,
        mutation: ProductMutation,
        draft: ProductDraft,
    ): ProductResult {
        if (session.role != UserRole.OWNER || !session.shopId.isUuid()) return denied()
        if (!requestId.isUuid() || !draft.validFor(mutation)) return invalid()
        return when (val result = remote.mutate(session.owner(), requestId, mutation, draft)) {
            is RemoteResult.Failure -> result.error.failure("Unable to update the product.")
            is RemoteResult.Success -> when (val refreshed = refresh(session)) {
                is ProductResult.Failure -> refreshed
                is ProductResult.Success -> ProductResult.Success(
                    when (mutation) {
                        ProductMutation.CREATE -> "Product created and audited."
                        ProductMutation.UPDATE -> "Product updated and audited."
                        ProductMutation.ARCHIVE -> "Product archived; history remains available."
                    },
                )
            }
        }
    }

    fun snapshot(): List<CatalogProduct> = products.value

    fun clear() {
        products.value = emptyList()
    }

    private fun ProductDraft.validFor(mutation: ProductMutation): Boolean = when (mutation) {
        ProductMutation.ARCHIVE -> productId.isUuid()
        ProductMutation.CREATE, ProductMutation.UPDATE ->
            (mutation == ProductMutation.CREATE || productId.isUuid()) &&
                name.trim().length in 1..160 && sku.trim().length in 1..64 &&
                (barcode == null || barcode.trim().length in 3..64) &&
                sellingPricePaisa >= 0 && lowStockThreshold >= 0
    }

    private fun RemoteFailure.failure(default: String) = ProductResult.Failure(
        this,
        when (kind) {
            RemoteErrorKind.UNAUTHORIZED -> "You are not allowed to change products."
            RemoteErrorKind.VALIDATION -> "Review the product fields and reserved codes."
            RemoteErrorKind.CONFLICT -> "The product changed or is used by an in-progress operation."
            RemoteErrorKind.OFFLINE -> "Connect to the internet and try again."
            RemoteErrorKind.TIMEOUT -> "The request timed out. Retry the same change safely."
            RemoteErrorKind.RATE_LIMITED -> "Too many attempts. Wait before retrying."
            RemoteErrorKind.UNKNOWN -> default
        },
    )

    private fun denied() = ProductResult.Failure(null, "You are not allowed to change products.")
    private fun invalid() = ProductResult.Failure(null, "Review the product fields and reserved codes.")
    private fun String?.isUuid(): Boolean = this != null && runCatching { UUID.fromString(this) }.isSuccess
    private fun UserSession.owner() = CacheOwner(userId, shopId)
}
