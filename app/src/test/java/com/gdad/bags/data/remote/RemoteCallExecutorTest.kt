package com.gdad.bags.data.remote

import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCallExecutorTest {
    @Test
    fun classifiesAllRequiredHttpFailures() = runBlocking {
        val cases = mapOf(
            400 to RemoteErrorKind.VALIDATION,
            401 to RemoteErrorKind.UNAUTHORIZED,
            409 to RemoteErrorKind.CONFLICT,
            500 to RemoteErrorKind.UNKNOWN,
        )

        cases.forEach { (status, expected) ->
            val result = executor().execute(RemoteOperation.PIN_LOGIN, requiresAuth = false) {
                throw RemoteHttpException(status)
            } as RemoteResult.Failure

            assertEquals(expected, result.error.kind)
        }
    }

    @Test
    fun distinguishesOfflineAndTimeoutFailures() = runBlocking {
        val offline = executor().execute(RemoteOperation.LOAD_IDENTITY, requiresAuth = false) {
            throw IllegalStateException("wrapper", UnknownHostException("unsafe host"))
        } as RemoteResult.Failure
        val timeout = executor(timeoutMillis = 1).execute(
            RemoteOperation.LOAD_IDENTITY,
            requiresAuth = false,
        ) {
            delay(50)
        } as RemoteResult.Failure

        assertEquals(RemoteErrorKind.OFFLINE, offline.error.kind)
        assertEquals(RetryDisposition.WITH_BACKOFF, offline.error.retry)
        assertEquals(RemoteErrorKind.TIMEOUT, timeout.error.kind)
        assertEquals(RetryDisposition.WITH_BACKOFF, timeout.error.retry)
    }

    @Test
    fun authenticatedUnauthorizedCallRefreshesAndRetriesExactlyOnce() = runBlocking {
        var refreshes = 0
        var attempts = 0
        val executor = RemoteCallExecutor(
            authSessionRefresher = AuthSessionRefresher { refreshes++ },
        )

        val result = executor.execute(RemoteOperation.LOAD_IDENTITY, requiresAuth = true) {
            attempts++
            if (attempts == 1) throw RemoteHttpException(401)
            "authoritative"
        } as RemoteResult.Success

        assertEquals("authoritative", result.value)
        assertEquals(1, refreshes)
        assertEquals(2, attempts)
    }

    @Test
    fun protectedCallRefreshesBeforeSendingAndUsesTheFreshSession() = runBlocking {
        val events = mutableListOf<String>()
        val result = RemoteCallExecutor(
            authSessionRefresher = AuthSessionRefresher { events += "refresh" },
        ).execute(
            operation = RemoteOperation.PROVISION_ACCOUNT,
            requiresAuth = true,
            refreshAuthBeforeAttempt = true,
        ) {
            events += "send"
            "accepted"
        } as RemoteResult.Success

        assertEquals("accepted", result.value)
        assertEquals(listOf("refresh", "send"), events)
    }

    @Test
    fun failedProtectedPreflightNeverSendsAndPreservesSafeStatus() = runBlocking {
        var sent = false
        val result = RemoteCallExecutor(
            authSessionRefresher = AuthSessionRefresher { throw RemoteHttpException(401) },
        ).execute(
            operation = RemoteOperation.PROVISION_ACCOUNT,
            requiresAuth = true,
            refreshAuthBeforeAttempt = true,
        ) {
            sent = true
        } as RemoteResult.Failure

        assertFalse(sent)
        assertEquals(RemoteErrorKind.UNAUTHORIZED, result.error.kind)
        assertEquals(401, result.error.statusCode)
    }

    @Test
    fun unauthenticatedUnauthorizedCallNeverRefreshes() = runBlocking {
        var refreshed = false
        val result = RemoteCallExecutor(
            authSessionRefresher = AuthSessionRefresher { refreshed = true },
        ).execute(RemoteOperation.PIN_LOGIN, requiresAuth = false) {
            throw RemoteHttpException(401)
        } as RemoteResult.Failure

        assertEquals(RemoteErrorKind.UNAUTHORIZED, result.error.kind)
        assertFalse(refreshed)
    }

    @Test
    fun diagnosticsExcludeRawExceptionMessages() = runBlocking {
        val diagnostics = mutableListOf<RemoteDiagnostic>()
        executor(diagnostics = RemoteDiagnosticSink(diagnostics::add)).execute(
            RemoteOperation.LOAD_IDENTITY,
            requiresAuth = false,
        ) {
            error("raw database secret must not escape")
        }

        assertEquals(1, diagnostics.size)
        assertEquals(RemoteErrorKind.UNKNOWN, diagnostics.single().kind)
        assertFalse(diagnostics.single().toString().contains("database secret"))
    }

    @Test
    fun callerCancellationIsNeverConvertedToRemoteFailure() = runBlocking {
        var cancelled = false
        try {
            executor().execute(RemoteOperation.LOAD_IDENTITY, requiresAuth = false) {
                throw CancellationException("caller stopped")
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    private fun executor(
        timeoutMillis: Long = 1_000,
        diagnostics: RemoteDiagnosticSink = RemoteDiagnosticSink.NONE,
    ) = RemoteCallExecutor(
        authSessionRefresher = AuthSessionRefresher { },
        timeoutMillis = timeoutMillis,
        diagnostics = diagnostics,
    )
}
