package com.gdad.bags.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

enum class OutboxOperation(val canQueueOffline: Boolean) {
    MANAGE_PRODUCT(true),
    MARK_NOTIFICATION_READ(true),
    FIFO_SALE(false),
    PURCHASE_RECEIPT(false),
    SALE_RETURN(false),
    INVENTORY_ADJUSTMENT(false),
    VENDOR_PAYMENT(false),
    VENDOR_RETURN(false),
    FINANCIAL_ENTRY(false),
    ACCOUNT_ADMINISTRATION(false),
}

enum class OutboxState { PENDING, RETRY_WAIT, IN_FLIGHT, PERMANENT_FAILURE }

@Entity(
    tableName = "mutation_outbox",
    primaryKeys = ["idempotency_key"],
    indices = [
        Index(value = ["owner_user_id", "owner_tenant_key", "state", "next_attempt_at_epoch_ms"]),
        Index(value = ["state", "updated_at_epoch_ms"]),
    ],
)
data class OutboxEntity(
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    val operation: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    val state: String = OutboxState.PENDING.name,
    @ColumnInfo(name = "next_attempt_at_epoch_ms") val nextAttemptAtEpochMillis: Long = 0,
    @ColumnInfo(name = "last_error_kind") val lastErrorKind: String? = null,
) : OwnedCacheRow
