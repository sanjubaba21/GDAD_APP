package com.gdad.bags.desktop

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedProductEntity
import com.gdad.bags.data.local.CachedStockSummaryEntity
import com.gdad.bags.data.local.CachedAccountEntity
import com.gdad.bags.data.local.CachedVendorEntity
import com.gdad.bags.data.product.ProductRemoteDataSource
import com.gdad.bags.data.product.ProductRemoteSnapshot
import com.gdad.bags.data.purchase.PurchaseRemoteDataSource
import com.gdad.bags.data.purchase.PurchaseRemoteSnapshot
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
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.PurchaseLineDraft
import com.gdad.bags.domain.purchase.PurchasePaymentMethod
import com.gdad.bags.domain.purchase.PurchaseResult
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
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

    @Test
    fun purchaseRefreshMapsVendorDuesAndAccounts() = runBlocking {
        val shopId = UUID.randomUUID().toString()
        val vendorId = UUID.randomUUID().toString()
        val accountId = UUID.randomUUID().toString()
        val remote = FakePurchaseRemote(
            PurchaseRemoteSnapshot(
                vendors = listOf(
                    CachedVendorEntity(
                        "owner", shopId, vendorId, "Bag Supplier", "9800000000", null, null,
                        5_000_00, true,
                    ),
                ),
                accounts = listOf(
                    CachedAccountEntity(
                        "owner", shopId, accountId, accountId, "Main Cash", "cash", 12_000_00, true,
                    ),
                ),
            ),
        )
        val repository = DesktopPurchaseManagementRepository(remote, FakeCatalogRepository())

        assertIs<PurchaseResult.Success<Unit>>(repository.refresh(session(UserRole.OWNER, shopId)))
        val directory = repository.snapshot()

        assertEquals(5_000_00, directory.vendors.single().duePaisa)
        assertEquals(12_000_00, directory.accounts.single().balancePaisa)
    }

    @Test
    fun ownerPostsPurchaseAndRefreshesProducts() = runBlocking {
        val shopId = UUID.randomUUID().toString()
        val productId = UUID.randomUUID().toString()
        val vendorId = UUID.randomUUID().toString()
        val products = FakeCatalogRepository()
        val remote = FakePurchaseRemote()
        val repository = DesktopPurchaseManagementRepository(remote, products)
        val draft = PurchaseDraft(
            vendorId = vendorId,
            invoiceReference = "INV-10",
            businessDate = "2026-09-10",
            lines = listOf(PurchaseLineDraft(productId, "Travel Bag", 2, 400_00)),
            paymentAmountPaisa = 300_00,
            paymentMethod = PurchasePaymentMethod.CASH,
        )

        val result = repository.postPurchase(
            session(UserRole.OWNER, shopId),
            UUID.randomUUID().toString(),
            draft,
        )

        assertIs<PurchaseResult.Success<PostedPurchase>>(result)
        assertEquals(draft, remote.lastPurchase)
        assertEquals(1, remote.postCount)
        assertEquals(1, products.refreshCount)
    }

    @Test
    fun salesmanAndOverpaymentCannotReachPurchaseRpc() = runBlocking {
        val shopId = UUID.randomUUID().toString()
        val productId = UUID.randomUUID().toString()
        val vendorId = UUID.randomUUID().toString()
        val remote = FakePurchaseRemote()
        val repository = DesktopPurchaseManagementRepository(remote, FakeCatalogRepository())
        val draft = PurchaseDraft(
            vendorId,
            null,
            "2026-09-10",
            listOf(PurchaseLineDraft(productId, "Travel Bag", 1, 400_00)),
            401_00,
            PurchasePaymentMethod.CASH,
        )

        val salesman = repository.postPurchase(
            session(UserRole.SALESMAN, shopId),
            UUID.randomUUID().toString(),
            draft.copy(paymentAmountPaisa = 400_00),
        )
        val overpaid = repository.postPurchase(
            session(UserRole.OWNER, shopId),
            UUID.randomUUID().toString(),
            draft,
        )

        assertIs<PurchaseResult.Failure>(salesman)
        assertIs<PurchaseResult.Failure>(overpaid)
        assertEquals(0, remote.postCount)
    }

    @Test
    fun ownerVendorCreateUsesAuditedRpcAndRefreshesDirectory() = runBlocking {
        val remote = FakePurchaseRemote()
        val repository = DesktopPurchaseManagementRepository(remote, FakeCatalogRepository())

        val result = repository.manageVendor(
            session(UserRole.OWNER, UUID.randomUUID().toString()),
            UUID.randomUUID().toString(),
            VendorMutation.CREATE,
            VendorDraft(null, "Bag Supplier", null, null, null),
        )

        assertIs<PurchaseResult.Success<Unit>>(result)
        assertEquals(VendorMutation.CREATE, remote.lastVendorMutation)
        assertEquals(1, remote.loadCount)
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
    var refreshCount: Int = 0

    override fun observe(session: UserSession): Flow<List<CatalogProduct>> = products
    override suspend fun refresh(session: UserSession): ProductResult {
        refreshCount += 1
        return ProductResult.Success("refreshed")
    }
    override suspend fun mutate(
        session: UserSession,
        requestId: String,
        mutation: ProductMutation,
        draft: ProductDraft,
    ): ProductResult = ProductResult.Success("changed")
}

private class FakePurchaseRemote(
    private val snapshot: PurchaseRemoteSnapshot = PurchaseRemoteSnapshot(emptyList(), emptyList()),
) : PurchaseRemoteDataSource {
    var loadCount: Int = 0
    var postCount: Int = 0
    var lastPurchase: PurchaseDraft? = null
    var lastVendorMutation: VendorMutation? = null

    override suspend fun load(owner: CacheOwner): RemoteResult<PurchaseRemoteSnapshot> {
        loadCount += 1
        return RemoteResult.Success(snapshot)
    }

    override suspend fun manageVendor(
        owner: CacheOwner,
        requestId: String,
        mutation: VendorMutation,
        draft: VendorDraft,
    ): RemoteResult<Unit> {
        lastVendorMutation = mutation
        return RemoteResult.Success(Unit)
    }

    override suspend fun postPurchase(
        owner: CacheOwner,
        requestId: String,
        draft: PurchaseDraft,
    ): RemoteResult<PostedPurchase> {
        postCount += 1
        lastPurchase = draft
        return RemoteResult.Success(
            PostedPurchase(
                purchaseBillId = "bill",
                purchaseReceiptId = "receipt",
                vendorPaymentId = "payment",
                grandTotalPaisa = 800_00,
                paidPaisa = 300_00,
                duePaisa = 500_00,
                lineCount = 1,
            ),
        )
    }
}
