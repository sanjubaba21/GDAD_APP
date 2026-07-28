package com.gdad.bags.data.product

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedProductEntity
import com.gdad.bags.data.local.CachedStockSummaryEntity
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteOperation
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class ProductRemoteSnapshot(
    val products: List<CachedProductEntity>,
    val stock: List<CachedStockSummaryEntity>,
)

interface ProductRemoteDataSource {
    suspend fun load(owner: CacheOwner, canSeeCost: Boolean): RemoteResult<ProductRemoteSnapshot>
    suspend fun mutate(owner: CacheOwner, requestId: String, mutation: ProductMutation, draft: ProductDraft): RemoteResult<Unit>
}

class SupabaseProductRemoteDataSource(
    private val client: SupabaseClient,
    private val calls: RemoteCallExecutor,
) : ProductRemoteDataSource {
    override suspend fun load(owner: CacheOwner, canSeeCost: Boolean) = calls.execute(
        RemoteOperation.LOAD_PRODUCTS, true,
    ) {
        val products = client.from("products").select(
            Columns.raw("id,sku_code,barcode,name,low_stock_threshold,default_selling_price_paisa,current_stock,active,updated_at"),
        ).decodeList<ProductRow>()
        val lots = if (canSeeCost) client.from("inventory_lots").select(
            Columns.raw("product_id,unit_cost_paisa,remaining_quantity"),
        ).decodeList<LotRow>() else emptyList()
        val value = lots.groupBy { it.productId }.mapValues { (_, rows) ->
            rows.sumOf { it.unitCostPaisa * it.remainingQuantity }
        }
        val now = System.currentTimeMillis()
        ProductRemoteSnapshot(
            products.map { row ->
                CachedProductEntity(
                    owner.userId, owner.tenantKey, row.id, row.name, row.sku, row.barcode,
                    row.sellingPricePaisa, row.lowStockThreshold, row.active,
                    Instant.parse(row.updatedAt).toEpochMilliseconds(),
                )
            },
            products.map { row ->
                CachedStockSummaryEntity(
                    owner.userId, owner.tenantKey, row.id, row.currentStock,
                    value[row.id], row.currentStock <= row.lowStockThreshold, now,
                )
            },
        )
    }

    override suspend fun mutate(owner: CacheOwner, requestId: String, mutation: ProductMutation, draft: ProductDraft) =
        calls.execute(RemoteOperation.MANAGE_PRODUCT, true) {
            client.postgrest.rpc(
                "manage_product",
                JsonObject(
                    mapOf(
                        "p_idempotency_key" to JsonPrimitive(requestId),
                        "p_action" to JsonPrimitive(mutation.name.lowercase()),
                        "p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),
                        "p_product_id" to (draft.productId?.let(::JsonPrimitive) ?: JsonNull),
                        "p_sku_code" to JsonPrimitive(draft.sku),
                        "p_barcode" to (draft.barcode?.let(::JsonPrimitive) ?: JsonNull),
                        "p_name" to JsonPrimitive(draft.name),
                        "p_low_stock_threshold" to JsonPrimitive(draft.lowStockThreshold),
                        "p_default_selling_price_paisa" to JsonPrimitive(draft.sellingPricePaisa),
                    ),
                ),
            )
            Unit
        }
}

@Serializable private data class ProductRow(
    val id: String,
    @SerialName("sku_code") val sku: String,
    val barcode: String?,
    val name: String,
    @SerialName("low_stock_threshold") val lowStockThreshold: Int,
    @SerialName("default_selling_price_paisa") val sellingPricePaisa: Long,
    @SerialName("current_stock") val currentStock: Long,
    val active: Boolean,
    @SerialName("updated_at") val updatedAt: String,
)
@Serializable private data class LotRow(
    @SerialName("product_id") val productId: String,
    @SerialName("unit_cost_paisa") val unitCostPaisa: Long,
    @SerialName("remaining_quantity") val remainingQuantity: Long,
)
