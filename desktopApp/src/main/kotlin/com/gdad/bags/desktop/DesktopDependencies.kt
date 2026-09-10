package com.gdad.bags.desktop

import com.gdad.bags.data.auth.PersistentInstallationIdProvider
import com.gdad.bags.data.auth.ProductionAuthRepository
import com.gdad.bags.data.auth.SupabaseAuthSessionDataSource
import com.gdad.bags.data.auth.SupabaseAuthoritativeIdentityDataSource
import com.gdad.bags.data.auth.SupabasePinLoginRemoteDataSource
import com.gdad.bags.data.auth.UnconfiguredAuthRepository
import com.gdad.bags.data.local.DesktopSessionCache
import com.gdad.bags.data.remote.AuthSessionRefresher
import com.gdad.bags.data.remote.DefaultSupabaseClientFactory
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.requireExpectedAuthSubject
import com.gdad.bags.data.product.SupabaseProductRemoteDataSource
import com.gdad.bags.data.report.SupabaseReportRemoteDataSource
import com.gdad.bags.data.sale.ProductionSaleCheckoutRepository
import com.gdad.bags.data.sale.SupabaseSaleRemoteDataSource
import com.gdad.bags.domain.auth.AuthRepository
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.ProductResult
import com.gdad.bags.domain.report.BusinessReport
import com.gdad.bags.domain.sale.PostedSale
import com.gdad.bags.domain.sale.SaleCheckoutRepository
import com.gdad.bags.domain.sale.SaleDraft
import com.gdad.bags.domain.sale.SaleResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession as SupabaseUserSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DesktopDependencies(config: DesktopConfig) {
    private val sessionManager = EphemeralSessionManager()
    private val client: SupabaseClient? = config.supabase.takeIf { it.isConfigured }?.let {
        DefaultSupabaseClientFactory().create(it, sessionManager)
    }

    private val remoteCalls: RemoteCallExecutor? = client?.let { configuredClient ->
        RemoteCallExecutor(
            AuthSessionRefresher { expectedSubject ->
                configuredClient.auth.refreshCurrentSession()
                val refreshedSubject = configuredClient.auth
                    .retrieveUserForCurrentSession(updateSession = true).id
                requireExpectedAuthSubject(expectedSubject, refreshedSubject)
            },
        )
    }

    val configurationError: String? = config.supabase.validationError

    val authRepository: AuthRepository = if (client == null || remoteCalls == null) {
        UnconfiguredAuthRepository()
    } else {
        ProductionAuthRepository(
            pinLogin = SupabasePinLoginRemoteDataSource(client, remoteCalls),
            authSession = SupabaseAuthSessionDataSource(client),
            identity = SupabaseAuthoritativeIdentityDataSource(client, remoteCalls),
            installationIdProvider = PersistentInstallationIdProvider(),
            sessionCache = DesktopSessionCache(),
        )
    }

    private val reports = if (client == null || remoteCalls == null) {
        null
    } else {
        SupabaseReportRemoteDataSource(client, remoteCalls)
    }

    val products: DesktopProductCatalogRepository? = if (client == null || remoteCalls == null) {
        null
    } else {
        DesktopProductCatalogRepository(SupabaseProductRemoteDataSource(client, remoteCalls))
    }

    val sales: SaleCheckoutRepository? = if (client == null || remoteCalls == null || products == null) {
        null
    } else {
        ProductionSaleCheckoutRepository(SupabaseSaleRemoteDataSource(client, remoteCalls), products)
    }

    suspend fun loadDashboard(session: UserSession): RemoteResult<BusinessReport>? {
        val shopId = session.shopId ?: return null
        return reports?.dashboard(com.gdad.bags.data.local.CacheOwner(session.userId, shopId))
    }

    suspend fun loadProducts(session: UserSession): ProductResult? = products?.refresh(session)

    suspend fun postSale(
        session: UserSession,
        requestId: String,
        draft: SaleDraft,
    ): SaleResult<PostedSale>? = sales?.post(session, requestId, draft)

    fun clearBusinessState() {
        products?.clear()
    }
}

/** Tokens live only for this process until Windows DPAPI persistence is implemented and reviewed. */
private class EphemeralSessionManager : SessionManager {
    private val mutex = Mutex()
    private var session: SupabaseUserSession? = null

    override suspend fun saveSession(session: SupabaseUserSession) = mutex.withLock {
        this.session = session
    }

    override suspend fun loadSession(): SupabaseUserSession = mutex.withLock {
        session ?: throw IllegalStateException("No stored authentication session")
    }

    override suspend fun deleteSession() = mutex.withLock {
        session = null
    }
}
