package com.gdad.bags.domain.auth

import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LoginUseCaseTest {
    @Test
    fun deterministicFakeRepositoryCanBeInjected() = runBlocking {
        val expectedSession = UserSession(
            userId = "owner.test",
            displayName = "Test Owner",
            role = UserRole.OWNER,
            shopId = "shop-test",
        )
        val fake = RecordingAuthRepository(LoginResult.Success(expectedSession))
        val useCase: AuthenticateUser = LoginUseCase(fake)

        val result = useCase(" owner.test ", "1234")

        assertEquals(" owner.test ", fake.lastUserId)
        assertEquals("1234", fake.lastPin)
        assertSame(expectedSession, (result as LoginResult.Success).session)
    }

    private class RecordingAuthRepository(
        private val result: LoginResult,
    ) : AuthRepository {
        var lastUserId: String? = null
        var lastPin: String? = null

        override suspend fun login(userId: String, pin: String): LoginResult {
            lastUserId = userId
            lastPin = pin
            return result
        }

        override suspend fun restoreSession(): SessionRestoreResult =
            SessionRestoreResult.SignedOut()

        override suspend fun logout() = Unit
    }
}
