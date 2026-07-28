package com.gdad.bags.data.auth

import com.gdad.bags.domain.auth.AuthRepository
import com.gdad.bags.domain.auth.LoginResult
import com.gdad.bags.domain.auth.OperationErrorKind
import com.gdad.bags.domain.auth.SessionRestoreResult
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.RetryDisposition
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PinLoginTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val tokenType: String,
)

sealed interface PinLoginRemoteResult {
    data class Success(val tokens: PinLoginTokens) : PinLoginRemoteResult
    data class Failure(val error: RemoteFailure) : PinLoginRemoteResult
}

fun interface PinLoginRemoteDataSource {
    suspend fun login(
        loginId: String,
        pin: String,
        requestId: String,
        installationId: String,
    ): PinLoginRemoteResult
}

interface AuthSessionDataSource {
    suspend fun importSession(tokens: PinLoginTokens): String
    suspend fun restoreSubjectOrNull(): String?
    suspend fun logoutAndClear()
    suspend fun clearLocalSession()
}

fun interface AuthoritativeIdentityDataSource {
    suspend fun load(subject: String): RemoteResult<UserSession>
}

class ProductionAuthRepository(
    private val pinLogin: PinLoginRemoteDataSource,
    private val authSession: AuthSessionDataSource,
    private val identity: AuthoritativeIdentityDataSource,
    private val installationIdProvider: InstallationIdProvider,
) : AuthRepository {
    private val operationMutex = Mutex()

    override suspend fun login(userId: String, pin: String): LoginResult =
        operationMutex.withLock {
            val normalizedLoginId = userId.trim().lowercase()
            if (!LOGIN_ID.matches(normalizedLoginId)) {
                return@withLock LoginResult.Failure(
                    "Enter a valid user ID",
                    OperationErrorKind.VALIDATION,
                )
            }
            if (!PIN.matches(pin)) {
                return@withLock LoginResult.Failure(
                    "PIN must contain 6 to 8 digits",
                    OperationErrorKind.VALIDATION,
                )
            }

            val remote = try {
                pinLogin.login(
                    loginId = normalizedLoginId,
                    pin = pin,
                    requestId = UUID.randomUUID().toString(),
                    installationId = installationIdProvider.getInstallationId(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PinLoginRemoteResult.Failure(unknownRemoteFailure())
            }
            when (remote) {
                is PinLoginRemoteResult.Failure -> LoginResult.Failure(
                    message = remote.safeMessage(),
                    kind = remote.error.kind.toDomainKind(),
                )
                is PinLoginRemoteResult.Success -> establishAuthenticatedIdentity(remote.tokens)
            }
        }

    override suspend fun restoreSession(): SessionRestoreResult = operationMutex.withLock {
        val subject = try {
            authSession.restoreSubjectOrNull()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            clearLocalSessionSafely()
            return@withLock SessionRestoreResult.SignedOut(
                "Your session could not be verified. Sign in again.",
            )
        } ?: return@withLock SessionRestoreResult.SignedOut()

        try {
            when (val loaded = identity.load(subject)) {
                is RemoteResult.Success -> SessionRestoreResult.Authenticated(loaded.value)
                is RemoteResult.Failure -> {
                    clearLocalSessionSafely()
                    SessionRestoreResult.SignedOut(
                        message = loaded.error.sessionMessage(),
                        kind = loaded.error.kind.toDomainKind(),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            clearLocalSessionSafely()
            SessionRestoreResult.SignedOut("Your session expired. Sign in again.")
        }
    }

    override suspend fun logout() = operationMutex.withLock {
        try {
            authSession.logoutAndClear()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            clearLocalSessionSafely()
        }
    }

    private suspend fun establishAuthenticatedIdentity(tokens: PinLoginTokens): LoginResult {
        return try {
            val subject = authSession.importSession(tokens)
            when (val loaded = identity.load(subject)) {
                is RemoteResult.Success -> LoginResult.Success(loaded.value)
                is RemoteResult.Failure -> {
                    clearLocalSessionSafely()
                    LoginResult.Failure(
                        message = loaded.error.identityMessage(),
                        kind = loaded.error.kind.toDomainKind(),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            clearLocalSessionSafely()
            LoginResult.Failure("Unable to verify your account. Try again.")
        }
    }

    private suspend fun clearLocalSessionSafely() {
        try {
            authSession.clearLocalSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The UI is still signed out; the next restore also rejects unreadable state.
        }
    }

    private fun PinLoginRemoteResult.Failure.safeMessage(): String = when (error.kind) {
        RemoteErrorKind.VALIDATION -> "Check your user ID and PIN"
        RemoteErrorKind.UNAUTHORIZED -> "Incorrect user ID or PIN"
        RemoteErrorKind.CONFLICT -> "This sign-in request conflicts with another operation."
        RemoteErrorKind.OFFLINE -> "You appear to be offline. Check your connection."
        RemoteErrorKind.TIMEOUT -> "Sign-in timed out. Check your connection and try again."
        RemoteErrorKind.RATE_LIMITED -> "Too many attempts. Try again later."
        RemoteErrorKind.UNKNOWN -> "Unable to sign in. Check your connection and try again."
    }

    private fun RemoteFailure.identityMessage(): String = when (kind) {
        RemoteErrorKind.OFFLINE -> "You appear to be offline. Check your connection."
        RemoteErrorKind.TIMEOUT -> "Account verification timed out. Try again."
        else -> "Unable to verify your account. Try again."
    }

    private fun RemoteFailure.sessionMessage(): String = when (kind) {
        RemoteErrorKind.OFFLINE -> "Your session could not be verified while offline. Sign in again."
        RemoteErrorKind.TIMEOUT -> "Session verification timed out. Sign in again."
        else -> "Your session expired. Sign in again."
    }

    private fun RemoteErrorKind.toDomainKind(): OperationErrorKind = when (this) {
        RemoteErrorKind.VALIDATION -> OperationErrorKind.VALIDATION
        RemoteErrorKind.UNAUTHORIZED -> OperationErrorKind.UNAUTHORIZED
        RemoteErrorKind.CONFLICT -> OperationErrorKind.CONFLICT
        RemoteErrorKind.OFFLINE -> OperationErrorKind.OFFLINE
        RemoteErrorKind.TIMEOUT -> OperationErrorKind.TIMEOUT
        RemoteErrorKind.RATE_LIMITED -> OperationErrorKind.RATE_LIMITED
        RemoteErrorKind.UNKNOWN -> OperationErrorKind.UNKNOWN
    }

    private fun unknownRemoteFailure(): RemoteFailure = RemoteFailure(
        kind = RemoteErrorKind.UNKNOWN,
        retry = RetryDisposition.NEVER,
    )

    private companion object {
        val LOGIN_ID = Regex("^[a-z0-9][a-z0-9._-]{2,63}$")
        val PIN = Regex("^[0-9]{6,8}$")
    }
}

class UnconfiguredAuthRepository : AuthRepository {
    override suspend fun login(userId: String, pin: String): LoginResult =
        LoginResult.Failure("App setup is incomplete. Contact the administrator.")

    override suspend fun restoreSession(): SessionRestoreResult = SessionRestoreResult.SignedOut()

    override suspend fun logout() = Unit
}
