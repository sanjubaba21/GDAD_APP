package com.gdad.bags.domain.vendorfinance

import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserSession

enum class VendorPaymentMethod { CASH, BANK }
enum class VendorEventType { PAYMENT, RETURN }

data class VendorReceiptLine(
    val id: String,
    val productId: String,
    val productName: String,
    val sku: String,
    val quantity: Int,
    val unitCostPaisa: Long,
    val returnedQuantity: Int,
    val availableQuantity: Int,
) {
    val returnableQuantity get() = (quantity - returnedQuantity).coerceAtMost(availableQuantity).coerceAtLeast(0)
}

data class VendorBill(
    val id: String,
    val vendorId: String,
    val status: String,
    val invoiceReference: String?,
    val businessDate: String,
    val occurredAt: String,
    val grandTotalPaisa: Long,
    val duePaisa: Long,
    val lines: List<VendorReceiptLine>,
)

data class VendorPaymentEvent(
    val id: String,
    val vendorId: String,
    val status: String,
    val method: String,
    val amountPaisa: Long,
    val businessDate: String,
    val occurredAt: String,
    val allocations: Map<String, Long>,
    val reversalReason: String?,
)

data class VendorReturnEvent(
    val id: String,
    val vendorId: String,
    val billId: String,
    val status: String,
    val reason: String,
    val totalPaisa: Long,
    val businessDate: String,
    val occurredAt: String,
    val reversalReason: String?,
)

data class VendorLedger(
    val bills: List<VendorBill> = emptyList(),
    val payments: List<VendorPaymentEvent> = emptyList(),
    val returns: List<VendorReturnEvent> = emptyList(),
)

data class VendorPaymentAllocationDraft(val billId: String, val amountPaisa: Long)
data class VendorPaymentDraft(
    val vendorId: String,
    val method: VendorPaymentMethod,
    val businessDate: String,
    val allocations: List<VendorPaymentAllocationDraft>,
)
data class VendorReturnLineDraft(val receiptLineId: String, val quantity: Int)
data class VendorReturnDraft(
    val billId: String,
    val businessDate: String,
    val reason: String,
    val lines: List<VendorReturnLineDraft>,
)
data class VendorReversalDraft(
    val eventType: VendorEventType,
    val eventId: String,
    val businessDate: String,
    val reason: String,
)

data class PostedVendorPayment(
    val paymentId: String,
    val vendorId: String,
    val amountPaisa: Long,
    val allocationCount: Int,
    val vendorDueAfterPaisa: Long,
)
data class PostedVendorReturn(
    val returnId: String,
    val billId: String,
    val returnValuePaisa: Long,
    val billDueAfterPaisa: Long,
    val lineCount: Int,
)
data class PostedVendorReversal(
    val eventType: String,
    val eventId: String,
    val reversalJournalId: String?,
)

sealed interface VendorFinanceResult<out T> {
    data class Success<T>(val value: T, val safeMessage: String) : VendorFinanceResult<T>
    data class Failure(val error: RemoteFailure?, val safeMessage: String) : VendorFinanceResult<Nothing>
}

interface VendorFinanceRepository {
    suspend fun load(session: UserSession): VendorFinanceResult<VendorLedger>
    suspend fun postPayment(session: UserSession, requestId: String, draft: VendorPaymentDraft): VendorFinanceResult<PostedVendorPayment>
    suspend fun postReturn(session: UserSession, requestId: String, draft: VendorReturnDraft): VendorFinanceResult<PostedVendorReturn>
    suspend fun reverse(session: UserSession, requestId: String, draft: VendorReversalDraft): VendorFinanceResult<PostedVendorReversal>
}
