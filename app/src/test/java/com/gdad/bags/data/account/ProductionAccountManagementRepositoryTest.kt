package com.gdad.bags.data.account

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.data.local.RoomCacheStore
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.account.AccountAction
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.AccountOperationResult
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.domain.account.CreateManagedShop
import com.gdad.bags.domain.account.ManagedAccount
import com.gdad.bags.domain.account.ManagedShop
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductionAccountManagementRepositoryTest {
    private lateinit var database: RoomCacheDatabase
    private lateinit var remote: FakeRemote
    private lateinit var repository: ProductionAccountManagementRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RoomCacheDatabase::class.java)
            .allowMainThreadQueries().build()
        remote = FakeRemote()
        repository = ProductionAccountManagementRepository(remote, AccountDirectoryStore(database))
        RoomCacheStore(database).activate(CacheOwner(OWNER.userId, OWNER.shopId))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun refreshPublishesOnlyRemoteDirectoryIntoRoom() = runBlocking {
        remote.directory = RemoteResult.Success(DIRECTORY)

        assertTrue(repository.refresh(OWNER) is AccountOperationResult.Success)

        assertEquals(DIRECTORY, repository.observe(OWNER).first())
    }

    @Test
    fun identitySwitchPurgesManagedAccountDirectory() = runBlocking {
        remote.directory = RemoteResult.Success(DIRECTORY)
        repository.refresh(OWNER)

        RoomCacheStore(database).activate(CacheOwner(OTHER_USER, OTHER_SHOP))

        assertEquals(AccountDirectory(), repository.observe(OWNER).first())
    }

    @Test
    fun createSuccessRefreshesAndReturnsAuditFriendlyMessage() = runBlocking {
        remote.directory = RemoteResult.Success(DIRECTORY)
        val result = repository.create(OWNER, REQUEST, CREATE)

        assertEquals("Salesman account created and audited.", (result as AccountOperationResult.Success).safeMessage)
        assertEquals(listOf(REQUEST), remote.createRequestIds)
        assertEquals(DIRECTORY, repository.observe(OWNER).first())
    }

    @Test
    fun superAdminCreatesShopThenRefreshesDirectory() = runBlocking {
        RoomCacheStore(database).activate(CacheOwner(ADMIN.userId, ADMIN.shopId))
        remote.directory = RemoteResult.Success(
            AccountDirectory(shops = listOf(ManagedShop(NEW_SHOP, "gdad-kathmandu", "GDAD Kathmandu", true))),
        )

        val result = repository.createShop(ADMIN, REQUEST, CREATE_SHOP)

        assertEquals(
            "Shop created with system accounts and an immutable audit record.",
            (result as AccountOperationResult.Success).safeMessage,
        )
        assertEquals(listOf(REQUEST), remote.shopRequestIds)
        assertEquals("gdad-kathmandu", repository.observe(ADMIN).first().shops.single().slug)
    }

    @Test
    fun nonAdminAndInvalidShopNeverReachRemoteBoundary() = runBlocking {
        val denied = repository.createShop(OWNER, REQUEST, CREATE_SHOP)
        val invalid = repository.createShop(ADMIN, REQUEST, CREATE_SHOP.copy(slug = "Bad Slug"))

        assertTrue(denied is AccountOperationResult.Failure)
        assertTrue(invalid is AccountOperationResult.Failure)
        assertTrue(remote.shopRequestIds.isEmpty())
    }

    @Test
    fun denialAndValidationNeverReachRemoteBoundary() = runBlocking {
        val salesman = OWNER.copy(userId = OTHER_USER, role = UserRole.SALESMAN)
        val denied = repository.create(salesman, REQUEST, CREATE)
        val crossShop = repository.create(OWNER, REQUEST, CREATE.copy(shopId = OTHER_SHOP))
        val invalid = repository.create(OWNER, REQUEST, CREATE.copy(pin = "1234"))

        assertTrue(denied is AccountOperationResult.Failure)
        assertTrue(crossShop is AccountOperationResult.Failure)
        assertTrue(invalid is AccountOperationResult.Failure)
        assertTrue(remote.createRequestIds.isEmpty())
    }

    @Test
    fun networkFailureIsSafeAndExactRequestIdCanRetry() = runBlocking {
        remote.createResults += RemoteResult.Failure(
            RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
        )
        remote.createResults += RemoteResult.Success(Unit)
        remote.directory = RemoteResult.Success(DIRECTORY)

        val first = repository.create(OWNER, REQUEST, CREATE)
        val second = repository.create(OWNER, REQUEST, CREATE)

        assertEquals("Connect to the internet and try again.", (first as AccountOperationResult.Failure).safeMessage)
        assertTrue(second is AccountOperationResult.Success)
        assertEquals(listOf(REQUEST, REQUEST), remote.createRequestIds)
    }

    @Test
    fun committedCreationRemainsSuccessWhenDirectoryRefreshIsPending() = runBlocking {
        remote.directory = RemoteResult.Failure(
            RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
        )

        val result = repository.create(OWNER, REQUEST, CREATE) as AccountOperationResult.Success

        assertTrue(result.safeMessage.startsWith("Salesman account created and audited."))
        assertTrue(result.safeMessage.contains("refresh is pending"))
        assertEquals(listOf(REQUEST), remote.createRequestIds)
    }

    @Test
    fun rejectedAdminSessionHasAnActionableMessage() = runBlocking {
        remote.createResults += RemoteResult.Failure(
            RemoteFailure(
                RemoteErrorKind.UNAUTHORIZED,
                RetryDisposition.AFTER_AUTH_REFRESH,
                statusCode = 401,
            ),
        )

        val result = repository.create(OWNER, REQUEST, CREATE) as AccountOperationResult.Failure

        assertEquals(
            "Your admin session could not be verified. Sign out and sign in again.",
            result.safeMessage,
        )
    }

    @Test
    fun duplicateLoginIdHasAnActionableMessage() = runBlocking {
        remote.createResults += RemoteResult.Failure(
            RemoteFailure(
                RemoteErrorKind.CONFLICT,
                RetryDisposition.NEVER,
                statusCode = 409,
            ),
        )

        val result = repository.create(OWNER, REQUEST, CREATE) as AccountOperationResult.Failure

        assertEquals(
            "This Login ID is already in use. Choose a different Login ID.",
            result.safeMessage,
        )
    }

    @Test
    fun accountFunctionTransportExplicitlyDeclaresJson() {
        assertEquals(
            ContentType.Application.Json.toString(),
            ACCOUNT_FUNCTION_HEADERS[HttpHeaders.ContentType],
        )
    }

    @Test
    fun disableSuccessReportsSessionRevocation() = runBlocking {
        remote.directory = RemoteResult.Success(DIRECTORY)
        repository.refresh(OWNER)
        val result = repository.administer(
            OWNER,
            REQUEST,
            AdministerManagedAccount(TARGET_USER, AccountAction.DISABLE, "826491"),
        )

        assertEquals(
            "Account disabled; refresh sessions were revoked.",
            (result as AccountOperationResult.Success).safeMessage,
        )
    }

    private class FakeRemote : AccountRemoteDataSource {
        var directory: RemoteResult<AccountDirectory> = RemoteResult.Success(AccountDirectory())
        val createResults = ArrayDeque<RemoteResult<Unit>>()
        val createRequestIds = mutableListOf<String>()
        val shopRequestIds = mutableListOf<String>()
        override suspend fun load(session: UserSession) = directory
        override suspend fun createShop(
            session: UserSession,
            requestId: String,
            input: CreateManagedShop,
        ): RemoteResult<Unit> {
            shopRequestIds += requestId
            return RemoteResult.Success(Unit)
        }
        override suspend fun create(session: UserSession, requestId: String, input: CreateManagedAccount): RemoteResult<Unit> {
            createRequestIds += requestId
            return createResults.removeFirstOrNull() ?: RemoteResult.Success(Unit)
        }
        override suspend fun administer(
            session: UserSession,
            requestId: String,
            input: AdministerManagedAccount,
        ) = RemoteResult.Success(Unit)
    }

    private companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val OTHER_SHOP = "22222222-2222-4222-8222-222222222222"
        const val TARGET_USER = "33333333-3333-4333-8333-333333333333"
        const val OTHER_USER = "44444444-4444-4444-8444-444444444444"
        const val REQUEST = "55555555-5555-4555-8555-555555555555"
        const val NEW_SHOP = "77777777-7777-4777-8777-777777777777"
        val OWNER = UserSession("66666666-6666-4666-8666-666666666666", "Owner", UserRole.OWNER, SHOP)
        val ADMIN = UserSession("88888888-8888-4888-8888-888888888888", "Admin", UserRole.SUPER_ADMIN, null)
        val CREATE = CreateManagedAccount("sales.user", "Sales User", "826491", SHOP)
        val CREATE_SHOP = CreateManagedShop("gdad-kathmandu", "GDAD Kathmandu")
        val DIRECTORY = AccountDirectory(
            accounts = listOf(ManagedAccount(TARGET_USER, SHOP, "sales.user", "Sales User", UserRole.SALESMAN, false, true)),
            shops = listOf(ManagedShop(SHOP, "main-shop", "Main Shop", true)),
        )
    }
}
