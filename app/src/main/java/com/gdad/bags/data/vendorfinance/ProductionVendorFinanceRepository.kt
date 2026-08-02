package com.gdad.bags.data.vendorfinance

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.purchase.PurchaseManagementRepository
import com.gdad.bags.domain.vendorfinance.*
import java.util.UUID

class ProductionVendorFinanceRepository(
    private val remote: VendorFinanceRemoteDataSource,
    private val purchases: PurchaseManagementRepository,
    private val products: ProductCatalogRepository,
) : VendorFinanceRepository {
    override suspend fun load(session: UserSession): VendorFinanceResult<VendorLedger> {
        if (!session.owner()) return denied()
        return when (val result = remote.load(session.cacheOwner())) {
            is RemoteResult.Success -> VendorFinanceResult.Success(result.value, "Vendor ledger refreshed.")
            is RemoteResult.Failure -> result.error.failure("Unable to refresh the vendor ledger.")
        }
    }

    override suspend fun postPayment(session: UserSession, requestId: String, draft: VendorPaymentDraft): VendorFinanceResult<PostedVendorPayment> {
        if (!session.owner()) return denied()
        if (!requestId.uuid() || !draft.valid()) return invalid("Review the payment date, method, bills, and positive amounts.")
        return when (val result = remote.postPayment(session.cacheOwner(), requestId, draft)) {
            is RemoteResult.Failure -> result.error.failure("Unable to post the vendor payment.")
            is RemoteResult.Success -> {
                purchases.refresh(session)
                VendorFinanceResult.Success(result.value, "Vendor payment posted with authoritative due.")
            }
        }
    }

    override suspend fun postReturn(session: UserSession, requestId: String, draft: VendorReturnDraft): VendorFinanceResult<PostedVendorReturn> {
        if (!session.owner()) return denied()
        if (!requestId.uuid() || !draft.valid()) return invalid("Review the return date, reason, lines, and quantities.")
        return when (val result = remote.postReturn(session.cacheOwner(), requestId, draft)) {
            is RemoteResult.Failure -> result.error.failure("Unable to post the vendor return.")
            is RemoteResult.Success -> {
                purchases.refresh(session); products.refresh(session)
                VendorFinanceResult.Success(result.value, "Vendor return posted with authoritative stock and due.")
            }
        }
    }

    override suspend fun reverse(session: UserSession, requestId: String, draft: VendorReversalDraft): VendorFinanceResult<PostedVendorReversal> {
        if (!session.owner()) return denied()
        if (!requestId.uuid() || !draft.valid()) return invalid("Review the reversal date, event, and required reason.")
        return when (val result = remote.reverse(session.cacheOwner(), requestId, draft)) {
            is RemoteResult.Failure -> result.error.failure("Unable to reverse the vendor event.")
            is RemoteResult.Success -> {
                purchases.refresh(session); if (draft.eventType == VendorEventType.RETURN) products.refresh(session)
                VendorFinanceResult.Success(result.value, "Vendor event reversed with an immutable journal.")
            }
        }
    }

    private fun VendorPaymentDraft.valid() = vendorId.uuid() && date(businessDate) && allocations.size in 1..100 && allocations.distinctBy { it.billId }.size == allocations.size && allocations.all { it.billId.uuid() && it.amountPaisa > 0 } && MoneyAmounts.sumPaisa(allocations.map { it.amountPaisa }) != null
    private fun VendorReturnDraft.valid() = billId.uuid() && date(businessDate) && reason.trim().length in 1..500 && lines.size in 1..100 && lines.distinctBy { it.receiptLineId }.size == lines.size && lines.all { it.receiptLineId.uuid() && it.quantity > 0 }
    private fun VendorReversalDraft.valid() = eventId.uuid() && date(businessDate) && reason.trim().length in 1..500
    private fun date(value:String)=runCatching{kotlinx.datetime.LocalDate.parse(value)}.isSuccess
    private fun RemoteFailure.failure(default:String)=VendorFinanceResult.Failure(this,when(kind){RemoteErrorKind.UNAUTHORIZED->"This Owner vendor operation is not allowed.";RemoteErrorKind.VALIDATION->"Review current bill due, available stock, date, amounts, quantities, and reason.";RemoteErrorKind.CONFLICT->"Vendor balances or an accounting resource changed. Refresh and review.";RemoteErrorKind.OFFLINE->"Vendor financial operations require an internet connection.";RemoteErrorKind.TIMEOUT->"The request timed out. Retry the same operation safely.";RemoteErrorKind.RATE_LIMITED->"Too many attempts. Wait before retrying.";RemoteErrorKind.UNKNOWN->default})
    private fun denied()=VendorFinanceResult.Failure(null,"Owner vendor-finance access is required.")
    private fun invalid(message:String)=VendorFinanceResult.Failure(null,message)
    private fun UserSession.owner()=role==UserRole.OWNER&&shopId.uuid()
    private fun UserSession.cacheOwner()=CacheOwner(userId,shopId)
    private fun String?.uuid()=this!=null&&runCatching{UUID.fromString(this)}.isSuccess
}
