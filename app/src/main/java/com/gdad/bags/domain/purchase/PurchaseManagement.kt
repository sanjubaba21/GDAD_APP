package com.gdad.bags.domain.purchase

import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

data class Vendor(
    val id: String,
    val name: String,
    val phone: String?,
    val taxReference: String?,
    val notes: String?,
    val duePaisa: Long,
    val active: Boolean,
)

data class PurchaseAccount(
    val id: String,
    val name: String,
    val type: String,
    val balancePaisa: Long,
    val active: Boolean,
)

data class PurchaseDirectory(
    val vendors: List<Vendor> = emptyList(),
    val accounts: List<PurchaseAccount> = emptyList(),
)

data class VendorDraft(
    val vendorId: String? = null,
    val name: String,
    val phone: String?,
    val taxReference: String?,
    val notes: String?,
)

enum class VendorMutation { CREATE, UPDATE, ARCHIVE }
enum class PurchasePaymentMethod { CASH, BANK }

data class PurchaseLineDraft(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitCostPaisa: Long,
) {
    val lineTotalPaisa: Long get() = Math.multiplyExact(quantity.toLong(), unitCostPaisa)
}

data class PurchaseDraft(
    val vendorId: String,
    val invoiceReference: String?,
    val businessDate: String,
    val lines: List<PurchaseLineDraft>,
    val paymentAmountPaisa: Long,
    val paymentMethod: PurchasePaymentMethod?,
)

data class PostedPurchase(
    val purchaseBillId: String,
    val purchaseReceiptId: String,
    val vendorPaymentId: String?,
    val grandTotalPaisa: Long,
    val paidPaisa: Long,
    val duePaisa: Long,
    val lineCount: Int,
)

sealed interface PurchaseResult<out T> {
    data class Success<T>(val value: T, val safeMessage: String) : PurchaseResult<T>
    data class Failure(val error: RemoteFailure?, val safeMessage: String) : PurchaseResult<Nothing>
}

interface PurchaseManagementRepository {
    fun observe(session: UserSession): Flow<PurchaseDirectory>
    suspend fun refresh(session: UserSession): PurchaseResult<Unit>
    suspend fun manageVendor(session: UserSession, requestId: String, mutation: VendorMutation, draft: VendorDraft): PurchaseResult<Unit>
    suspend fun postPurchase(session: UserSession, requestId: String, draft: PurchaseDraft): PurchaseResult<PostedPurchase>
}
