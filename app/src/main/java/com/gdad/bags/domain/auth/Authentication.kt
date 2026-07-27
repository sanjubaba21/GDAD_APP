package com.gdad.bags.domain.auth

import com.gdad.bags.domain.model.UserSession

sealed interface LoginResult {
    data class Success(val session: UserSession) : LoginResult
    data class Failure(val message: String) : LoginResult
}

sealed interface SessionRestoreResult {
    data class Authenticated(val session: UserSession) : SessionRestoreResult
    data class SignedOut(val message: String? = null) : SessionRestoreResult
}

interface AuthRepository {
    suspend fun login(userId: String, pin: String): LoginResult
    suspend fun restoreSession(): SessionRestoreResult
    suspend fun logout()
}

fun interface AuthenticateUser {
    suspend operator fun invoke(userId: String, pin: String): LoginResult
}

class LoginUseCase(private val authRepository: AuthRepository) : AuthenticateUser {
    override suspend fun invoke(userId: String, pin: String): LoginResult =
        authRepository.login(userId, pin)
}

fun interface RestoreSession {
    suspend operator fun invoke(): SessionRestoreResult
}

class RestoreSessionUseCase(private val authRepository: AuthRepository) : RestoreSession {
    override suspend fun invoke(): SessionRestoreResult = authRepository.restoreSession()
}

fun interface LogoutUser {
    suspend operator fun invoke()
}

class LogoutUseCase(private val authRepository: AuthRepository) : LogoutUser {
    override suspend fun invoke() = authRepository.logout()
}
