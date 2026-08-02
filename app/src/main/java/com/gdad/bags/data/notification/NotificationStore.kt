package com.gdad.bags.data.notification

import androidx.room.withTransaction
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedNotificationEntity
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.domain.notification.AppNotification
import com.gdad.bags.domain.notification.NotificationCenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LocalMarkRead { MARKED, ALREADY_READ, NOT_FOUND }

interface NotificationStore {
    fun observe(owner: CacheOwner): Flow<NotificationCenter>
    suspend fun replace(owner: CacheOwner, values: List<AppNotification>)
    suspend fun markRead(owner: CacheOwner, notificationId: String): LocalMarkRead
}

class RoomNotificationStore(
    private val database: RoomCacheDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : NotificationStore {
    override fun observe(owner: CacheOwner): Flow<NotificationCenter> =
        database.readDao().observeNotifications(owner.userId, owner.tenantKey).map { rows ->
            NotificationCenter(
                rows.asSequence()
                    .filter { it.expiresAtEpochMillis > now() }
                    .map { it.domain() }
                    .toList(),
            )
        }

    override suspend fun replace(owner: CacheOwner, values: List<AppNotification>) =
        database.withTransaction {
            val identity = database.identityDao().get()
            require(identity?.userId == owner.userId && identity.tenantKey == owner.tenantKey)
            val locallyRead = database.readDao()
                .listNotifications(owner.userId, owner.tenantKey)
                .filter { it.isRead }
                .mapTo(mutableSetOf()) { it.id }
            val current = values.filter { it.expiresAtEpochMillis > now() }.map {
                it.copy(isRead = it.isRead || it.id in locallyRead).entity(owner)
            }
            database.writeDao().clearNotifications()
            database.writeDao().putNotifications(current)
        }

    override suspend fun markRead(owner: CacheOwner, notificationId: String): LocalMarkRead =
        database.withTransaction {
            val item = database.readDao().getNotification(owner.userId, owner.tenantKey, notificationId)
                ?: return@withTransaction LocalMarkRead.NOT_FOUND
            if (item.expiresAtEpochMillis <= now()) return@withTransaction LocalMarkRead.NOT_FOUND
            if (item.isRead) return@withTransaction LocalMarkRead.ALREADY_READ
            if (database.writeDao().markNotificationRead(owner.userId, owner.tenantKey, notificationId) == 1) {
                LocalMarkRead.MARKED
            } else {
                LocalMarkRead.ALREADY_READ
            }
        }

    private fun CachedNotificationEntity.domain() = AppNotification(
        id,
        shopId,
        category,
        title,
        body,
        recordType,
        recordId,
        isRead,
        createdAtEpochMillis,
        expiresAtEpochMillis,
    )

    private fun AppNotification.entity(owner: CacheOwner) = CachedNotificationEntity(
        ownerUserId = owner.userId,
        ownerTenantKey = owner.tenantKey,
        id = id,
        shopId = shopId,
        category = category,
        title = title,
        body = body,
        recordType = recordType,
        recordId = recordId,
        isRead = isRead,
        createdAtEpochMillis = createdAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )
}
