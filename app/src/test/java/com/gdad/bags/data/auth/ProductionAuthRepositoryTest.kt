package com.gdad.bags.data.auth

import com.gdad.bags.domain.auth.LoginResult
import com.gdad.bags.domain.auth.OperationErrorKind
import com.gdad.bags.domain.auth.SessionRestoreResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.SessionCache
import kotlinx.coroutines.runBlocking
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionAuthRepositoryTest {
    private val tokens = PinLoginTokens("access", "refresh", 3600, "bearer")
    private val authoritativeSession = UserSession(
        userId = "10000000-0000-4000-8000-000000000001",
        displayName = "Authoritative Owner",
        role = UserRole.OWNER,
        shopId = "20000000-0000-4000-8000-000000000001",
    )

    @Test
    fun successfulLoginNormalizesIdAndPublishesOnlyAuthoritativeIdentity() = runBlocking {
        val remote = FakePinLogin(PinLoginRemoteResult.Success(tokens))
        val authSession = FakeAuthSession(importedSubject = authoritativeSession.userId)
        val cache = FakeSessionCache()
        val repository = repository(remote, authSession, FakeIdentity(authoritativeSession), cache)

        val result = repository.login("  Owner.Kathmandu  ", TEST_PIN)

        assertEquals("owner.kathmandu", remote.loginId)
        assertEquals(TEST_PIN, remote.pin)
        assertTrue(remote.requestId?.let(UUID_PATTERN::matches) == true)
        assertEquals("installation-test-0001", remote.installationId)
        assertEquals(tokens, authSession.importedTokens)
        assertEquals(authoritativeSession, (result as LoginResult.Success).session)
        assertEquals(authoritativeSession.userId, cache.activeOwner?.userId)
        assertEquals(authoritativeSession.shopId, cache.activeOwner?.shopId)
    }

    @Test
    fun invalidInputNeverCallsHostedLogin() = runBlocking {
        val remote = FakePinLogin(PinLoginRemoteResult.Success(tokens))
        val repository = repository(remote, FakeAuthSession(), FakeIdentity(authoritativeSession))

        val result = repository.login("x", "123")

        assertTrue(result is LoginResult.Failure)
        assertNull(remote.loginId)
    }

    @Test
    fun legacyExistingPinReachesHostedLogin() = runBlocking {
        val remote = FakePinLogin(PinLoginRemoteResult.Success(tokens))
        val repository = repository(
            remote,
            FakeAuthSession(importedSubject = authoritativeSession.userId),
            FakeIdentity(authoritativeSession),
        )

        val result = repository.login("owner.kathmandu", "4826")

        assertTrue(result is LoginResult.Success)
        assertEquals("4826", remote.pin)
    }

    @Test
    fun hostedFailuresMapToSafeMessages() = runBlocking {
        val remote = FakePinLogin(
            PinLoginRemoteResult.Failure(
                RemoteFailure(RemoteErrorKind.UNAUTHORIZED, RetryDisposition.AFTER_AUTH_REFRESH),
            ),
        )
        val repository = repository(remote, FakeAuthSession(), FakeIdentity(authoritativeSession))

        val result = repository.login("owner.test", TEST_PIN) as LoginResult.Failure

        assertEquals("Incorrect user ID or PIN", result.message)
        assertEquals(OperationErrorKind.UNAUTHORIZED, result.kind)
        assertFalse(result.message.contains("SERVICE", ignoreCase = true))
    }

    @Test
    fun hostedValidationFailureIdentifiesAnIncompatibleAppRequest() = runBlocking {
        val remote = FakePinLogin(
            PinLoginRemoteResult.Failure(
                RemoteFailure(RemoteErrorKind.VALIDATION, RetryDisposition.NEVER),
            ),
        )
        val repository = repository(remote, FakeAuthSession(), FakeIdentity(authoritativeSession))

        val result = repository.login("owner.test", TEST_PIN) as LoginResult.Failure

        assertEquals(
            "This app version cannot complete sign-in. Install the latest GDAD BAGS APK.",
            result.message,
        )
        assertEquals(OperationErrorKind.VALIDATION, result.kind)
    }

    @Test
    fun pinLoginTransportExplicitlyDeclaresJson() {
        assertEquals(
            ContentType.Application.Json.toString(),
            PIN_LOGIN_HEADERS[HttpHeaders.ContentType],
        )
    }

    @Test
    fun installationIdentifierFailureBecomesSafeUnavailableResult() = runBlocking {
        val remote = FakePinLogin(PinLoginRemoteResult.Success(tokens))
        val repository = ProductionAuthRepository(
            pinLogin = remote,
            authSession = FakeAuthSession(),
            identity = FakeIdentity(authoritativeSession),
            installationIdProvider = InstallationIdProvider {
                error("storage unavailable")
            },
            sessionCache = FakeSessionCache(),
        )

        val result = repository.login("owner.test", TEST_PIN) as LoginResult.Failure

        assertEquals("Unable to sign in. Check your connection and try again.", result.message)
        assertNull(remote.loginId)
    }

    @Test
    fun identityFailureAfterTokenImportClearsLocalSession() = runBlocking {
        val authSession = FakeAuthSession(importedSubject = authoritativeSession.userId)
        val repository = repository(
            FakePinLogin(PinLoginRemoteResult.Success(tokens)),
            authSession,
            FailingIdentity,
        )

        val result = repository.login("owner.test", TEST_PIN)

        assertTrue(result is LoginResult.Failure)
        assertEquals(1, authSession.clearCount)
    }

    @Test
    fun restoredSessionIsPublishedOnlyAfterAuthoritativeReload() = runBlocking {
        val authSession = FakeAuthSession(restoredSubject = authoritativeSession.userId)
        val repository = repository(
            FakePinLogin(PinLoginRemoteResult.Success(tokens)),
            authSession,
            FakeIdentity(authoritativeSession),
        )

        val result = repository.restoreSession() as SessionRestoreResult.Authenticated

        assertEquals(authoritativeSession, result.session)
    }

    @Test
    fun logoutUsesSessionGateway() = runBlocking {
        val authSession = FakeAuthSession()
        val cache = FakeSessionCache()
        val repository = repository(
            FakePinLogin(PinLoginRemoteResult.Success(tokens)),
            authSession,
            FakeIdentity(authoritativeSession),
            cache,
        )

        repository.logout()

        assertEquals(1, authSession.logoutCount)
        assertEquals(1, cache.purgeCount)
    }

    @Test
    fun logoutFailureFallsBackToLocalClear() = runBlocking {
        val authSession = FakeAuthSession(failLogout = true)
        val repository = repository(
            FakePinLogin(PinLoginRemoteResult.Success(tokens)),
            authSession,
            FakeIdentity(authoritativeSession),
        )

        repository.logout()

        assertEquals(1, authSession.logoutCount)
        assertEquals(1, authSession.clearCount)
    }

    private fun repository(
        remote: FakePinLogin,
        authSession: FakeAuthSession,
        identity: AuthoritativeIdentityDataSource,
        sessionCache: FakeSessionCache = FakeSessionCache(),
    ) = ProductionAuthRepository(
        pinLogin = remote,
        authSession = authSession,
        identity = identity,
        installationIdProvider = InstallationIdProvider { "installation-test-0001" },
        sessionCache = sessionCache,
    )

    private class FakePinLogin(
        var result: PinLoginRemoteResult,
    ) : PinLoginRemoteDataSource {
        var loginId: String? = null
        var pin: String? = null
        var requestId: String? = null
        var installationId: String? = null

        override suspend fun login(
            loginId: String,
            pin: String,
            requestId: String,
            installationId: String,
        ): PinLoginRemoteResult {
            this.loginId = loginId
            this.pin = pin
            this.requestId = requestId
            this.installationId = installationId
            return result
        }
    }

    private class FakeAuthSession(
        private val importedSubject: String = "subject",
        private val restoredSubject: String? = null,
        private val failLogout: Boolean = false,
    ) : AuthSessionDataSource {
        var importedTokens: PinLoginTokens? = null
        var logoutCount = 0
        var clearCount = 0

        override suspend fun importSession(tokens: PinLoginTokens): String {
            importedTokens = tokens
            return importedSubject
        }

        override suspend fun restoreSubjectOrNull(): String? = restoredSubject

        override suspend fun logoutAndClear() {
            logoutCount++
            if (failLogout) error("network sign-out failed")
        }

        override suspend fun clearLocalSession() {
            clearCount++
        }
    }

    private class FakeIdentity(
        private val session: UserSession,
    ) : AuthoritativeIdentityDataSource {
        override suspend fun load(subject: String): RemoteResult<UserSession> {
            assertEquals(session.userId, subject)
            return RemoteResult.Success(session)
        }
    }

    private object FailingIdentity : AuthoritativeIdentityDataSource {
        override suspend fun load(subject: String): RemoteResult<UserSession> =
            error("identity unavailable")
    }

    private class FakeSessionCache : SessionCache {
        var activeOwner: CacheOwner? = null
        var purgeCount = 0

        override suspend fun activate(owner: CacheOwner) {
            activeOwner = owner
        }

        override suspend fun purge() {
            purgeCount++
        }
    }

    private companion object {
        const val TEST_PIN = "482604"
        val UUID_PATTERN = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        )
    }
}
