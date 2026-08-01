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
        assertEquals(6, RoomCacheDatabase.VERSION)
        assertEquals(5, RoomCacheDatabase.MIGRATIONS.size)
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
    fun productObserverUsesOwnerIndexAndStopsAtSupportedWindow() = runBlocking {
        val owner = CacheOwner(USER_A, SHOP_A)
        val rows = List(CacheQueryWindow.MAX_ROWS + 1) { index ->
            product(owner, "product-${index.toString().padStart(3, '0')}").copy(
                name = "Product ${index.toString().padStart(3, '0')}",
                sku = "SKU-${index.toString().padStart(3, '0')}",
            )
        }
        database.writeDao().putProducts(rows)

        val observed = store.observeProducts(owner).first()
        val queryPlan = buildList {
            database.openHelper.readableDatabase.query(
                "EXPLAIN QUERY PLAN SELECT * FROM cached_products " +
                    "WHERE owner_user_id = ? AND owner_tenant_key = ? " +
                    "ORDER BY name COLLATE NOCASE, id LIMIT ?",
                arrayOf<Any?>(owner.userId, owner.tenantKey, CacheQueryWindow.MAX_ROWS),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("detail")))
                }
            }
        }

        assertEquals(CacheQueryWindow.MAX_ROWS, observed.size)
        assertEquals("product-000", observed.first().id)
        assertEquals("product-499", observed.last().id)
        assertTrue(queryPlan.any { "USING INDEX" in it })
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
        lowStockThreshold = 2,
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
        shopId = SHOP_A,
        category = "low_stock",
        title = "Low stock",
        body = "Travel Bag is low",
        recordType = "product",
        recordId = PRODUCT_A,
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
