package com.gdad.bags.data.purchase

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseDirectory
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.PurchaseManagementRepository
import com.gdad.bags.domain.purchase.PurchaseResult
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ProductionPurchaseManagementRepository(
    private val remote: PurchaseRemoteDataSource,
    private val store: PurchaseDirectoryStore,
    private val products: ProductCatalogRepository,
) : PurchaseManagementRepository {
    override fun observe(session: UserSession): Flow<PurchaseDirectory> = store.observe(session.owner())

    override suspend fun refresh(session: UserSession): PurchaseResult<Unit> {
        if (!session.isValidOwner()) return denied()
        return when (val result = remote.load(session.owner())) {
            is RemoteResult.Failure -> result.error.failure("Unable to refresh purchasing data.")
            is RemoteResult.Success -> {
                store.replace(session.owner(), result.value.vendors, result.value.accounts)
                PurchaseResult.Success(Unit, "Purchasing data refreshed.")
            }
        }
    }

    override suspend fun manageVendor(
        session: UserSession,
        requestId: String,
        mutation: VendorMutation,
        draft: VendorDraft,
    ): PurchaseResult<Unit> {
        if (!session.isValidOwner()) return denied()
        if (!requestId.isUuid() || !draft.valid(mutation)) return invalid()
        return when (val result = remote.manageVendor(session.owner(), requestId, mutation, draft)) {
            is RemoteResult.Failure -> result.error.failure("Unable to update the vendor.")
            is RemoteResult.Success -> when (val refreshed = refresh(session)) {
                is PurchaseResult.Failure -> refreshed
                is PurchaseResult.Success -> PurchaseResult.Success(Unit, when (mutation) {
                    VendorMutation.CREATE -> "Vendor created and audited."
                    VendorMutation.UPDATE -> "Vendor updated and audited."
                    VendorMutation.ARCHIVE -> "Vendor archived; history remains available."
                })
            }
        }
    }

    override suspend fun postPurchase(
        session: UserSession,
        requestId: String,
        draft: PurchaseDraft,
    ): PurchaseResult<PostedPurchase> {
        if (!session.isValidOwner()) return denied()
        if (!requestId.isUuid() || !draft.valid()) return invalid()
        return when (val result = remote.postPurchase(session.owner(), requestId, draft)) {
            is RemoteResult.Failure -> result.error.failure("Unable to post the purchase.")
            is RemoteResult.Success -> {
                refresh(session)
                products.refresh(session)
                PurchaseResult.Success(result.value, "Purchase posted with authoritative totals.")
            }
        }
    }

    private fun VendorDraft.valid(mutation: VendorMutation) = when (mutation) {
        VendorMutation.ARCHIVE -> vendorId.isUuid()
        VendorMutation.CREATE, VendorMutation.UPDATE ->
            (mutation == VendorMutation.CREATE || vendorId.isUuid()) && name.trim().length in 1..160 &&
                (phone == null || phone.trim().length in 1..40) &&
                (taxReference == null || taxReference.trim().length in 1..80) &&
                (notes == null || notes.trim().length in 1..1000)
    }

    private fun PurchaseDraft.valid(): Boolean = vendorId.isUuid() &&
        runCatching { kotlinx.datetime.LocalDate.parse(businessDate) }.isSuccess &&
        lines.size in 1..100 && lines.distinctBy { it.productId }.size == lines.size &&
        lines.all { it.productId.isUuid() && it.quantity > 0 && it.unitCostPaisa >= 0 && runCatching { it.lineTotalPaisa }.isSuccess } &&
        paymentAmountPaisa >= 0 && (paymentAmountPaisa == 0L) == (paymentMethod == null) &&
        runCatching { lines.map { it.lineTotalPaisa } }.getOrNull()
            ?.let(MoneyAmounts::sumPaisa)?.let { paymentAmountPaisa <= it } == true

    private fun RemoteFailure.failure(default: String) = PurchaseResult.Failure(this, when (kind) {
        RemoteErrorKind.UNAUTHORIZED -> "This Owner operation is not allowed."
        RemoteErrorKind.VALIDATION -> "Review the date, invoice, lines, quantities, costs, and payment."
        RemoteErrorKind.CONFLICT -> "The invoice already exists or the accounting period/resource is unavailable."
        RemoteErrorKind.OFFLINE -> "Purchases require an internet connection. Connect and retry the same request."
        RemoteErrorKind.TIMEOUT -> "The request timed out. Retry to safely check the same purchase."
        RemoteErrorKind.RATE_LIMITED -> "Too many attempts. Wait before retrying."
        RemoteErrorKind.UNKNOWN -> default
    })
    private fun denied() = PurchaseResult.Failure(null, "This Owner operation is not allowed.")
    private fun invalid() = PurchaseResult.Failure(null, "Review the date, invoice, lines, quantities, costs, and payment.")
    private fun UserSession.isValidOwner() = role == UserRole.OWNER && shopId.isUuid()
    private fun UserSession.owner() = CacheOwner(userId, shopId)
    private fun String?.isUuid() = this != null && runCatching { UUID.fromString(this) }.isSuccess
}
