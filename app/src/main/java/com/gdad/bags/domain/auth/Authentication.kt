package com.gdad.bags.domain.auth

import com.gdad.bags.domain.model.UserSession

sealed interface LoginResult {
    data class Success(val session: UserSession) : LoginResult
    data class Failure(val message: String) : LoginResult
}

interface AuthRepository {
    suspend fun login(userId: String, pin: String): LoginResult
}

fun interface AuthenticateUser {
    suspend operator fun invoke(userId: String, pin: String): LoginResult
}

class LoginUseCase(private val authRepository: AuthRepository) : AuthenticateUser {
    override suspend fun invoke(userId: String, pin: String): LoginResult =
        authRepository.login(userId, pin)
}
