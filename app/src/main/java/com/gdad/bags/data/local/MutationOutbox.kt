package com.gdad.bags.data.local

import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface EnqueueResult {
    data class Queued(val idempotencyKey: String) : EnqueueResult
    data class AlreadyQueued(val idempotencyKey: String) : EnqueueResult
    data class Rejected(val safeMessage: String) : EnqueueResult
}

class MutationOutbox(
    private val dao: OutboxDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val schedule: () -> Unit = {},
) {
    suspend fun enqueue(
        owner: CacheOwner,
        operation: OutboxOperation,
        payload: JsonObject,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): EnqueueResult {
        if (!operation.canQueueOffline) {
            return EnqueueResult.Rejected("This operation requires a live connection.")
        }
        if (runCatching { UUID.fromString(idempotencyKey) }.isFailure) {
            return EnqueueResult.Rejected("The request identifier is invalid.")
        }
        val encoded = payload.toString()
        if (encoded.toByteArray().size > MAX_PAYLOAD_BYTES || payload.containsSensitiveField()) {
            return EnqueueResult.Rejected("The offline request contains unsupported data.")
        }
        val timestamp = now()
        val inserted = dao.enqueue(
            OutboxEntity(
                idempotencyKey = idempotencyKey,
                ownerUserId = owner.userId,
                ownerTenantKey = owner.tenantKey,
                operation = operation.name,
                payloadJson = encoded,
                createdAtEpochMillis = timestamp,
                updatedAtEpochMillis = timestamp,
            ),
        )
        return if (inserted == -1L) {
            EnqueueResult.AlreadyQueued(idempotencyKey)
        } else {
            schedule()
            EnqueueResult.Queued(idempotencyKey)
        }
    }

    fun observe(owner: CacheOwner): Flow<List<OutboxEntity>> =
        dao.observe(owner.userId, owner.tenantKey)

    private fun JsonElement.containsSensitiveField(): Boolean = when (this) {
        is JsonObject -> entries.any { (key, value) ->
            key.lowercase() in SENSITIVE_FIELDS || value.containsSensitiveField()
        }
        is kotlinx.serialization.json.JsonArray -> any { it.containsSensitiveField() }
        else -> false
    }

    private companion object {
        const val MAX_PAYLOAD_BYTES = 64 * 1024
        val SENSITIVE_FIELDS = setOf(
            "pin", "password", "access_token", "refresh_token", "authorization",
            "apikey", "service" + "_role", "secret",
        )
    }
}

fun interface OutboxRemoteDispatcher {
    suspend fun dispatch(item: OutboxEntity): RemoteResult<Unit>
}

sealed interface OutboxProcessResult {
    data object NoWork : OutboxProcessResult
    data object Completed : OutboxProcessResult
    data object RetryScheduled : OutboxProcessResult
    data class NeedsResolution(val error: RemoteErrorKind) : OutboxProcessResult
}

class OutboxProcessor(
    private val database: RoomCacheDatabase,
    private val dispatcher: OutboxRemoteDispatcher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun processActive(): OutboxProcessResult {
        val timestamp = now()
        val dao = database.outboxDao()
        dao.recoverStale(timestamp, timestamp - STALE_IN_FLIGHT_MILLIS)
        val identity = database.identityDao().get() ?: return OutboxProcessResult.NoWork
        val owner = CacheOwner(identity.userId, identity.shopId)
        val item = dao.claimNext(owner, timestamp) ?: return OutboxProcessResult.NoWork

        // A logout or user switch after claim must never dispatch this record with a new session.
        val active = database.identityDao().get()
        if (active == null || active.userId != item.ownerUserId || active.tenantKey != item.ownerTenantKey) {
            dao.delete(item.idempotencyKey)
            return OutboxProcessResult.NoWork
        }

        return when (val result = dispatcher.dispatch(item)) {
            is RemoteResult.Success -> {
                dao.delete(item.idempotencyKey)
                OutboxProcessResult.Completed
            }
            is RemoteResult.Failure -> handleFailure(dao, item, result.error.kind, timestamp)
        }
    }

    private suspend fun handleFailure(
        dao: OutboxDao,
        item: OutboxEntity,
        kind: RemoteErrorKind,
        timestamp: Long,
    ): OutboxProcessResult {
        val terminal = kind in setOf(
            RemoteErrorKind.VALIDATION,
            RemoteErrorKind.CONFLICT,
            RemoteErrorKind.UNAUTHORIZED,
        ) || item.attemptCount >= MAX_ATTEMPTS
        if (terminal) {
            dao.failPermanently(item.idempotencyKey, timestamp, kind.name)
            return OutboxProcessResult.NeedsResolution(kind)
        }
        val exponent = (item.attemptCount - 1).coerceIn(0, 10)
        val delay = (BASE_BACKOFF_MILLIS shl exponent).coerceAtMost(MAX_BACKOFF_MILLIS)
        dao.retry(item.idempotencyKey, timestamp + delay, timestamp, kind.name)
        return OutboxProcessResult.RetryScheduled
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val BASE_BACKOFF_MILLIS = 30_000L
        const val MAX_BACKOFF_MILLIS = 6 * 60 * 60 * 1_000L
        const val STALE_IN_FLIGHT_MILLIS = 15 * 60 * 1_000L
    }
}
