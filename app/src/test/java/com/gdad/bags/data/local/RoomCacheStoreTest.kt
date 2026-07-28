package com.gdad.bags.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.RetryDisposition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomCacheStoreTest {
    private lateinit var database: RoomCacheDatabase
    private lateinit var store: RoomCacheStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RoomCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomCacheStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initialSchemaOpensAndExportsWithExplicitMigrationPolicy() = runBlocking {
        database.identityDao().get()

        assertTrue(database.isOpen)
        assertEquals(2, RoomCacheDatabase.VERSION)
        assertEquals(1, RoomCacheDatabase.MIGRATIONS.size)
    }

    @Test
    fun snapshotPublishesRoomBackedFlows() = runBlocking {
        val owner = CacheOwner(USER_A, SHOP_A)
        store.replaceSnapshot(
            CacheSnapshot(
                owner = owner,
                profile = profile(owner),
                products = listOf(product(owner, PRODUCT_A)),
                dashboard = dashboard(owner),
                notifications = listOf(notification(owner)),
            ),
        )

        assertEquals("Owner A", store.observeProfile(owner).first()?.displayName)
        assertEquals(listOf(PRODUCT_A), store.observeProducts(owner).first().map { it.id })
        assertEquals(1, store.observeDashboard(owner).first()?.lowStockCount)
        assertEquals(1, store.observeNotifications(owner).first().size)
    }

    @Test
    fun switchingUserOrTenantPurgesPreviousRowsBeforePublishingIdentity() = runBlocking {
        val firstOwner = CacheOwner(USER_A, SHOP_A)
        store.replaceSnapshot(
            CacheSnapshot(firstOwner, products = listOf(product(firstOwner, PRODUCT_A))),
        )

        store.activate(CacheOwner(USER_B, SHOP_B))

        assertTrue(store.observeProducts(firstOwner).first().isEmpty())
        assertEquals(USER_B, database.identityDao().get()?.userId)
        assertEquals(SHOP_B, database.identityDao().get()?.shopId)
    }

    @Test
    fun switchingShopForSameUserAlsoPurgesPreviousRows() = runBlocking {
        val firstOwner = CacheOwner(USER_A, SHOP_A)
        store.replaceSnapshot(
            CacheSnapshot(firstOwner, products = listOf(product(firstOwner, PRODUCT_A))),
        )

        store.activate(CacheOwner(USER_A, SHOP_B))

        assertTrue(store.observeProducts(firstOwner).first().isEmpty())
    }

    @Test
    fun missingIdentityMarkerPurgesOrphanRowsBeforeActivation() = runBlocking {
        val firstOwner = CacheOwner(USER_A, SHOP_A)
        store.replaceSnapshot(
            CacheSnapshot(firstOwner, products = listOf(product(firstOwner, PRODUCT_A))),
        )
        database.identityDao().clear()

        store.activate(firstOwner)

        assertTrue(store.observeProducts(firstOwner).first().isEmpty())
        assertEquals(USER_A, database.identityDao().get()?.userId)
    }

    @Test
    fun logoutPurgeRemovesRowsAndIdentity() = runBlocking {
        val owner = CacheOwner(USER_A, SHOP_A)
        store.replaceSnapshot(
            CacheSnapshot(owner, products = listOf(product(owner, PRODUCT_A))),
        )

        store.purge()

        assertTrue(store.observeProducts(owner).first().isEmpty())
        assertNull(database.identityDao().get())
    }

    @Test
    fun invalidMixedOwnerRefreshRollsBackEntireReplacement() = runBlocking {
        val owner = CacheOwner(USER_A, SHOP_A)
        store.replaceSnapshot(
            CacheSnapshot(owner, products = listOf(product(owner, PRODUCT_A))),
        )
        val foreignRow = product(CacheOwner(USER_B, SHOP_B), "foreign-product")

        try {
            store.replaceSnapshot(CacheSnapshot(owner, products = listOf(foreignRow)))
        } catch (_: IllegalArgumentException) {
            // Expected owner guard after transactional delete.
        }

        assertEquals(listOf(PRODUCT_A), store.observeProducts(owner).first().map { it.id })
    }

    @Test
    fun synchronizerPublishesSuccessAndRetainsLastGoodDataOnRemoteFailure() = runBlocking {
        val owner = CacheOwner(USER_A, SHOP_A)
        val firstSnapshot = CacheSnapshot(
            owner,
            products = listOf(product(owner, PRODUCT_A)),
        )
        var remote: RemoteResult<CacheSnapshot> = RemoteResult.Success(firstSnapshot)
        val synchronizer = CacheSynchronizer(CacheSnapshotSource { remote }, store)

        assertEquals(CacheSyncResult.Refreshed, synchronizer.refresh(owner))
        remote = RemoteResult.Failure(
            RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
        )

        val failed = synchronizer.refresh(owner) as CacheSyncResult.Failed

        assertEquals(RemoteErrorKind.OFFLINE, failed.error.kind)
        assertEquals(listOf(PRODUCT_A), store.observeProducts(owner).first().map { it.id })
    }

    private fun profile(owner: CacheOwner) = CachedProfileEntity(
        ownerUserId = owner.userId,
        ownerTenantKey = owner.tenantKey,
        displayName = "Owner A",
        role = "owner",
        disabled = false,
        refreshedAtEpochMillis = 1_000,
    )

    private fun product(owner: CacheOwner, id: String) = CachedProductEntity(
        ownerUserId = owner.userId,
        ownerTenantKey = owner.tenantKey,
        id = id,
        name = "Travel Bag",
        sku = "BAG-001",
        barcode = null,
        sellingPricePaisa = 250_000,
        active = true,
        updatedAtEpochMillis = 1_000,
    )

    private fun dashboard(owner: CacheOwner) = CachedDashboardSummaryEntity(
        ownerUserId = owner.userId,
        ownerTenantKey = owner.tenantKey,
        salesPaisa = 500_000,
        profitPaisa = 100_000,
        receivablesPaisa = 50_000,
        vendorDuePaisa = 75_000,
        cashBankPaisa = 400_000,
        lowStockCount = 1,
        generatedAtEpochMillis = 1_000,
    )

    private fun notification(owner: CacheOwner) = CachedNotificationEntity(
        ownerUserId = owner.userId,
        ownerTenantKey = owner.tenantKey,
        id = "notification-a",
        category = "low_stock",
        title = "Low stock",
        body = "Travel Bag is low",
        isRead = false,
        createdAtEpochMillis = 1_000,
        expiresAtEpochMillis = 2_000,
    )

    private companion object {
        const val USER_A = "10000000-0000-4000-8000-000000000001"
        const val USER_B = "10000000-0000-4000-8000-000000000002"
        const val SHOP_A = "20000000-0000-4000-8000-000000000001"
        const val SHOP_B = "20000000-0000-4000-8000-000000000002"
        const val PRODUCT_A = "30000000-0000-4000-8000-000000000001"
    }
}
