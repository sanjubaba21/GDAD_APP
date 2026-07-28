package com.gdad.bags.data.returning

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.returning.PostedSaleReturn
import com.gdad.bags.domain.returning.ReturnResult
import com.gdad.bags.domain.returning.SaleHistory
import com.gdad.bags.domain.returning.SaleReturnDraft
import com.gdad.bags.domain.returning.SaleReturnRepository
import java.util.UUID

class ProductionSaleReturnRepository(
    private val remote: SaleReturnRemoteDataSource,
    private val products: ProductCatalogRepository,
) : SaleReturnRepository {
    override suspend fun load(session: UserSession): ReturnResult<SaleHistory> {
        if (session.role == UserRole.SUPER_ADMIN || !session.shopId.isUuid()) return denied()
        return when (
            val result = remote.load(
                session.owner(),
                includeCost = session.role == UserRole.OWNER,
            )
        ) {
            is RemoteResult.Success -> ReturnResult.Success(
                result.value,
                "Sale history refreshed.",
            )
            is RemoteResult.Failure -> result.error.failure("Unable to refresh sale history.")
        }
    }

    override suspend fun post(
        session: UserSession,
        requestId: String,
        draft: SaleReturnDraft,
    ): ReturnResult<PostedSaleReturn> {
        if (session.role != UserRole.OWNER || !session.shopId.isUuid()) return denied()
        if (!requestId.isUuid() || !draft.valid()) return invalid()
        return when (val result = remote.post(session.owner(), requestId, draft)) {
            is RemoteResult.Failure -> result.error.failure("Unable to post the return.")
            is RemoteResult.Success -> {
                products.refresh(session)
                ReturnResult.Success(
                    result.value,
                    "Return posted with authoritative stock and refund totals.",
                )
            }
        }
    }

    private fun SaleReturnDraft.valid() = saleId.isUuid() &&
        reason.trim().length in 1..500 &&
        runCatching { kotlinx.datetime.LocalDate.parse(businessDate) }.isSuccess &&
        lines.size in 1..100 &&
        lines.distinctBy { it.saleLineId }.size == lines.size &&
        lines.all { it.saleLineId.isUuid() && it.quantity > 0 }

    private fun RemoteFailure.failure(default: String) = ReturnResult.Failure(
        this,
        when (kind) {
            RemoteErrorKind.UNAUTHORIZED ->
                "This return is not allowed or is outside the returnable sale state."
            RemoteErrorKind.VALIDATION ->
                "Refresh and review the date, reason, quantities, disposition, and refund method."
            RemoteErrorKind.CONFLICT ->
                "Returnable quantities or an accounting resource changed. History was refreshed."
            RemoteErrorKind.OFFLINE -> "Returns require an internet connection."
            RemoteErrorKind.TIMEOUT -> "The request timed out. Retry the same return safely."
            RemoteErrorKind.RATE_LIMITED -> "Too many attempts. Wait before retrying."
            RemoteErrorKind.UNKNOWN -> default
        },
    )

    private fun denied() = ReturnResult.Failure(null, "This return operation is not allowed.")
    private fun invalid() = ReturnResult.Failure(
        null,
        "Refresh and review the date, reason, quantities, disposition, and refund method.",
    )

    private fun UserSession.owner() = CacheOwner(userId, shopId)
    private fun String?.isUuid() = this != null && runCatching { UUID.fromString(this) }.isSuccess
}
