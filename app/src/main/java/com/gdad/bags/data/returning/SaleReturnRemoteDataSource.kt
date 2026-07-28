package com.gdad.bags.data.returning

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteOperation
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.returning.PostedSaleReturn
import com.gdad.bags.domain.returning.SaleAllocation
import com.gdad.bags.domain.returning.SaleHistory
import com.gdad.bags.domain.returning.SaleHistoryEntry
import com.gdad.bags.domain.returning.SaleHistoryLine
import com.gdad.bags.domain.returning.SaleReturnDraft
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface SaleReturnRemoteDataSource {
    suspend fun load(owner: CacheOwner, includeCost: Boolean): RemoteResult<SaleHistory>

    suspend fun post(
        owner: CacheOwner,
        requestId: String,
        draft: SaleReturnDraft,
    ): RemoteResult<PostedSaleReturn>
}

class SupabaseSaleReturnRemoteDataSource(
    private val client: SupabaseClient,
    private val calls: RemoteCallExecutor,
) : SaleReturnRemoteDataSource {
    override suspend fun load(
        owner: CacheOwner,
        includeCost: Boolean,
    ): RemoteResult<SaleHistory> = calls.execute(RemoteOperation.LOAD_SALE_HISTORY, true) {
        val sales = client.from("sales")
            .select(
                Columns.raw(
                    "id,status,is_credit,customer_name,customer_contact," +
                        "business_date,occurred_at,grand_total_paisa",
                ),
            ).decodeList<SaleRow>()
        val lines = client.from("sale_lines")
            .select(
                Columns.raw(
                    "id,sale_id,product_id,product_name,sku_code,quantity," +
                        "effective_unit_price_paisa,line_total_paisa",
                ),
            ).decodeList<LineRow>()
        val payments = client.from("sale_payments")
            .select(Columns.raw("sale_id,status,amount_paisa"))
            .decodeList<PaymentRow>()
        val returns = client.from("sale_returns")
            .select(Columns.raw("id,sale_id,status,total_value_paisa"))
            .decodeList<ReturnRow>()
        val returnedLines = client.from("sale_return_lines")
            .select(Columns.raw("sale_return_id,sale_line_id,quantity,refund_value_paisa"))
            .decodeList<ReturnedLineRow>()
        val refunds = client.from("refunds")
            .select(Columns.raw("sale_return_id,status,amount_paisa"))
            .decodeList<RefundRow>()
        val allocations = if (includeCost) {
            client.from("sale_lot_allocations")
                .select(Columns.raw("sale_line_id,lot_id,quantity,unit_cost_paisa"))
                .decodeList<AllocationRow>()
        } else {
            emptyList()
        }
        val returnById = returns.associateBy { it.id }

        SaleHistory(
            sales = sales.map { sale ->
                val postedReturns = returns.filter {
                    it.saleId == sale.id && it.status != "reversed"
                }
                val returned = postedReturns.sumOf { it.total }
                val paid = payments.filter {
                    it.saleId == sale.id && it.status == "posted"
                }.sumOf { it.amount }
                val refund = refunds.filter {
                    it.status == "posted" && returnById[it.returnId]?.saleId == sale.id
                }.sumOf { it.amount }

                SaleHistoryEntry(
                    id = sale.id,
                    status = sale.status,
                    isCredit = sale.credit,
                    customerName = sale.customerName,
                    customerContact = sale.customerContact,
                    businessDate = sale.date,
                    occurredAt = sale.occurred,
                    grandTotalPaisa = sale.total,
                    paidPaisa = paid,
                    returnedPaisa = returned,
                    refundedPaisa = refund,
                    duePaisa = (
                        sale.total - returned - (paid - refund)
                    ).coerceAtLeast(0),
                    lines = lines.filter { it.saleId == sale.id }.map { line ->
                        val prior = returnedLines.filter {
                            returnById[it.returnId]?.status != "reversed" &&
                                it.lineId == line.id
                        }
                        SaleHistoryLine(
                            id = line.id,
                            productId = line.productId,
                            productName = line.name,
                            sku = line.sku,
                            quantity = line.quantity,
                            unitPricePaisa = line.price,
                            lineTotalPaisa = line.total,
                            returnedQuantity = prior.sumOf { it.quantity },
                            returnedValuePaisa = prior.sumOf { it.value },
                            allocations = allocations.filter { it.lineId == line.id }.map {
                                SaleAllocation(it.lotId, it.quantity, it.cost)
                            },
                        )
                    },
                )
            }.sortedByDescending { it.occurredAt },
        )
    }

    override suspend fun post(
        owner: CacheOwner,
        requestId: String,
        draft: SaleReturnDraft,
    ): RemoteResult<PostedSaleReturn> = calls.execute(RemoteOperation.POST_SALE_RETURN, true) {
        client.postgrest.rpc(
            function = "post_sale_return",
            parameters = JsonObject(
                mapOf(
                    "p_idempotency_key" to JsonPrimitive(requestId),
                    "p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),
                    "p_sale_id" to JsonPrimitive(draft.saleId),
                    "p_business_date" to JsonPrimitive(draft.businessDate),
                    "p_reason" to JsonPrimitive(draft.reason),
                    "p_lines" to JsonArray(
                        draft.lines.map {
                            JsonObject(
                                mapOf(
                                    "sale_line_id" to JsonPrimitive(it.saleLineId),
                                    "quantity" to JsonPrimitive(it.quantity),
                                    "disposition" to JsonPrimitive(
                                        it.disposition.name.lowercase(),
                                    ),
                                ),
                            )
                        },
                    ),
                    "p_refund_method" to (
                        draft.refundMethod?.name?.lowercase()?.let(::JsonPrimitive) ?: JsonNull
                    ),
                ),
            ),
        ).decodeAs<PostedRow>().domain()
    }
}

