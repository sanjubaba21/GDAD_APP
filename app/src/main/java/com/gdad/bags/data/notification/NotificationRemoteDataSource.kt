package com.gdad.bags.data.notification

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteOperation
import com.gdad.bags.data.remote.RemoteQueryWindow
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.requireSupportedWindow
import com.gdad.bags.domain.notification.AppNotification
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface NotificationRemoteDataSource {
    suspend fun load(owner: CacheOwner): RemoteResult<List<AppNotification>>
}

class SupabaseNotificationRemoteDataSource(
    private val client: SupabaseClient,
    private val calls: RemoteCallExecutor,
) : NotificationRemoteDataSource {
    override suspend fun load(owner: CacheOwner) = calls.execute(
        RemoteOperation.LOAD_NOTIFICATIONS,
        requiresAuth = true,
    ) {
        val rows = client.from("notifications").select(
            Columns.raw("id,shop_id,category,title,body,record_type,record_id,created_at,expires_at"),
        ) {
            limit(RemoteQueryWindow.REQUEST_ROWS)
            order("created_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
        }.decodeList<NotificationRow>().requireSupportedWindow("notifications")
        val readIds = client.from("notification_reads").select(
            Columns.raw("notification_id"),
        ) {
            limit(RemoteQueryWindow.REQUEST_ROWS)
            order("notification_id", Order.ASCENDING)
            filter { eq("user_id", owner.userId) }
        }.decodeList<ReadRow>().requireSupportedWindow("notification reads")
            .mapTo(mutableSetOf()) { it.notificationId }
        rows.map { it.domain(it.id in readIds) }.sortedByDescending { it.createdAtEpochMillis }
    }
}

@Serializable
private data class NotificationRow(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    val category: String,
    val title: String,
    val body: String,
    @SerialName("record_type") val recordType: String,
    @SerialName("record_id") val recordId: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
) {
    fun domain(isRead: Boolean) = AppNotification(
        id = id,
        shopId = shopId,
        category = category,
        title = title,
        body = body,
        recordType = recordType,
        recordId = recordId,
        isRead = isRead,
        createdAtEpochMillis = Instant.parse(createdAt).toEpochMilli(),
        expiresAtEpochMillis = Instant.parse(expiresAt).toEpochMilli(),
    )
}

@Serializable
private data class ReadRow(@SerialName("notification_id") val notificationId: String)
