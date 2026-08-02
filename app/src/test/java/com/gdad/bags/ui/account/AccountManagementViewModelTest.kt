package com.gdad.bags.ui.account

import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.AccountManagementRepository
import com.gdad.bags.domain.account.AccountOperationResult
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.domain.account.ManagedShop
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun networkRetryReusesTheExactRequestIdAndPublishesSafeSuccess() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = AccountManagementViewModel(repository)
        viewModel.activate(OWNER)
        advanceUntilIdle()

        viewModel.create(CREATE)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.safeMessage?.contains("internet") == true)

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.requestIds.size)
        assertEquals(repository.requestIds.first(), repository.requestIds.last())
        assertEquals("Salesman account created and audited.", viewModel.state.value.safeMessage)
    }

    private class FakeRepository : AccountManagementRepository {
        private val directory = MutableStateFlow(
            AccountDirectory(shops = listOf(ManagedShop(SHOP, "shop", "Shop", true))),
        )
        val requestIds = mutableListOf<String>()
        override fun observe(session: UserSession): Flow<AccountDirectory> = directory
        override suspend fun refresh(session: UserSession) = AccountOperationResult.Success("Accounts refreshed.")
        override suspend fun create(session: UserSession, requestId: String, input: CreateManagedAccount): AccountOperationResult {
            requestIds += requestId
            return if (requestIds.size == 1) AccountOperationResult.Failure(
                RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
                "Connect to the internet and try again.",
            ) else AccountOperationResult.Success("Salesman account created and audited.")
        }
        override suspend fun administer(
            session: UserSession,
            requestId: String,
            input: AdministerManagedAccount,
        ) = AccountOperationResult.Success("updated")
    }

    private companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        val OWNER = UserSession("22222222-2222-4222-8222-222222222222", "Owner", UserRole.OWNER, SHOP)
        val CREATE = CreateManagedAccount("sales.user", "Sales User", "826491", SHOP)
    }
}
