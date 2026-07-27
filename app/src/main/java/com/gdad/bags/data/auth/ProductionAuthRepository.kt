package com.gdad.bags.data.auth

import com.gdad.bags.domain.auth.AuthRepository
import com.gdad.bags.domain.auth.LoginResult
import com.gdad.bags.domain.auth.SessionRestoreResult
import com.gdad.bags.domain.model.UserSession
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

enum class PinLoginFailure {
    INVALID_REQUEST,
    INVALID_CREDENTIALS,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
}

sealed interface PinLoginRemoteResult {
    data class Success(val tokens: PinLoginTokens) : PinLoginRemoteResult
    data class Failure(val reason: PinLoginFailure) : PinLoginRemoteResult
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
    suspend fun load(subject: String): UserSession
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
                return@withLock LoginResult.Failure("Enter a valid user ID")
            }
            if (!PIN.matches(pin)) {
                return@withLock LoginResult.Failure("PIN must contain 6 to 8 digits")
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
                PinLoginRemoteResult.Failure(PinLoginFailure.SERVICE_UNAVAILABLE)
            }
            when (remote) {
                is PinLoginRemoteResult.Failure -> LoginResult.Failure(remote.safeMessage())
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
            SessionRestoreResult.Authenticated(identity.load(subject))
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
            LoginResult.Success(identity.load(subject))
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

    private fun PinLoginRemoteResult.Failure.safeMessage(): String = when (reason) {
        PinLoginFailure.INVALID_REQUEST -> "Check your user ID and PIN"
        PinLoginFailure.INVALID_CREDENTIALS -> "Incorrect user ID or PIN"
        PinLoginFailure.RATE_LIMITED -> "Too many attempts. Try again later."
        PinLoginFailure.SERVICE_UNAVAILABLE ->
            "Unable to sign in. Check your connection and try again."
    }

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
