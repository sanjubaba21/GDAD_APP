package com.gdad.bags.ui.account

import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.AccountManagementRepository
import com.gdad.bags.domain.account.AccountOperationResult
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.domain.account.CreateManagedShop
import com.gdad.bags.domain.account.DeleteManagedShop
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
import org.junit.Assert.assertNotEquals
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

    @Test
    fun shopRetryReusesRequestIdAndEmptyAdminDirectoryStillShowsControls() = runTest(dispatcher) {
        val repository = FakeRepository().also { it.directory.value = AccountDirectory() }
        val viewModel = AccountManagementViewModel(repository)
        viewModel.activate(ADMIN)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.content is com.gdad.bags.ui.components.ContentState.Ready)
        viewModel.createShop(CreateManagedShop("gdad-kathmandu", "GDAD Kathmandu"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.safeMessage?.contains("internet") == true)

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.shopRequestIds.size)
        assertEquals(repository.shopRequestIds.first(), repository.shopRequestIds.last())
        assertEquals("Shop created with system accounts and an immutable audit record.", viewModel.state.value.safeMessage)
    }

    @Test
    fun deletionRetryReusesTheExactRequestId() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = AccountManagementViewModel(repository)
        viewModel.activate(ADMIN)
        advanceUntilIdle()

        viewModel.deleteShop(DeleteManagedShop(SHOP, "shop", "Controlled test cleanup", "826491"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.safeMessage?.contains("internet") == true)

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.deleteRequestIds.size)
        assertEquals(repository.deleteRequestIds.first(), repository.deleteRequestIds.last())
        assertTrue(viewModel.state.value.safeMessage?.startsWith("Shop deleted") == true)
    }

    @Test
    fun deniedDeletionDropsRejectedPinAndRequiresFreshSubmission() = runTest(dispatcher) {
        val repository = FakeRepository().also { it.denyDeletion = true }
        val viewModel = AccountManagementViewModel(repository)
        viewModel.activate(ADMIN)
        advanceUntilIdle()

        val input = DeleteManagedShop(SHOP, "shop", "Controlled test cleanup", "4826")
        viewModel.deleteShop(input)
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(1, repository.deleteRequestIds.size)
        assertTrue(viewModel.state.value.safeMessage?.contains("denied") == true)

        repository.denyDeletion = false
        viewModel.deleteShop(input.copy(reauthPin = "826491"))
        advanceUntilIdle()

        assertEquals(2, repository.deleteRequestIds.size)
        assertNotEquals(repository.deleteRequestIds.first(), repository.deleteRequestIds.last())
        assertTrue(viewModel.state.value.safeMessage?.startsWith("Shop deleted") == true)
    }

    private class FakeRepository : AccountManagementRepository {
        val directory = MutableStateFlow(
            AccountDirectory(shops = listOf(ManagedShop(SHOP, "shop", "Shop", true))),
        )
        val requestIds = mutableListOf<String>()
        val shopRequestIds = mutableListOf<String>()
        val deleteRequestIds = mutableListOf<String>()
        var denyDeletion = false
        override fun observe(session: UserSession): Flow<AccountDirectory> = directory
        override suspend fun refresh(session: UserSession) = AccountOperationResult.Success("Accounts refreshed.")
        override suspend fun createShop(
            session: UserSession,
            requestId: String,
            input: CreateManagedShop,
        ): AccountOperationResult {
            shopRequestIds += requestId
            return if (shopRequestIds.size == 1) AccountOperationResult.Failure(
                RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
                "Connect to the internet and try again.",
            ) else AccountOperationResult.Success("Shop created with system accounts and an immutable audit record.")
        }
        override suspend fun create(session: UserSession, requestId: String, input: CreateManagedAccount): AccountOperationResult {
            requestIds += requestId
            return if (requestIds.size == 1) AccountOperationResult.Failure(
                RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
                "Connect to the internet and try again.",
            ) else AccountOperationResult.Success("Salesman account created and audited.")
        }
        override suspend fun deleteShop(
            session: UserSession,
            requestId: String,
            input: DeleteManagedShop,
        ): AccountOperationResult {
            deleteRequestIds += requestId
            return if (denyDeletion) AccountOperationResult.Failure(
                RemoteFailure(RemoteErrorKind.UNAUTHORIZED, RetryDisposition.AFTER_AUTH_REFRESH, 403),
                "Shop deletion was denied before any data changed.",
            ) else if (deleteRequestIds.size == 1) AccountOperationResult.Failure(
                RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
                "Connect to the internet before deleting a shop.",
            ) else AccountOperationResult.Success(
                "Shop deleted; its records and shop-only managed access were removed.",
            )
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
        val ADMIN = UserSession("33333333-3333-4333-8333-333333333333", "Admin", UserRole.SUPER_ADMIN, null)
        val CREATE = CreateManagedAccount("sales.user", "Sales User", "826491", SHOP)
    }
}
