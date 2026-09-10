package com.gdad.bags.desktop

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedProductEntity
import com.gdad.bags.data.local.CachedStockSummaryEntity
import com.gdad.bags.data.product.ProductRemoteDataSource
import com.gdad.bags.data.product.ProductRemoteSnapshot
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.sale.ProductionSaleCheckoutRepository
import com.gdad.bags.data.sale.SaleRemoteDataSource
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DesktopBusinessRepositoryTest {
    @Test
    fun productRefreshMapsAuthoritativeStockAndOwnerCost() = runBlocking {
        val productId = UUID.randomUUID().toString()
        val shopId = UUID.randomUUID().toString()
        val remote = FakeProductRemote(
            ProductRemoteSnapshot(
                products = listOf(
                    CachedProductEntity(
                        "owner", shopId, productId, "Travel Bag", "TB-1", null,
                        125_00, 2, true, 1,
                    ),
                ),
                stock = listOf(
                    CachedStockSummaryEntity(
                        "owner", shopId, productId, 7, 420_00, false, 1,
                    ),
                ),
            ),
        )
        val repository = DesktopProductCatalogRepository(remote)
        val session = session(UserRole.OWNER, shopId)

        assertIs<ProductResult.Success>(repository.refresh(session))
        val product = repository.observe(session).first().single()

        assertEquals(7, product.quantityOnHand)
        assertEquals(420_00, product.stockValuePaisa)
        assertEquals(true, remote.lastCanSeeCost)
    }

    @Test
    fun salesmanCannotMutateProducts() = runBlocking {
        val remote = FakeProductRemote(ProductRemoteSnapshot(emptyList(), emptyList()))
        val repository = DesktopProductCatalogRepository(remote)

        val result = repository.mutate(
            session(UserRole.SALESMAN, UUID.randomUUID().toString()),
            UUID.randomUUID().toString(),
            ProductMutation.CREATE,
            ProductDraft(null, "Bag", "B-1", null, 100_00, 1),
        )

        assertIs<ProductResult.Failure>(result)
        assertNull(remote.lastMutation)
    }

    @Test
    fun salesmanMayPostNegotiatedPriceButNotDiscount() = runBlocking {
        val shopId = UUID.randomUUID().toString()
        val productId = UUID.randomUUID().toString()
        val products = FakeCatalogRepository()
        val remote = FakeSaleRemote()
        val repository = ProductionSaleCheckoutRepository(remote, products)
        val session = session(UserRole.SALESMAN, shopId)
        val negotiated = saleDraft(productId, pricePaisa = 125_00, discountPaisa = 0)

        val success = repository.post(session, UUID.randomUUID().toString(), negotiated)
        val denied = repository.post(
            session,
            UUID.randomUUID().toString(),
            negotiated.copy(saleDiscountPaisa = 1_00),
        )

        assertIs<SaleResult.Success<PostedSale>>(success)
        assertEquals(125_00, remote.lastDraft?.lines?.single()?.effectiveUnitPricePaisa)
        assertIs<SaleResult.Failure>(denied)
        assertEquals(1, remote.postCount)
    }

    @Test
    fun incompletePriceAndExcessiveDiscountRemainInvalid() {
        val productId = UUID.randomUUID().toString()

        assertNull(
            calculateSaleSubtotal(
                listOf(SaleLineDraft(productId, "Travel Bag", 1, effectiveUnitPricePaisa = null)),
            ),
        )
        assertNull(calculateSaleTotal(subtotalPaisa = 100_00, discountPaisa = 101_00))
    }

    private fun session(role: UserRole, shopId: String) = UserSession(
        userId = UUID.randomUUID().toString(),
        displayName = role.name,
        role = role,
        shopId = shopId,
    )

    private fun saleDraft(productId: String, pricePaisa: Long, discountPaisa: Long) = SaleDraft(
        businessDate = "2026-09-08",
        lines = listOf(SaleLineDraft(productId, "Travel Bag", 1, pricePaisa)),
        saleDiscountPaisa = discountPaisa,
        payments = listOf(SalePaymentDraft(SalePaymentMethod.CASH, pricePaisa - discountPaisa)),
    )
}

private class FakeProductRemote(
    private val snapshot: ProductRemoteSnapshot,
) : ProductRemoteDataSource {
    var lastCanSeeCost: Boolean? = null
    var lastMutation: ProductMutation? = null

    override suspend fun load(owner: CacheOwner, canSeeCost: Boolean): RemoteResult<ProductRemoteSnapshot> {
        lastCanSeeCost = canSeeCost
        return RemoteResult.Success(snapshot)
    }

    override suspend fun mutate(
        owner: CacheOwner,
        requestId: String,
        mutation: ProductMutation,
        draft: ProductDraft,
    ): RemoteResult<Unit> {
        lastMutation = mutation
        return RemoteResult.Success(Unit)
    }
}

private class FakeSaleRemote : SaleRemoteDataSource {
    var lastDraft: SaleDraft? = null
    var postCount: Int = 0

    override suspend fun post(
        owner: CacheOwner,
        requestId: String,
        draft: SaleDraft,
    ): RemoteResult<PostedSale> {
        lastDraft = draft
        postCount += 1
        val total = draft.lines.sumOf { requireNotNull(it.effectiveUnitPricePaisa) * it.quantity }
        return RemoteResult.Success(PostedSale("sale", total, total, 0, 80_00, 1, 1))
    }
}

private class FakeCatalogRepository : ProductCatalogRepository {
    private val products = MutableStateFlow<List<CatalogProduct>>(emptyList())

    override fun observe(session: UserSession): Flow<List<CatalogProduct>> = products
    override suspend fun refresh(session: UserSession): ProductResult = ProductResult.Success("refreshed")
    override suspend fun mutate(
        session: UserSession,
        requestId: String,
        mutation: ProductMutation,
        draft: ProductDraft,
    ): ProductResult = ProductResult.Success("changed")
}
