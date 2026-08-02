package com.gdad.bags.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.RetryDisposition
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class MutationOutboxTest {
    private lateinit var context: Context
    private lateinit var database: RoomCacheDatabase
    private val owner = CacheOwner("user-a", "shop-a")
    private var clock = 1_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DATABASE)
        database = openDatabase()
        runBlocking { RoomCacheStore(database).activate(owner) }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun confirmedQueueSurvivesDatabaseCloseAndDuplicateKeyDoesNotDuplicate() = runBlocking {
        val key = UUID.randomUUID().toString()
        val outbox = MutationOutbox(database.outboxDao(), { clock })
        val payload = buildJsonObject { put("p_action", "create") }

        assertTrue(outbox.enqueue(owner, OutboxOperation.MANAGE_PRODUCT, payload, key) is EnqueueResult.Queued)
        assertTrue(outbox.enqueue(owner, OutboxOperation.MANAGE_PRODUCT, payload, key) is EnqueueResult.AlreadyQueued)
        database.close()
        database = openDatabase()

        assertEquals(listOf(key), database.outboxDao().observe(owner.userId, owner.tenantKey).first().map { it.idempotencyKey })
    }

    @Test
    fun riskyAndSecretBearingMutationsAreRejectedBeforePersistence() = runBlocking {
        val outbox = MutationOutbox(database.outboxDao(), { clock })

        assertTrue(outbox.enqueue(owner, OutboxOperation.FIFO_SALE, buildJsonObject {}) is EnqueueResult.Rejected)
        assertTrue(outbox.enqueue(owner, OutboxOperation.MANAGE_PRODUCT, buildJsonObject { put("pin", "1234") }) is EnqueueResult.Rejected)
        assertTrue(database.outboxDao().observe(owner.userId, owner.tenantKey).first().isEmpty())
    }

    @Test
    fun transientRetryKeepsStableKeyAndSuccessRemovesExactlyOneMutation() = runBlocking {
        val key = UUID.randomUUID().toString()
        MutationOutbox(database.outboxDao(), { clock }).enqueue(
            owner, OutboxOperation.MANAGE_PRODUCT, buildJsonObject { put("p_action", "create") }, key,
        )
        val dispatched = mutableListOf<String>()
        var offline = true
        val processor = OutboxProcessor(database, { item ->
            dispatched += item.idempotencyKey
            if (offline) RemoteResult.Failure(RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF))
            else RemoteResult.Success(Unit)
        }, { clock })

        assertEquals(OutboxProcessResult.RetryScheduled, processor.processActive())
        assertEquals(key, database.outboxDao().get(key)?.idempotencyKey)
        clock += OutboxProcessor.BASE_BACKOFF_MILLIS
        offline = false
        assertEquals(OutboxProcessResult.Completed, processor.processActive())

        assertEquals(listOf(key, key), dispatched)
        assertNull(database.outboxDao().get(key))
    }

    @Test
    fun exponentialRetryStopsAtAttemptCapAndRequiresResolution() = runBlocking {
        val key = UUID.randomUUID().toString()
        MutationOutbox(database.outboxDao(), { clock }).enqueue(
            owner, OutboxOperation.MANAGE_PRODUCT, buildJsonObject { put("p_action", "update") }, key,
        )
        val processor = OutboxProcessor(database, {
            RemoteResult.Failure(RemoteFailure(RemoteErrorKind.TIMEOUT, RetryDisposition.WITH_BACKOFF))
        }, { clock })
        val expectedDelays = listOf(30_000L, 60_000L, 120_000L, 240_000L)

        expectedDelays.forEachIndexed { index, expectedDelay ->
            assertEquals(OutboxProcessResult.RetryScheduled, processor.processActive())
            val retained = requireNotNull(database.outboxDao().get(key))
            assertEquals(index + 1, retained.attemptCount)
            assertEquals(clock + expectedDelay, retained.nextAttemptAtEpochMillis)
            clock = retained.nextAttemptAtEpochMillis
        }
        assertEquals(
            OutboxProcessResult.NeedsResolution(RemoteErrorKind.TIMEOUT),
            processor.processActive(),
        )

        val retained = requireNotNull(database.outboxDao().get(key))
        assertEquals(OutboxProcessor.MAX_ATTEMPTS, retained.attemptCount)
        assertEquals(OutboxState.PERMANENT_FAILURE.name, retained.state)
        assertEquals(OutboxProcessResult.NoWork, processor.processActive())
    }

    @Test
    fun staleInFlightClaimIsRecoveredBeforeDispatch() = runBlocking {
        val key = UUID.randomUUID().toString()
        MutationOutbox(database.outboxDao(), { clock }).enqueue(
            owner, OutboxOperation.MARK_NOTIFICATION_READ, buildJsonObject {}, key,
        )
        assertEquals(key, database.outboxDao().claimNext(owner, clock)?.idempotencyKey)
        clock += OutboxProcessor.STALE_IN_FLIGHT_MILLIS + 1
        var calls = 0

        val result = OutboxProcessor(database, {
            calls += 1
            RemoteResult.Success(Unit)
        }, { clock }).processActive()

        assertEquals(OutboxProcessResult.Completed, result)
        assertEquals(1, calls)
        assertNull(database.outboxDao().get(key))
    }

    @Test
    fun validationStopsRetryAndSurfacesResolutionState() = runBlocking {
        val key = UUID.randomUUID().toString()
        MutationOutbox(database.outboxDao(), { clock }).enqueue(
            owner, OutboxOperation.MARK_NOTIFICATION_READ,
            buildJsonObject { put("target_notification_id", "notification-a") }, key,
        )
        val processor = OutboxProcessor(database, {
            RemoteResult.Failure(RemoteFailure(RemoteErrorKind.VALIDATION, RetryDisposition.NEVER))
        }, { clock })

        assertEquals(OutboxProcessResult.NeedsResolution(RemoteErrorKind.VALIDATION), processor.processActive())
        val retained = database.outboxDao().get(key)
        assertEquals(OutboxState.PERMANENT_FAILURE.name, retained?.state)
        assertEquals(RemoteErrorKind.VALIDATION.name, retained?.lastErrorKind)
    }

    @Test
    fun logoutAndIdentitySwitchPurgePreviousOwnersOutbox() = runBlocking {
        val key = UUID.randomUUID().toString()
        MutationOutbox(database.outboxDao(), { clock }).enqueue(
            owner, OutboxOperation.MARK_NOTIFICATION_READ, buildJsonObject {}, key,
        )

        RoomCacheStore(database).activate(CacheOwner("user-b", "shop-b"))

        assertNull(database.outboxDao().get(key))
        var calls = 0
        val result = OutboxProcessor(database, { calls += 1; RemoteResult.Success(Unit) }, { clock }).processActive()
        assertEquals(OutboxProcessResult.NoWork, result)
        assertEquals(0, calls)
    }

    @Test
    fun readSnapshotRefreshPreservesConfirmedMutation() = runBlocking {
        val key = UUID.randomUUID().toString()
        MutationOutbox(database.outboxDao(), { clock }).enqueue(
            owner, OutboxOperation.MARK_NOTIFICATION_READ, buildJsonObject {}, key,
        )

        RoomCacheStore(database).replaceSnapshot(CacheSnapshot(owner))

        assertEquals(key, database.outboxDao().get(key)?.idempotencyKey)
    }

    @Test
    fun workRequestRequiresNetworkAndExponentialBackoff() {
        val spec = OutboxWork.request().workSpec

        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(OutboxProcessor.BASE_BACKOFF_MILLIS, spec.backoffDelayDuration)
    }

    private fun openDatabase(): RoomCacheDatabase =
        Room.databaseBuilder(context, RoomCacheDatabase::class.java, TEST_DATABASE)
            .allowMainThreadQueries()
            .addMigrations(*RoomCacheDatabase.MIGRATIONS)
            .build()

    private companion object {
        const val TEST_DATABASE = "mutation-outbox-test.db"
    }
}
