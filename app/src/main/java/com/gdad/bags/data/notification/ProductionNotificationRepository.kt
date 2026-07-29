package com.gdad.bags.data.notification

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.EnqueueResult
import com.gdad.bags.data.local.MutationOutbox
import com.gdad.bags.data.local.OutboxOperation
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.notification.*
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ProductionNotificationRepository(
    private val remote: NotificationRemoteDataSource,
    private val store: NotificationStore,
    private val outbox: MutationOutbox,
) : NotificationRepository {
    override fun observe(session: UserSession): Flow<NotificationCenter> = store.observe(session.owner())

    override suspend fun refresh(session: UserSession): NotificationResult<Unit> {
        if (!session.allowed()) return denied()
        return when (val result = remote.load(session.owner())) {
            is RemoteResult.Failure -> result.error.failure("Unable to refresh notifications.")
            is RemoteResult.Success -> {
                if (session.role != UserRole.SUPER_ADMIN && result.value.any { it.shopId != session.shopId }) {
                    return NotificationResult.Failure(null, "Notification data did not match this shop.")
                }
                store.replace(session.owner(), result.value)
                NotificationResult.Success(Unit, "Notifications refreshed.")
            }
        }
    }

    override suspend fun markRead(
        session: UserSession,
        notificationId: String,
    ): NotificationResult<Unit> {
        if (!session.allowed()) return denied()
        if (!notificationId.isUuid()) {
            return NotificationResult.Failure(null, "The notification is not available.")
        }
        return when (store.markRead(session.owner(), notificationId)) {
            LocalMarkRead.NOT_FOUND -> NotificationResult.Failure(null, "The notification is not available.")
            LocalMarkRead.ALREADY_READ -> NotificationResult.Success(Unit, "Notification already read.")
            LocalMarkRead.MARKED -> when (
                outbox.enqueue(
                    owner = session.owner(),
                    operation = OutboxOperation.MARK_NOTIFICATION_READ,
                    payload = JsonObject(
                        mapOf("target_notification_id" to JsonPrimitive(notificationId)),
                    ),
                )
            ) {
                is EnqueueResult.Queued, is EnqueueResult.AlreadyQueued ->
                    NotificationResult.Success(Unit, "Notification marked read.")
                is EnqueueResult.Rejected -> NotificationResult.Failure(
                    null,
                    "Unable to save notification read state.",
                )
            }
        }
    }

    private fun RemoteFailure.failure(default: String) = NotificationResult.Failure(
        this,
        when (kind) {
            RemoteErrorKind.UNAUTHORIZED -> "Notifications are not available for this account."
            RemoteErrorKind.VALIDATION -> "The notification request is invalid."
            RemoteErrorKind.CONFLICT -> "Notification state changed. Refresh and review it."
            RemoteErrorKind.OFFLINE -> "You are offline. Saved notifications remain available."
            RemoteErrorKind.TIMEOUT -> "Notification refresh timed out. Retry when connected."
            RemoteErrorKind.RATE_LIMITED -> "Too many refresh attempts. Wait before retrying."
            RemoteErrorKind.UNKNOWN -> default
        },
    )

    private fun denied() = NotificationResult.Failure(
        null,
        "Notifications are not available for this account.",
    )

    private fun UserSession.allowed() = userId.isUuid() &&
        (role == UserRole.SUPER_ADMIN || shopId.isUuid())

    private fun UserSession.owner() = CacheOwner(userId, shopId)
    private fun String?.isUuid() = this != null && runCatching { UUID.fromString(this) }.isSuccess
}
