package com.gdad.bags.data.auth

import com.gdad.bags.domain.auth.AuthRepository
import com.gdad.bags.domain.auth.LoginResult
import com.gdad.bags.domain.auth.SessionRestoreResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession

/** Development-only adapter. It stores no PIN and must not ship. */
class PreviewAuthRepository : AuthRepository {
    override suspend fun login(userId: String, pin: String): LoginResult {
        val normalized = userId.trim().lowercase()
        if (normalized.isBlank()) return LoginResult.Failure("Enter your user ID")
        if (pin.length !in 4..8 || pin.any { !it.isDigit() }) {
            return LoginResult.Failure("PIN must contain 4 to 8 digits")
        }
        val role = when {
            normalized.startsWith("admin") -> UserRole.SUPER_ADMIN
            normalized.startsWith("sales") -> UserRole.SALESMAN
            else -> UserRole.OWNER
        }
        return LoginResult.Success(
            UserSession(
                userId = normalized,
                displayName = normalized.replaceFirstChar(Char::uppercase),
                role = role,
                shopId = if (role == UserRole.SUPER_ADMIN) null else "gdad-bags",
            )
        )
    }

    override suspend fun restoreSession(): SessionRestoreResult = SessionRestoreResult.SignedOut()

    override suspend fun logout() = Unit
}
