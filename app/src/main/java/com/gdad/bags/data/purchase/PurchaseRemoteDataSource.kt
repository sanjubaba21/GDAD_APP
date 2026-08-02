package com.gdad.bags.data.purchase

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedAccountEntity
import com.gdad.bags.data.local.CachedVendorEntity
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteOperation
import com.gdad.bags.data.remote.RemoteQueryWindow
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.requireSupportedWindow
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class PurchaseRemoteSnapshot(val vendors: List<CachedVendorEntity>, val accounts: List<CachedAccountEntity>)

interface PurchaseRemoteDataSource {
    suspend fun load(owner: CacheOwner): RemoteResult<PurchaseRemoteSnapshot>
    suspend fun manageVendor(owner: CacheOwner, requestId: String, mutation: VendorMutation, draft: VendorDraft): RemoteResult<Unit>
    suspend fun postPurchase(owner: CacheOwner, requestId: String, draft: PurchaseDraft): RemoteResult<PostedPurchase>
}

class SupabasePurchaseRemoteDataSource(
    private val client: SupabaseClient,
    private val calls: RemoteCallExecutor,
) : PurchaseRemoteDataSource {
    override suspend fun load(owner: CacheOwner) = calls.execute(RemoteOperation.LOAD_PURCHASE_DIRECTORY, true) {
        val vendors = client.from("vendors").select(
            Columns.raw("id,display_name,phone,tax_reference,notes,active"),
        ) {
            limit(RemoteQueryWindow.REQUEST_ROWS)
            order("display_name", Order.ASCENDING)
            order("id", Order.ASCENDING)
        }.decodeList<VendorRow>().requireSupportedWindow("vendors")
        val report = client.postgrest.rpc(
            "get_dashboard_report",
            JsonObject(mapOf("p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)))),
        ).decodeAs<DashboardReport>()
        val dues = report.vendorDues.requireSupportedWindow("dashboard vendor dues")
            .associate { it.vendorId to it.duePaisa }
        val accounts = report.accountBalances.requireSupportedWindow("dashboard account balances")
        PurchaseRemoteSnapshot(
            vendors.map { row ->
                CachedVendorEntity(owner.userId, owner.tenantKey, row.id, row.name, row.phone, row.taxReference, row.notes, dues[row.id] ?: 0, row.active)
            },
            accounts.map { row ->
                CachedAccountEntity(owner.userId, owner.tenantKey, row.id, row.id, row.name, row.type, row.balancePaisa, true)
            },
        )
    }

    override suspend fun manageVendor(owner: CacheOwner, requestId: String, mutation: VendorMutation, draft: VendorDraft) =
        calls.execute(RemoteOperation.MANAGE_VENDOR, true) {
            client.postgrest.rpc("manage_vendor", JsonObject(mapOf(
                "p_idempotency_key" to JsonPrimitive(requestId),
                "p_action" to JsonPrimitive(mutation.name.lowercase()),
                "p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),
                "p_vendor_id" to (draft.vendorId?.let(::JsonPrimitive) ?: JsonNull),
                "p_display_name" to JsonPrimitive(draft.name),
                "p_phone" to (draft.phone?.let(::JsonPrimitive) ?: JsonNull),
                "p_tax_reference" to (draft.taxReference?.let(::JsonPrimitive) ?: JsonNull),
                "p_notes" to (draft.notes?.let(::JsonPrimitive) ?: JsonNull),
            )))
            Unit
        }

    override suspend fun postPurchase(owner: CacheOwner, requestId: String, draft: PurchaseDraft) =
        calls.execute(RemoteOperation.POST_PURCHASE_RECEIPT, true) {
            val payload = JsonObject(mapOf(
                "p_idempotency_key" to JsonPrimitive(requestId),
                "p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),
                "p_vendor_id" to JsonPrimitive(draft.vendorId),
                "p_invoice_reference" to (draft.invoiceReference?.let(::JsonPrimitive) ?: JsonNull),
                "p_business_date" to JsonPrimitive(draft.businessDate),
                "p_lines" to JsonArray(draft.lines.map { JsonObject(mapOf(
                    "product_id" to JsonPrimitive(it.productId),
                    "quantity" to JsonPrimitive(it.quantity),
                    "unit_cost_paisa" to JsonPrimitive(it.unitCostPaisa),
                )) }),
                "p_payment_amount_paisa" to JsonPrimitive(draft.paymentAmountPaisa),
                "p_payment_method" to (draft.paymentMethod?.name?.lowercase()?.let(::JsonPrimitive) ?: JsonNull),
            ))
            client.postgrest.rpc("post_purchase_receipt", payload).decodeAs<PostedPurchaseRow>().domain()
        }
}

@Serializable private data class VendorRow(
    val id: String,
    @SerialName("display_name") val name: String,
    val phone: String?,
    @SerialName("tax_reference") val taxReference: String?,
    val notes: String?,
    val active: Boolean,
)
@Serializable private data class DashboardReport(
    @SerialName("vendor_dues") val vendorDues: List<VendorDueRow> = emptyList(),
    @SerialName("account_balances") val accountBalances: List<AccountBalanceRow> = emptyList(),
)
@Serializable private data class VendorDueRow(@SerialName("vendor_id") val vendorId: String, @SerialName("due_paisa") val duePaisa: Long)
@Serializable private data class AccountBalanceRow(
    @SerialName("account_id") val id: String,
    @SerialName("display_name") val name: String,
    @SerialName("account_type") val type: String,
    @SerialName("balance_paisa") val balancePaisa: Long,
)
@Serializable private data class PostedPurchaseRow(
    @SerialName("purchase_bill_id") val billId: String,
    @SerialName("purchase_receipt_id") val receiptId: String,
    @SerialName("vendor_payment_id") val paymentId: String?,
    @SerialName("grand_total_paisa") val grandTotal: Long,
    @SerialName("paid_paisa") val paid: Long,
    @SerialName("due_paisa") val due: Long,
    @SerialName("line_count") val lineCount: Int,
) {
    fun domain() = PostedPurchase(billId, receiptId, paymentId, grandTotal, paid, due, lineCount)
}
