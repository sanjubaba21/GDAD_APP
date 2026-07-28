package com.gdad.bags.data.product

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedProductEntity
import com.gdad.bags.data.local.CachedStockSummaryEntity
import com.gdad.bags.data.local.MutationOutbox
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.data.local.RoomCacheStore
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.product.ProductResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductionProductCatalogRepositoryTest {
    private lateinit var database: RoomCacheDatabase
    private lateinit var remote: FakeRemote
    private lateinit var repository: ProductionProductCatalogRepository

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RoomCacheDatabase::class.java).allowMainThreadQueries().build()
        remote = FakeRemote()
        repository = ProductionProductCatalogRepository(remote, ProductCatalogStore(database), MutationOutbox(database.outboxDao()))
        RoomCacheStore(database).activate(CacheOwner(OWNER.userId, OWNER.shopId))
    }

    @After fun tearDown() = database.close()

    @Test fun ownerRefreshPublishesCostAndArchivedHistory() = runBlocking {
        assertTrue(repository.refresh(OWNER) is ProductResult.Success)
        val products = repository.observe(OWNER).first()
        assertEquals(2, products.size)
        assertEquals(640000L, products.single { it.id == PRODUCT }.stockValuePaisa)
        assertTrue(!products.single { it.id == ARCHIVED }.active)
        assertEquals(listOf(true), remote.costVisibility)
    }

    @Test fun salesmanCannotMutateAndNeverReachesRemote() = runBlocking {
        val result = repository.mutate(OWNER.copy(role = UserRole.SALESMAN), REQUEST, ProductMutation.UPDATE, DRAFT)
        assertTrue(result is ProductResult.Failure)
        assertTrue(remote.requestIds.isEmpty())
    }

    @Test fun invalidOwnerDraftNeverReachesRemote() = runBlocking {
        val result = repository.mutate(OWNER, REQUEST, ProductMutation.UPDATE, DRAFT.copy(sku = ""))
        assertEquals("Review the product fields and reserved codes.", (result as ProductResult.Failure).safeMessage)
        assertTrue(remote.requestIds.isEmpty())
    }

    @Test fun transientMutationIsQueuedWithExactIdempotencyKey() = runBlocking {
        remote.mutationResult = RemoteResult.Failure(RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF))
        val result = repository.mutate(OWNER, REQUEST, ProductMutation.UPDATE, DRAFT)
        assertTrue(result is ProductResult.Success)
        assertNotNull(database.outboxDao().get(REQUEST))
        assertEquals(listOf(REQUEST), remote.requestIds)
    }

    @Test fun conflictProvidesActionableSafeMessage() = runBlocking {
        remote.mutationResult = RemoteResult.Failure(RemoteFailure(RemoteErrorKind.CONFLICT, RetryDisposition.NEVER))
        val result = repository.mutate(OWNER, REQUEST, ProductMutation.ARCHIVE, DRAFT)
        assertEquals(
            "The product changed or is used by an in-progress operation.",
            (result as ProductResult.Failure).safeMessage,
        )
    }

    private class FakeRemote : ProductRemoteDataSource {
        val costVisibility = mutableListOf<Boolean>()
        val requestIds = mutableListOf<String>()
        var mutationResult: RemoteResult<Unit> = RemoteResult.Success(Unit)
        override suspend fun load(owner: CacheOwner, canSeeCost: Boolean): RemoteResult<ProductRemoteSnapshot> {
            costVisibility += canSeeCost
            return RemoteResult.Success(snapshot(owner))
        }
        override suspend fun mutate(owner: CacheOwner, requestId: String, mutation: ProductMutation, draft: ProductDraft): RemoteResult<Unit> {
            requestIds += requestId
            return mutationResult
        }
    }

    private companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val PRODUCT = "22222222-2222-4222-8222-222222222222"
        const val ARCHIVED = "33333333-3333-4333-8333-333333333333"
        const val REQUEST = "44444444-4444-4444-8444-444444444444"
        val OWNER = UserSession("55555555-5555-4555-8555-555555555555", "Owner", UserRole.OWNER, SHOP)
        val DRAFT = ProductDraft(PRODUCT, "Travel Bag", "TB-1", "890123", 150000, 4)
        fun snapshot(owner: CacheOwner) = ProductRemoteSnapshot(
            listOf(
                CachedProductEntity(owner.userId, owner.tenantKey, PRODUCT, "Travel Bag", "TB-1", "890123", 150000, 4, true, 1),
                CachedProductEntity(owner.userId, owner.tenantKey, ARCHIVED, "Old Bag", "OLD-1", null, 50000, 0, false, 1),
            ),
            listOf(
                CachedStockSummaryEntity(owner.userId, owner.tenantKey, PRODUCT, 8, 640000, false, 1),
                CachedStockSummaryEntity(owner.userId, owner.tenantKey, ARCHIVED, 0, 0, true, 1),
            ),
        )
    }
}
