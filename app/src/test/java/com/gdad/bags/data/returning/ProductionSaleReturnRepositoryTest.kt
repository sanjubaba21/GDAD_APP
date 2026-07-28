package com.gdad.bags.data.returning

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
import com.gdad.bags.domain.returning.PostedSaleReturn
import com.gdad.bags.domain.returning.RefundMethod
import com.gdad.bags.domain.returning.ReturnDisposition
import com.gdad.bags.domain.returning.ReturnLineDraft
import com.gdad.bags.domain.returning.ReturnResult
import com.gdad.bags.domain.returning.SaleAllocation
import com.gdad.bags.domain.returning.SaleHistory
import com.gdad.bags.domain.returning.SaleHistoryEntry
import com.gdad.bags.domain.returning.SaleHistoryLine
import com.gdad.bags.domain.returning.SaleReturnDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSaleReturnRepositoryTest {
    @Test
    fun salesmanLoadsHistoryWithoutCostAndCannotPost() = runBlocking {
        val remote = FakeRemote()
        val repository = ProductionSaleReturnRepository(remote, FakeProducts())
        val salesman = OWNER.copy(role = UserRole.SALESMAN)

        assertTrue(repository.load(salesman) is ReturnResult.Success)
        assertEquals(listOf(false), remote.costRequests)
        assertTrue(repository.post(salesman, REQUEST, DRAFT) is ReturnResult.Failure)
        assertTrue(remote.requestIds.isEmpty())
    }

    @Test
    fun ownerLoadsCostAndRefreshesProductsAfterAuthoritativeReturn() = runBlocking {
        val remote = FakeRemote()
        val products = FakeProducts()
        val repository = ProductionSaleReturnRepository(remote, products)

        val loaded = repository.load(OWNER) as ReturnResult.Success
        assertEquals(5000, loaded.value.sales.single().lines.single().allocations.single().unitCostPaisa)
        val result = repository.post(OWNER, REQUEST, DRAFT) as ReturnResult.Success

        assertEquals(POSTED, result.value)
        assertEquals(listOf(REQUEST), remote.requestIds)
        assertEquals(1, products.refreshes)
        assertEquals(1, remote.costRequests.size)
    }

    @Test
    fun conflictKeepsSafeActionableMessage() = runBlocking {
        val remote = FakeRemote().apply {
            postResult = RemoteResult.Failure(
                RemoteFailure(RemoteErrorKind.CONFLICT, RetryDisposition.NEVER),
            )
        }
        val repository = ProductionSaleReturnRepository(remote, FakeProducts())

        val result = repository.post(OWNER, REQUEST, DRAFT) as ReturnResult.Failure

        assertTrue(result.safeMessage.contains("refreshed", ignoreCase = true))
        assertEquals(0, remote.costRequests.size)
    }

    @Test
    fun invalidDraftNeverReachesRemote() = runBlocking {
        val remote = FakeRemote()
        val repository = ProductionSaleReturnRepository(remote, FakeProducts())

        val result = repository.post(OWNER, REQUEST, DRAFT.copy(reason = ""))

        assertTrue(result is ReturnResult.Failure)
        assertTrue(remote.requestIds.isEmpty())
    }

    private class FakeRemote : SaleReturnRemoteDataSource {
        val costRequests = mutableListOf<Boolean>()
        val requestIds = mutableListOf<String>()
        var postResult: RemoteResult<PostedSaleReturn> = RemoteResult.Success(POSTED)

        override suspend fun load(
            owner: CacheOwner,
            includeCost: Boolean,
        ): RemoteResult<SaleHistory> {
            costRequests += includeCost
            val value = if (includeCost) HISTORY else HISTORY.withoutCost()
            return RemoteResult.Success(value)
        }

        override suspend fun post(
            owner: CacheOwner,
            requestId: String,
            draft: SaleReturnDraft,
        ): RemoteResult<PostedSaleReturn> {
            requestIds += requestId
            return postResult
        }
    }

    private class FakeProducts : ProductCatalogRepository {
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

    companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val ACTOR = "22222222-2222-4222-8222-222222222222"
        const val SALE = "33333333-3333-4333-8333-333333333333"
        const val LINE = "44444444-4444-4444-8444-444444444444"
        const val PRODUCT = "55555555-5555-4555-8555-555555555555"
        const val LOT = "66666666-6666-4666-8666-666666666666"
        const val REQUEST = "77777777-7777-4777-8777-777777777777"
        const val RETURN = "88888888-8888-4888-8888-888888888888"
        val OWNER = UserSession(ACTOR, "Owner", UserRole.OWNER, SHOP)
        val DRAFT = SaleReturnDraft(
            SALE,
            "2026-07-28",
            "Customer exchange",
            listOf(ReturnLineDraft(LINE, 1, ReturnDisposition.SELLABLE)),
            RefundMethod.CASH,
        )
        val POSTED = PostedSaleReturn(RETURN, SALE, 10000, 10000, 0, 1, 5000, "returned")
        val HISTORY = SaleHistory(
            listOf(
                SaleHistoryEntry(
                    SALE,
                    "posted",
                    false,
                    "Customer",
                    "9800000000",
                    "2026-07-28",
                    "2026-07-28T10:00:00Z",
                    20000,
                    20000,
                    0,
                    0,
                    0,
                    listOf(
                        SaleHistoryLine(
                            LINE,
                            PRODUCT,
                            "Bag",
                            "B-1",
                            2,
                            10000,
                            20000,
                            0,
                            0,
                            listOf(SaleAllocation(LOT, 2, 5000)),
                        ),
                    ),
                ),
            ),
        )
    }
}

private fun SaleHistory.withoutCost() = SaleHistory(
    sales.map { sale ->
        sale.copy(lines = sale.lines.map { it.copy(allocations = emptyList()) })
    },
)
