package com.gdad.bags.data.remote

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class RemoteOperation {
    PIN_LOGIN,
    LOAD_IDENTITY,
    AUTH_REFRESH,
    OUTBOX_MUTATION,
    LOAD_ACCOUNT_DIRECTORY,
    PROVISION_ACCOUNT,
    ADMINISTER_ACCOUNT,
    LOAD_PRODUCTS,
    MANAGE_PRODUCT,
    LOAD_PURCHASE_DIRECTORY,
    MANAGE_VENDOR,
    POST_PURCHASE_RECEIPT,
    LOAD_STOCK_HISTORY,
    POST_INVENTORY_ADJUSTMENT,
    POST_FIFO_SALE,
    LOAD_SALE_HISTORY,
    POST_SALE_RETURN,
    LOAD_VENDOR_LEDGER,
    POST_VENDOR_PAYMENT,
    POST_VENDOR_RETURN,
    REVERSE_VENDOR_EVENT,
    LOAD_FINANCE_LEDGER,
    POST_EXPENSE,
    POST_CASH_MOVEMENT,
    POST_ACCOUNT_TRANSFER,
    REVERSE_FINANCIAL_OPERATION,
    LOAD_DASHBOARD_REPORT,
    LOAD_BUSINESS_REPORT,
    LOAD_NOTIFICATIONS,
}

enum class RemoteErrorKind {
    VALIDATION,
    UNAUTHORIZED,
    CONFLICT,
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    UNKNOWN,
}

enum class RetryDisposition {
    NEVER,
    AFTER_AUTH_REFRESH,
    WITH_BACKOFF,
}

data class RemoteFailure(
    val kind: RemoteErrorKind,
    val retry: RetryDisposition,
)

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val error: RemoteFailure) : RemoteResult<Nothing>
}

/** Contains developer-safe metadata only. Exception and response messages are excluded. */
data class RemoteDiagnostic(
    val operation: RemoteOperation,
    val kind: RemoteErrorKind,
    val statusCode: Int?,
    val exceptionType: String,
)

fun interface RemoteDiagnosticSink {
    fun record(diagnostic: RemoteDiagnostic)

    companion object {
        val NONE = RemoteDiagnosticSink { }
    }
}

fun interface AuthSessionRefresher {
    suspend fun refresh()
}

internal class RemoteHttpException(val statusCode: Int) : Exception()

class RemoteCallExecutor(
    private val authSessionRefresher: AuthSessionRefresher,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val diagnostics: RemoteDiagnosticSink = RemoteDiagnosticSink.NONE,
) {
    init {
        require(timeoutMillis > 0)
    }

    suspend fun <T> execute(
        operation: RemoteOperation,
        requiresAuth: Boolean,
        block: suspend () -> T,
    ): RemoteResult<T> {
        val first = attempt(operation, block)
        if (
            !requiresAuth || first !is RemoteResult.Failure ||
            first.error.retry != RetryDisposition.AFTER_AUTH_REFRESH
        ) {
            return first
        }

        return when (val refresh = attempt(RemoteOperation.AUTH_REFRESH) {
            authSessionRefresher.refresh()
        }) {
            is RemoteResult.Failure -> refresh
            is RemoteResult.Success -> attempt(operation, block)
        }
    }

    private suspend fun <T> attempt(
        operation: RemoteOperation,
        block: suspend () -> T,
    ): RemoteResult<T> = try {
        RemoteResult.Success(withTimeout(timeoutMillis) { block() })
    } catch (timeout: TimeoutCancellationException) {
        failure(operation, timeout, RemoteErrorKind.TIMEOUT, null)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val status = error.statusCodeOrNull()
        failure(operation, error, classify(error, status), status)
    }

    private fun failure(
        operation: RemoteOperation,
        error: Throwable,
        kind: RemoteErrorKind,
        statusCode: Int?,
    ): RemoteResult.Failure {
        diagnostics.record(
            RemoteDiagnostic(
                operation = operation,
                kind = kind,
                statusCode = statusCode,
                exceptionType = error::class.simpleName ?: "Throwable",
            ),
        )
        return RemoteResult.Failure(
            RemoteFailure(
                kind = kind,
                retry = when (kind) {
                    RemoteErrorKind.UNAUTHORIZED -> RetryDisposition.AFTER_AUTH_REFRESH
                    RemoteErrorKind.OFFLINE,
                    RemoteErrorKind.TIMEOUT,
                    RemoteErrorKind.RATE_LIMITED,
                    -> RetryDisposition.WITH_BACKOFF
                    RemoteErrorKind.VALIDATION,
                    RemoteErrorKind.CONFLICT,
                    RemoteErrorKind.UNKNOWN,
                    -> RetryDisposition.NEVER
                },
            ),
        )
    }

    private fun classify(error: Throwable, statusCode: Int?): RemoteErrorKind {
        val databaseKind = error.postgrestCodeOrNull()?.let { code ->
            when (code) {
                "22003", "22023", "23514" -> RemoteErrorKind.VALIDATION
                "23505", "55000" -> RemoteErrorKind.CONFLICT
                "42501" -> RemoteErrorKind.UNAUTHORIZED
                else -> null
            }
        }
        if (databaseKind != null) return databaseKind
        if (statusCode != null) {
            return when (statusCode) {
                400, 422 -> RemoteErrorKind.VALIDATION
                401, 403 -> RemoteErrorKind.UNAUTHORIZED
                409 -> RemoteErrorKind.CONFLICT
                429 -> RemoteErrorKind.RATE_LIMITED
                else -> RemoteErrorKind.UNKNOWN
            }
        }
        return when {
            error.hasCause<HttpRequestTimeoutException>() ||
                error.hasCause<ConnectTimeoutException>() ||
                error.hasCause<SocketTimeoutException>() -> RemoteErrorKind.TIMEOUT
            error.hasCause<UnknownHostException>() ||
                error.hasCause<ConnectException>() ||
                error.hasCause<NoRouteToHostException>() ||
                error.hasCause<IOException>() -> RemoteErrorKind.OFFLINE
            else -> RemoteErrorKind.UNKNOWN
        }
    }

    private fun Throwable.statusCodeOrNull(): Int? = generateSequence(this) { it.cause }
        .mapNotNull {
            when (it) {
                is RemoteHttpException -> it.statusCode
                is RestException -> it.statusCode
                else -> null
            }
        }
        .firstOrNull()

    private fun Throwable.postgrestCodeOrNull(): String? = generateSequence(this) { it.cause }
        .filterIsInstance<PostgrestRestException>()
        .map { it.code }
        .firstOrNull()

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is T }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
