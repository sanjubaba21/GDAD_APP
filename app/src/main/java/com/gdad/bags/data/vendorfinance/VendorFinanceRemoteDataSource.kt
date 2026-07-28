package com.gdad.bags.data.vendorfinance

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteOperation
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.vendorfinance.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

interface VendorFinanceRemoteDataSource {
    suspend fun load(owner: CacheOwner): RemoteResult<VendorLedger>
    suspend fun postPayment(owner: CacheOwner, requestId: String, draft: VendorPaymentDraft): RemoteResult<PostedVendorPayment>
    suspend fun postReturn(owner: CacheOwner, requestId: String, draft: VendorReturnDraft): RemoteResult<PostedVendorReturn>
    suspend fun reverse(owner: CacheOwner, requestId: String, draft: VendorReversalDraft): RemoteResult<PostedVendorReversal>
}

class SupabaseVendorFinanceRemoteDataSource(
    private val client: SupabaseClient,
    private val calls: RemoteCallExecutor,
) : VendorFinanceRemoteDataSource {
    override suspend fun load(owner: CacheOwner) = calls.execute(RemoteOperation.LOAD_VENDOR_LEDGER, true) {
        val bills = client.from("purchase_bills").select(Columns.raw("id,vendor_id,status,invoice_reference,business_date,occurred_at,grand_total_paisa")).decodeList<BillRow>()
        val billLines = client.from("purchase_bill_lines").select(Columns.raw("id,product_name,sku_code")).decodeList<BillLineRow>().associateBy { it.id }
        val receiptLines = client.from("purchase_receipt_lines").select(Columns.raw("id,purchase_bill_id,purchase_bill_line_id,product_id,quantity,unit_cost_paisa")).decodeList<ReceiptLineRow>()
        val lots = client.from("inventory_lots").select(Columns.raw("purchase_receipt_line_id,remaining_quantity")).decodeList<LotRow>().associateBy { it.receiptLineId }
        val payments = client.from("vendor_payments").select(Columns.raw("id,vendor_id,status,method,amount_paisa,business_date,occurred_at,reversal_reason")).decodeList<PaymentRow>()
        val allocations = client.from("vendor_payment_allocations").select(Columns.raw("vendor_payment_id,purchase_bill_id,amount_paisa")).decodeList<PaymentAllocationRow>()
        val returns = client.from("vendor_returns").select(Columns.raw("id,vendor_id,purchase_bill_id,status,reason,total_value_paisa,business_date,occurred_at,reversal_reason")).decodeList<ReturnRow>()
        val returnLines = client.from("vendor_return_lines").select(Columns.raw("vendor_return_id,purchase_receipt_line_id,quantity")).decodeList<ReturnLineRow>()
        val paymentById = payments.associateBy { it.id }
        val returnById = returns.associateBy { it.id }

        VendorLedger(
            bills = bills.map { bill ->
                val paid = allocations.filter { it.billId == bill.id && paymentById[it.paymentId]?.status == "posted" }.sumOf { it.amount }
                val returned = returns.filter { it.billId == bill.id && it.status == "posted" }.sumOf { it.total }
                VendorBill(
                    bill.id, bill.vendorId, bill.status, bill.invoice, bill.date, bill.occurred, bill.total,
                    (bill.total - paid - returned).coerceAtLeast(0),
                    receiptLines.filter { it.billId == bill.id }.map { line ->
                        val detail = billLines[line.billLineId]
                        val prior = returnLines.filter { it.receiptLineId == line.id && returnById[it.returnId]?.status == "posted" }.sumOf { it.quantity }
                        VendorReceiptLine(line.id, line.productId, detail?.name ?: "Product", detail?.sku ?: "—", line.quantity, line.cost, prior, lots[line.id]?.remaining ?: 0)
                    },
                )
            }.sortedByDescending { it.occurredAt },
            payments = payments.map { payment ->
                VendorPaymentEvent(payment.id, payment.vendorId, payment.status, payment.method, payment.amount, payment.date, payment.occurred, allocations.filter { it.paymentId == payment.id }.associate { it.billId to it.amount }, payment.reversalReason)
            }.sortedByDescending { it.occurredAt },
            returns = returns.map { VendorReturnEvent(it.id, it.vendorId, it.billId, it.status, it.reason, it.total, it.date, it.occurred, it.reversalReason) }.sortedByDescending { it.occurredAt },
        )
    }

    override suspend fun postPayment(owner: CacheOwner, requestId: String, draft: VendorPaymentDraft) = calls.execute(RemoteOperation.POST_VENDOR_PAYMENT, true) {
        client.postgrest.rpc("post_vendor_payment", JsonObject(mapOf(
            "p_idempotency_key" to JsonPrimitive(requestId), "p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),
            "p_vendor_id" to JsonPrimitive(draft.vendorId), "p_method" to JsonPrimitive(draft.method.name.lowercase()),
            "p_business_date" to JsonPrimitive(draft.businessDate), "p_allocations" to JsonArray(draft.allocations.map { JsonObject(mapOf("purchase_bill_id" to JsonPrimitive(it.billId), "amount_paisa" to JsonPrimitive(it.amountPaisa))) }),
        ))).decodeAs<PaymentResultRow>().domain()
    }

    override suspend fun postReturn(owner: CacheOwner, requestId: String, draft: VendorReturnDraft) = calls.execute(RemoteOperation.POST_VENDOR_RETURN, true) {
        client.postgrest.rpc("post_vendor_return", JsonObject(mapOf(
            "p_idempotency_key" to JsonPrimitive(requestId), "p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)), "p_purchase_bill_id" to JsonPrimitive(draft.billId),
            "p_business_date" to JsonPrimitive(draft.businessDate), "p_reason" to JsonPrimitive(draft.reason),
            "p_lines" to JsonArray(draft.lines.map { JsonObject(mapOf("purchase_receipt_line_id" to JsonPrimitive(it.receiptLineId), "quantity" to JsonPrimitive(it.quantity))) }),
        ))).decodeAs<ReturnResultRow>().domain()
    }

    override suspend fun reverse(owner: CacheOwner, requestId: String, draft: VendorReversalDraft) = calls.execute(RemoteOperation.REVERSE_VENDOR_EVENT, true) {
        client.postgrest.rpc("reverse_vendor_event", JsonObject(mapOf(
            "p_idempotency_key" to JsonPrimitive(requestId), "p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)), "p_event_type" to JsonPrimitive(draft.eventType.name.lowercase()),
            "p_event_id" to JsonPrimitive(draft.eventId), "p_business_date" to JsonPrimitive(draft.businessDate), "p_reason" to JsonPrimitive(draft.reason),
        ))).decodeAs<ReversalResultRow>().domain()
    }
}

