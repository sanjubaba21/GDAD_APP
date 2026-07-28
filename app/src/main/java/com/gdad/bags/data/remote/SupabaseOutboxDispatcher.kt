package com.gdad.bags.data.remote

import com.gdad.bags.data.local.OutboxEntity
import com.gdad.bags.data.local.OutboxOperation
import com.gdad.bags.data.local.OutboxRemoteDispatcher
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SupabaseOutboxDispatcher(
    private val client: SupabaseClient,
    private val remoteCalls: RemoteCallExecutor,
) : OutboxRemoteDispatcher {
    override suspend fun dispatch(item: OutboxEntity): RemoteResult<Unit> = remoteCalls.execute(
        operation = RemoteOperation.OUTBOX_MUTATION,
        requiresAuth = true,
    ) {
        val payload = Json.parseToJsonElement(item.payloadJson) as JsonObject
        when (OutboxOperation.valueOf(item.operation)) {
            OutboxOperation.MANAGE_PRODUCT -> client.postgrest.rpc(
                function = "manage_product",
                parameters = JsonObject(
                    payload + mapOf(
                        "p_idempotency_key" to JsonPrimitive(item.idempotencyKey),
                        "p_shop_id" to JsonPrimitive(item.ownerTenantKey),
                    ),
                ),
            )
            OutboxOperation.MARK_NOTIFICATION_READ -> client.postgrest.rpc(
                function = "mark_notification_read",
                parameters = payload,
            )
            else -> error("Online-only operation reached the mutation outbox")
        }
        Unit
    }
}
