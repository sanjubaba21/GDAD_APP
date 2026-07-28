package com.gdad.bags.data.purchase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedAccountEntity
import com.gdad.bags.data.local.CachedVendorEntity
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.data.local.RoomCacheStore
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
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.PurchaseLineDraft
import com.gdad.bags.domain.purchase.PurchasePaymentMethod
import com.gdad.bags.domain.purchase.PurchaseResult
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class ProductionPurchaseManagementRepositoryTest {
    private lateinit var database: RoomCacheDatabase
    private lateinit var remote: FakeRemote
    private lateinit var products: FakeProducts
    private lateinit var repository: ProductionPurchaseManagementRepository
    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RoomCacheDatabase::class.java).allowMainThreadQueries().build()
        RoomCacheStore(database).activate(CacheOwner(OWNER.userId, OWNER.shopId))
        remote = FakeRemote(); products = FakeProducts()
        repository = ProductionPurchaseManagementRepository(remote, PurchaseDirectoryStore(database), products)
    }
    @After fun tearDown() = database.close()

    @Test fun refreshPublishesOwnerScopedVendorsAndBalances() = runBlocking {
        assertTrue(repository.refresh(OWNER) is PurchaseResult.Success)
        val directory = repository.observe(OWNER).first()
        assertEquals(25000L, directory.vendors.single().duePaisa)
        assertEquals(900000L, directory.accounts.single().balancePaisa)
    }

    @Test fun postedPurchaseReturnsServerTotalsAndRefreshesStockBalances() = runBlocking {
        val result = repository.postPurchase(OWNER, REQUEST, DRAFT) as PurchaseResult.Success
        assertEquals(POSTED, result.value)
        assertEquals(listOf(REQUEST), remote.purchaseIds)
        assertEquals(1, products.refreshes)
        assertTrue(remote.loads >= 1)
    }

    @Test fun offlinePurchaseIsNotQueuedAndExactKeyCanRetry() = runBlocking {
        remote.postResults += RemoteResult.Failure(RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF))
        remote.postResults += RemoteResult.Success(POSTED)
        assertTrue(repository.postPurchase(OWNER, REQUEST, DRAFT) is PurchaseResult.Failure)
        assertTrue(repository.postPurchase(OWNER, REQUEST, DRAFT) is PurchaseResult.Success)
        assertEquals(listOf(REQUEST, REQUEST), remote.purchaseIds)
    }

    @Test fun nonOwnerAndInvalidPaymentNeverReachRemote() = runBlocking {
        repository.postPurchase(OWNER.copy(role = UserRole.SALESMAN), REQUEST, DRAFT)
        repository.postPurchase(OWNER, REQUEST, DRAFT.copy(paymentAmountPaisa = 9999999))
        assertTrue(remote.purchaseIds.isEmpty())
    }

    private class FakeRemote : PurchaseRemoteDataSource {
        var loads = 0; val purchaseIds = mutableListOf<String>(); val postResults = ArrayDeque<RemoteResult<PostedPurchase>>()
        override suspend fun load(owner: CacheOwner): RemoteResult<PurchaseRemoteSnapshot> { loads++; return RemoteResult.Success(PurchaseRemoteSnapshot(
            listOf(CachedVendorEntity(owner.userId, owner.tenantKey, VENDOR, "Vendor", "9800", "PAN", "Notes", 25000, true)),
            listOf(CachedAccountEntity(owner.userId, owner.tenantKey, ACCOUNT, ACCOUNT, "Cash", "cash", 900000, true)),
        )) }
        override suspend fun manageVendor(owner: CacheOwner, requestId: String, mutation: VendorMutation, draft: VendorDraft) = RemoteResult.Success(Unit)
        override suspend fun postPurchase(owner: CacheOwner, requestId: String, draft: PurchaseDraft): RemoteResult<PostedPurchase> { purchaseIds += requestId; return postResults.removeFirstOrNull() ?: RemoteResult.Success(POSTED) }
    }
    private class FakeProducts : ProductCatalogRepository {
        var refreshes = 0
        override fun observe(session: UserSession): Flow<List<CatalogProduct>> = flowOf(emptyList())
        override suspend fun refresh(session: UserSession): ProductResult { refreshes++; return ProductResult.Success("ok") }
        override suspend fun mutate(session: UserSession, requestId: String, mutation: ProductMutation, draft: ProductDraft) = ProductResult.Success("ok")
    }
    companion object {
        const val SHOP="11111111-1111-4111-8111-111111111111"; const val VENDOR="22222222-2222-4222-8222-222222222222"; const val PRODUCT="33333333-3333-4333-8333-333333333333"; const val ACCOUNT="44444444-4444-4444-8444-444444444444"; const val REQUEST="55555555-5555-4555-8555-555555555555"
        val OWNER=UserSession("66666666-6666-4666-8666-666666666666","Owner",UserRole.OWNER,SHOP)
        val DRAFT=PurchaseDraft(VENDOR,"INV-1","2026-07-28",listOf(PurchaseLineDraft(PRODUCT,"Bag",2,50000)),50000,PurchasePaymentMethod.CASH)
        val POSTED=PostedPurchase("77777777-7777-4777-8777-777777777777","88888888-8888-4888-8888-888888888888",ACCOUNT,100000,50000,50000,1)
    }
}
