package com.gdad.bags.domain.notification

import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

data class AppNotification(
    val id: String,
    val shopId: String,
    val category: String,
    val title: String,
    val body: String,
    val recordType: String,
    val recordId: String?,
    val isRead: Boolean,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

data class NotificationCenter(val items: List<AppNotification> = emptyList()) {
    val unreadCount: Int get() = items.count { !it.isRead }
}

sealed interface NotificationResult<out T> {
    data class Success<T>(val value: T, val safeMessage: String) : NotificationResult<T>
    data class Failure(val error: RemoteFailure?, val safeMessage: String) : NotificationResult<Nothing>
}

interface NotificationRepository {
    fun observe(session: UserSession): Flow<NotificationCenter>
    suspend fun refresh(session: UserSession): NotificationResult<Unit>
    suspend fun markRead(session: UserSession, notificationId: String): NotificationResult<Unit>
}
