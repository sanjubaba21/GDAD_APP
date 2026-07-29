package com.gdad.bags.data.sale

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.sale.PostedSale
import com.gdad.bags.domain.sale.SaleDraft
import com.gdad.bags.domain.sale.SaleResult
import com.gdad.bags.domain.sale.SaleCheckoutRepository
import java.util.UUID

class ProductionSaleCheckoutRepository(
    private val remote: SaleRemoteDataSource,
    private val products: ProductCatalogRepository,
) : SaleCheckoutRepository {
    override suspend fun post(
        session: UserSession,
        requestId: String,
        draft: SaleDraft,
    ): SaleResult<PostedSale> {
        if (session.role == UserRole.SUPER_ADMIN || !session.shopId.isUuid()) return denied()
        if (!requestId.isUuid() || !draft.valid(session.role)) return invalid()
        return when (val result = remote.post(CacheOwner(session.userId, session.shopId), requestId, draft)) {
            is RemoteResult.Failure -> result.error.failure()
            is RemoteResult.Success -> {
                products.refresh(session)
                SaleResult.Success(result.value, "Sale posted with authoritative FIFO totals.")
            }
        }
    }

    private fun SaleDraft.valid(role: UserRole): Boolean {
        if (
            runCatching { kotlinx.datetime.LocalDate.parse(businessDate) }.isFailure ||
            lines.size !in 1..100 || lines.distinctBy { it.productId }.size != lines.size ||
            saleDiscountPaisa < 0 || payments.size > 10
        ) return false
        if (lines.any { line ->
                !line.productId.isUuid() || line.quantity <= 0 ||
                    line.effectiveUnitPricePaisa?.let { it < 0 } == true ||
                    line.lineDiscountPaisa < 0 ||
                    line.effectiveUnitPricePaisa?.let { price ->
                        runCatching { Math.multiplyExact(price, line.quantity.toLong()) }.isFailure
                    } == true
            } || payments.any { it.amountPaisa <= 0 } ||
            MoneyAmounts.sumPaisa(payments.map { it.amountPaisa }) == null
        ) return false
        if (
            role == UserRole.SALESMAN &&
            (saleDiscountPaisa != 0L || isCredit ||
                lines.any { it.effectiveUnitPricePaisa != null || it.lineDiscountPaisa != 0L })
        ) return false
        return if (isCredit) {
            role == UserRole.OWNER && !customerName.isNullOrBlank() &&
                !customerContact.isNullOrBlank() &&
                dueDate?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.isSuccess } == true
        } else {
            customerName == null && customerContact == null && dueDate == null
        }
    }

    private fun RemoteFailure.failure() = SaleResult.Failure(this, when (kind) {
        RemoteErrorKind.UNAUTHORIZED -> "This pricing or credit sale is not allowed."
        RemoteErrorKind.VALIDATION -> "Review products, prices, discounts, payment, customer, and date."
        RemoteErrorKind.CONFLICT -> "Insufficient stock or an accounting resource is unavailable."
        RemoteErrorKind.OFFLINE -> "Sales require an internet connection."
        RemoteErrorKind.TIMEOUT -> "The request timed out. Retry the same sale safely."
        RemoteErrorKind.RATE_LIMITED -> "Too many attempts. Wait before retrying."
        RemoteErrorKind.UNKNOWN -> "Unable to post the sale."
    })

    private fun denied() = SaleResult.Failure(null, "This sale operation is not allowed.")
    private fun invalid() = SaleResult.Failure(
        null,
        "Review products, prices, discounts, payment, customer, and date.",
    )
    private fun String?.isUuid() = this != null && runCatching { UUID.fromString(this) }.isSuccess
}