@Serializable
private data class SaleRow(
    val id: String,
    val status: String,
    @SerialName("is_credit") val credit: Boolean,
    @SerialName("customer_name") val customerName: String?,
    @SerialName("customer_contact") val customerContact: String?,
    @SerialName("business_date") val date: String,
    @SerialName("occurred_at") val occurred: String,
    @SerialName("grand_total_paisa") val total: Long,
)

@Serializable
private data class LineRow(
    val id: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val name: String,
    @SerialName("sku_code") val sku: String,
    val quantity: Int,
    @SerialName("effective_unit_price_paisa") val price: Long,
    @SerialName("line_total_paisa") val total: Long,
)

@Serializable
private data class PaymentRow(
    @SerialName("sale_id") val saleId: String,
    val status: String,
    @SerialName("amount_paisa") val amount: Long,
)

@Serializable
private data class ReturnRow(
    val id: String,
    @SerialName("sale_id") val saleId: String,
    val status: String,
    @SerialName("total_value_paisa") val total: Long,
)

@Serializable
private data class ReturnedLineRow(
    @SerialName("sale_return_id") val returnId: String,
    @SerialName("sale_line_id") val lineId: String,
    val quantity: Int,
    @SerialName("refund_value_paisa") val value: Long,
)

@Serializable
private data class RefundRow(
    @SerialName("sale_return_id") val returnId: String,
    val status: String,
    @SerialName("amount_paisa") val amount: Long,
)

@Serializable
private data class AllocationRow(
    @SerialName("sale_line_id") val lineId: String,
    @SerialName("lot_id") val lotId: String,
    val quantity: Int,
    @SerialName("unit_cost_paisa") val cost: Long,
)

@Serializable
private data class PostedRow(
    @SerialName("sale_return_id") val id: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("return_value_paisa") val value: Long,
    @SerialName("refund_paisa") val refund: Long,
    @SerialName("due_after_paisa") val due: Long,
    @SerialName("restored_quantity") val restored: Int,
    @SerialName("restored_cost_paisa") val cost: Long,
    @SerialName("sale_status") val status: String,
) {
    fun domain() = PostedSaleReturn(
        returnId = id,
        saleId = saleId,
        returnValuePaisa = value,
        refundPaisa = refund,
        dueAfterPaisa = due,
        restoredQuantity = restored,
        restoredCostPaisa = cost,
        saleStatus = status,
    )
}
