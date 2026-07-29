package com.gdad.bags.data.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.MutationOutbox
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.data.local.RoomCacheStore
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.notification.AppNotification
import com.gdad.bags.domain.notification.NotificationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductionNotificationRepositoryTest {
    private lateinit var database: RoomCacheDatabase
    private lateinit var remote: Remote
    private lateinit var repository: ProductionNotificationRepository
    private var schedules = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            RoomCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        RoomCacheStore(database).activate(OWNER.owner())
        remote = Remote(listOf(active(), expired()))
        repository = ProductionNotificationRepository(
            remote,
            RoomNotificationStore(database) { NOW },
            MutationOutbox(database.outboxDao(), { NOW }) { schedules++ },
        )
    }

    @After fun tearDown() = database.close()

    @Test
    fun refreshCachesOnlyCurrentAuthorizedRows() = runBlocking {
        assertTrue(repository.refresh(OWNER) is NotificationResult.Success)
        val center = repository.observe(OWNER).first()
        assertEquals(listOf(NOTIFICATION), center.items.map { it.id })
        assertEquals(1, center.unreadCount)
    }

    @Test
    fun crossShopOwnerResponseFailsClosed() = runBlocking {
        remote.items = listOf(active().copy(shopId = OTHER_SHOP))
        assertTrue(repository.refresh(OWNER) is NotificationResult.Failure)
        assertTrue(repository.observe(OWNER).first().items.isEmpty())
    }

    @Test
    fun repeatedMarkReadIsImmediateAndQueuesExactlyOnce() = runBlocking {
        repository.refresh(OWNER)

        assertTrue(repository.markRead(OWNER, NOTIFICATION) is NotificationResult.Success)
        assertTrue(repository.markRead(OWNER, NOTIFICATION) is NotificationResult.Success)

        assertTrue(repository.observe(OWNER).first().items.single().isRead)
        val queued = database.outboxDao().observe(OWNER.userId, OWNER.owner().tenantKey).first()
        assertEquals(1, queued.size)
        assertEquals(1, schedules)

        remote.items = listOf(active(isRead = false))
        repository.refresh(OWNER)
        assertTrue(repository.observe(OWNER).first().items.single().isRead)
    }

    @Test
    fun missingNotificationCannotCreateReadOperation() = runBlocking {
        val result = repository.markRead(OWNER, MISSING)
        assertTrue(result is NotificationResult.Failure)
        assertTrue(database.outboxDao().observe(OWNER.userId, OWNER.owner().tenantKey).first().isEmpty())
    }

    private class Remote(var items: List<AppNotification>) : NotificationRemoteDataSource {
        override suspend fun load(owner: CacheOwner) = RemoteResult.Success(items)
    }

    companion object {
        const val NOW = 10_000L
        const val USER = "11111111-1111-4111-8111-111111111111"
        const val SHOP = "22222222-2222-4222-8222-222222222222"
        const val OTHER_SHOP = "33333333-3333-4333-8333-333333333333"
        const val NOTIFICATION = "44444444-4444-4444-8444-444444444444"
        const val MISSING = "55555555-5555-4555-8555-555555555555"
        const val PRODUCT = "66666666-6666-4666-8666-666666666666"
        val OWNER = UserSession(USER, "Owner", UserRole.OWNER, SHOP)

        fun active(isRead: Boolean = false) = AppNotification(
            NOTIFICATION,
            SHOP,
            "low_stock",
            "Low stock",
            "Travel Bag is low",
            "product",
            PRODUCT,
            isRead,
            1_000,
            20_000,
        )

        fun expired() = active().copy(
            id = "77777777-7777-4777-8777-777777777777",
            expiresAtEpochMillis = 9_000,
        )

        private fun UserSession.owner() = CacheOwner(userId, shopId)
    }
}
