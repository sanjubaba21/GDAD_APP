package com.gdad.bags.data.product

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.EnqueueResult
import com.gdad.bags.data.local.MutationOutbox
import com.gdad.bags.data.local.OutboxOperation
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ProductionProductCatalogRepository(
    private val remote: ProductRemoteDataSource,
    private val store: ProductCatalogStore,
    private val outbox: MutationOutbox,
) : ProductCatalogRepository {
    override fun observe(session: UserSession): Flow<List<CatalogProduct>> = store.observe(session.owner())

    override suspend fun refresh(session: UserSession): ProductResult {
        if (session.role == UserRole.SUPER_ADMIN || !session.shopId.isUuid()) return denied()
        return when (val result = remote.load(session.owner(), session.role == UserRole.OWNER)) {
            is RemoteResult.Failure -> result.error.failure("Unable to refresh products.")
            is RemoteResult.Success -> {
                store.replace(session.owner(), result.value.products, result.value.stock)
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
            is RemoteResult.Success -> afterMutation(session, mutation)
            is RemoteResult.Failure -> {
                if (result.error.kind in setOf(RemoteErrorKind.OFFLINE, RemoteErrorKind.TIMEOUT, RemoteErrorKind.RATE_LIMITED)) {
                    when (outbox.enqueue(session.owner(), OutboxOperation.MANAGE_PRODUCT, draft.payload(mutation), requestId)) {
                        is EnqueueResult.Queued, is EnqueueResult.AlreadyQueued ->
                            ProductResult.Success("Product change saved offline and will retry automatically.")
                        is EnqueueResult.Rejected -> invalid()
                    }
                } else result.error.failure("Unable to update the product.")
            }
        }
    }

    private suspend fun afterMutation(session: UserSession, mutation: ProductMutation): ProductResult =
        when (val refreshed = refresh(session)) {
            is ProductResult.Failure -> refreshed
            is ProductResult.Success -> ProductResult.Success(
                when (mutation) {
                    ProductMutation.CREATE -> "Product created and audited."
                    ProductMutation.UPDATE -> "Product updated and audited."
                    ProductMutation.ARCHIVE -> "Product archived; history remains available."
                },
            )
        }

    private fun ProductDraft.validFor(mutation: ProductMutation): Boolean = when (mutation) {
        ProductMutation.ARCHIVE -> productId.isUuid()
        ProductMutation.CREATE, ProductMutation.UPDATE ->
            (mutation == ProductMutation.CREATE || productId.isUuid()) &&
                name.trim().length in 1..160 && sku.trim().length in 1..64 &&
                (barcode == null || barcode.trim().length in 3..64) &&
                sellingPricePaisa >= 0 && lowStockThreshold >= 0
    }

    private fun ProductDraft.payload(mutation: ProductMutation): JsonObject = JsonObject(
        mapOf(
            "p_action" to JsonPrimitive(mutation.name.lowercase()),
            "p_product_id" to (productId?.let(::JsonPrimitive) ?: JsonNull),
            "p_sku_code" to JsonPrimitive(sku),
            "p_barcode" to (barcode?.let(::JsonPrimitive) ?: JsonNull),
            "p_name" to JsonPrimitive(name),
            "p_low_stock_threshold" to JsonPrimitive(lowStockThreshold),
            "p_default_selling_price_paisa" to JsonPrimitive(sellingPricePaisa),
        ),
    )

    private fun RemoteFailure.failure(default: String) = ProductResult.Failure(
        this,
        when (kind) {
            RemoteErrorKind.UNAUTHORIZED -> "You are not allowed to change products."
            RemoteErrorKind.VALIDATION -> "Review the product fields and reserved codes."
            RemoteErrorKind.CONFLICT -> "The product changed or is used by an in-progress operation."
            RemoteErrorKind.OFFLINE -> "Connect to the internet and try again."
            RemoteErrorKind.TIMEOUT -> "The request timed out. Try again."
            RemoteErrorKind.RATE_LIMITED -> "Too many attempts. Wait before retrying."
            RemoteErrorKind.UNKNOWN -> default
        },
    )
    private fun denied() = ProductResult.Failure(null, "You are not allowed to change products.")
    private fun invalid() = ProductResult.Failure(null, "Review the product fields and reserved codes.")
    private fun String?.isUuid(): Boolean = this != null && runCatching { UUID.fromString(this) }.isSuccess
    private fun UserSession.owner() = CacheOwner(userId, shopId)
}
