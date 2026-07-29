package com.gdad.bags.ui.auth

import com.gdad.bags.domain.auth.AuthenticateUser
import com.gdad.bags.domain.auth.LoginResult
import com.gdad.bags.domain.auth.LogoutUser
import com.gdad.bags.domain.auth.RestoreSession
import com.gdad.bags.domain.auth.SessionRestoreResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test
    fun authoritativeSessionRestorePublishesAuthenticatedState() = runTest(dispatcher) {
        val viewModel = viewModel(restore = { SessionRestoreResult.Authenticated(SESSION) })

        assertTrue(viewModel.uiState.value.isInitializing)
        advanceUntilIdle()

        assertEquals(SESSION, viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isInitializing)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun loginIsBlockedUntilRestoreCompletesAndDuplicateSubmissionIsIgnored() = runTest(dispatcher) {
        var loginCalls = 0
        val viewModel = viewModel(
            authenticate = { _, _ ->
                loginCalls += 1
                LoginResult.Failure("Safe failure")
            },
        )

        viewModel.login("owner", "111111")
        assertEquals(0, loginCalls)
        advanceUntilIdle()

        viewModel.login("owner", "111111")
        viewModel.login("owner", "111111")
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        assertEquals(1, loginCalls)
        assertEquals("Safe failure", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun successfulLoginAndLogoutNeverLeaveStaleSession() = runTest(dispatcher) {
        var logoutCalls = 0
        val viewModel = viewModel(
            authenticate = { _, _ -> LoginResult.Success(SESSION) },
            logout = { logoutCalls += 1 },
        )
        advanceUntilIdle()

        viewModel.login("owner", "111111")
        advanceUntilIdle()
        assertEquals(SESSION, viewModel.uiState.value.session)

        viewModel.logout()
        advanceUntilIdle()
        assertEquals(1, logoutCalls)
        assertNull(viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isInitializing)
    }

    @Test
    fun signedOutRestoreMessageCanBeCleared() = runTest(dispatcher) {
        val viewModel = viewModel(
            restore = { SessionRestoreResult.SignedOut("Session expired safely") },
        )
        advanceUntilIdle()

        assertEquals("Session expired safely", viewModel.uiState.value.errorMessage)
        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    private fun viewModel(
        authenticate: AuthenticateUser = AuthenticateUser { _, _ -> LoginResult.Failure("Not used") },
        restore: RestoreSession = RestoreSession { SessionRestoreResult.SignedOut() },
        logout: LogoutUser = LogoutUser { },
    ) = AuthViewModel(authenticate, restore, logout)

    private companion object {
        val SESSION = UserSession(
            userId = "10000000-0000-4000-8000-000000000001",
            displayName = "Owner",
            role = UserRole.OWNER,
            shopId = "20000000-0000-4000-8000-000000000002",
        )
    }
}
