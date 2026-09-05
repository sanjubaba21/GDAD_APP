package com.gdad.bags.data.sale

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.product.ProductResult
import com.gdad.bags.domain.sale.PostedSale
import com.gdad.bags.domain.sale.SaleDraft
import com.gdad.bags.domain.sale.SaleLineDraft
import com.gdad.bags.domain.sale.SalePaymentDraft
import com.gdad.bags.domain.sale.SalePaymentMethod
import com.gdad.bags.domain.sale.SaleResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSaleCheckoutRepositoryTest {
    @Test
    fun salesmanCanUseNegotiatedPriceButCannotDiscountOrCreateCredit() = runBlocking {
        val remote = Remote()
        val repo = ProductionSaleCheckoutRepository(remote, Products())
        val negotiated = DRAFT.copy(
            lines = DRAFT.lines.map { it.copy(effectiveUnitPricePaisa = 750) },
            payments = listOf(SalePaymentDraft(SalePaymentMethod.CASH, 750)),
        )

        assertTrue(repo.post(SALESMAN, REQUEST, negotiated) is SaleResult.Success)
        assertEquals(750L, remote.drafts.single().lines.single().effectiveUnitPricePaisa)

        repo.post(SALESMAN, REQUEST, DRAFT.copy(saleDiscountPaisa = 1))
        repo.post(
            SALESMAN,
            REQUEST,
            DRAFT.copy(lines = DRAFT.lines.map { it.copy(lineDiscountPaisa = 1) }),
        )
        repo.post(
            SALESMAN,
            REQUEST,
            DRAFT.copy(
                isCredit = true,
                customerName = "A",
                customerContact = "1",
                dueDate = "2026-07-28",
            ),
        )
        assertEquals(1, remote.ids.size)
    }

    @Test
    fun successUsesServerResultAndRefreshesStock() = runBlocking {
        val remote = Remote()
        val products = Products()
        val repo = ProductionSaleCheckoutRepository(remote, products)
        val result = repo.post(SALESMAN, REQUEST, DRAFT) as SaleResult.Success
        assertEquals(POSTED, result.value)
        assertEquals(1, products.refreshes)
    }

    @Test
    fun insufficientStockIsActionableAndSameKeyCanRetry() = runBlocking {
        val remote = Remote()
        remote.results += RemoteResult.Failure(
            RemoteFailure(RemoteErrorKind.VALIDATION, RetryDisposition.NEVER),
        )
        remote.results += RemoteResult.Success(POSTED)
        val repo = ProductionSaleCheckoutRepository(remote, Products())
        assertTrue(
            (repo.post(SALESMAN, REQUEST, DRAFT) as SaleResult.Failure)
                .safeMessage.contains("products"),
        )
        assertTrue(repo.post(SALESMAN, REQUEST, DRAFT) is SaleResult.Success)
        assertEquals(listOf(REQUEST, REQUEST), remote.ids)
    }

    @Test
    fun overflowingExplicitPriceOrPaymentTotalNeverReachesRemote() = runBlocking {
        val remote = Remote()
        val repo = ProductionSaleCheckoutRepository(remote, Products())
        val owner = SALESMAN.copy(role = UserRole.OWNER)
        assertTrue(
            repo.post(
                owner,
                REQUEST,
                DRAFT.copy(
                    lines = listOf(
                        DRAFT.lines.single().copy(
                            quantity = 2,
                            effectiveUnitPricePaisa = Long.MAX_VALUE,
                        ),
                    ),
                ),
            ) is SaleResult.Failure,
        )
        assertTrue(
            repo.post(
                owner,
                REQUEST,
                DRAFT.copy(
                    payments = listOf(
                        SalePaymentDraft(SalePaymentMethod.CASH, Long.MAX_VALUE),
                        SalePaymentDraft(SalePaymentMethod.BANK, 1),
                    ),
                ),
            ) is SaleResult.Failure,
        )
        assertTrue(remote.ids.isEmpty())
    }

    private class Remote : SaleRemoteDataSource {
        val ids = mutableListOf<String>()
        val drafts = mutableListOf<SaleDraft>()
        val results = ArrayDeque<RemoteResult<PostedSale>>()

        override suspend fun post(
            owner: CacheOwner,
            requestId: String,
            draft: SaleDraft,
        ): RemoteResult<PostedSale> {
            ids += requestId
            drafts += draft
            return results.removeFirstOrNull() ?: RemoteResult.Success(POSTED)
        }
    }

    private class Products : ProductCatalogRepository {
        var refreshes = 0

        override fun observe(session: UserSession): Flow<List<CatalogProduct>> = flowOf(emptyList())

        override suspend fun refresh(session: UserSession): ProductResult {
            refreshes++
            return ProductResult.Success("ok")
        }

        override suspend fun mutate(
            session: UserSession,
            requestId: String,
            mutation: ProductMutation,
            draft: ProductDraft,
        ) = ProductResult.Success("ok")
    }

    private companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val PRODUCT = "22222222-2222-4222-8222-222222222222"
        const val REQUEST = "33333333-3333-4333-8333-333333333333"
        val SALESMAN = UserSession(
            "44444444-4444-4444-8444-444444444444",
            "Sales",
            UserRole.SALESMAN,
            SHOP,
        )
        val DRAFT = SaleDraft(
            "2026-07-28",
            listOf(SaleLineDraft(PRODUCT, "Bag", 1, null)),
            payments = listOf(SalePaymentDraft(SalePaymentMethod.CASH, 1_000)),
        )
        val POSTED = PostedSale(
            "55555555-5555-4555-8555-555555555555",
            1_000,
            1_000,
            0,
            null,
            1,
            1,
        )
    }
}