@Serializable private data class BillRow(val id:String,@SerialName("vendor_id")val vendorId:String,val status:String,@SerialName("invoice_reference")val invoice:String?,@SerialName("business_date")val date:String,@SerialName("occurred_at")val occurred:String,@SerialName("grand_total_paisa")val total:Long)
@Serializable private data class BillLineRow(val id:String,@SerialName("product_name")val name:String,@SerialName("sku_code")val sku:String)
@Serializable private data class ReceiptLineRow(val id:String,@SerialName("purchase_bill_id")val billId:String,@SerialName("purchase_bill_line_id")val billLineId:String,@SerialName("product_id")val productId:String,val quantity:Int,@SerialName("unit_cost_paisa")val cost:Long)
@Serializable private data class LotRow(@SerialName("purchase_receipt_line_id")val receiptLineId:String,@SerialName("remaining_quantity")val remaining:Int)
@Serializable private data class PaymentRow(val id:String,@SerialName("vendor_id")val vendorId:String,val status:String,val method:String,@SerialName("amount_paisa")val amount:Long,@SerialName("business_date")val date:String,@SerialName("occurred_at")val occurred:String,@SerialName("reversal_reason")val reversalReason:String?)
@Serializable private data class PaymentAllocationRow(@SerialName("vendor_payment_id")val paymentId:String,@SerialName("purchase_bill_id")val billId:String,@SerialName("amount_paisa")val amount:Long)
@Serializable private data class ReturnRow(val id:String,@SerialName("vendor_id")val vendorId:String,@SerialName("purchase_bill_id")val billId:String,val status:String,val reason:String,@SerialName("total_value_paisa")val total:Long,@SerialName("business_date")val date:String,@SerialName("occurred_at")val occurred:String,@SerialName("reversal_reason")val reversalReason:String?)
@Serializable private data class ReturnLineRow(@SerialName("vendor_return_id")val returnId:String,@SerialName("purchase_receipt_line_id")val receiptLineId:String,val quantity:Int)
@Serializable private data class PaymentResultRow(@SerialName("vendor_payment_id")val id:String,@SerialName("vendor_id")val vendorId:String,@SerialName("amount_paisa")val amount:Long,@SerialName("allocation_count")val count:Int,@SerialName("vendor_due_after_paisa")val due:Long){fun domain()=PostedVendorPayment(id,vendorId,amount,count,due)}
@Serializable private data class ReturnResultRow(@SerialName("vendor_return_id")val id:String,@SerialName("purchase_bill_id")val billId:String,@SerialName("return_value_paisa")val value:Long,@SerialName("bill_due_after_paisa")val due:Long,@SerialName("line_count")val count:Int){fun domain()=PostedVendorReturn(id,billId,value,due,count)}
@Serializable private data class ReversalResultRow(@SerialName("event_type")val type:String,@SerialName("event_id")val id:String,@SerialName("reversal_journal_id")val journalId:String?){fun domain()=PostedVendorReversal(type,id,journalId)}
