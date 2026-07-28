package com.gdad.bags.di

import android.content.Context
import com.gdad.bags.data.auth.EncryptedSessionManager
import com.gdad.bags.data.auth.PersistentInstallationIdProvider
import com.gdad.bags.data.auth.ProductionAuthRepository
import com.gdad.bags.data.auth.SupabaseAuthSessionDataSource
import com.gdad.bags.data.auth.SupabaseAuthoritativeIdentityDataSource
import com.gdad.bags.data.auth.SupabasePinLoginRemoteDataSource
import com.gdad.bags.data.auth.UnconfiguredAuthRepository
import com.gdad.bags.data.account.AccountDirectoryStore
import com.gdad.bags.data.account.ProductionAccountManagementRepository
import com.gdad.bags.data.account.SupabaseAccountRemoteDataSource
import com.gdad.bags.data.product.ProductCatalogStore
import com.gdad.bags.data.product.ProductionProductCatalogRepository
import com.gdad.bags.data.product.SupabaseProductRemoteDataSource
import com.gdad.bags.data.purchase.ProductionPurchaseManagementRepository
import com.gdad.bags.data.purchase.PurchaseDirectoryStore
import com.gdad.bags.data.purchase.SupabasePurchaseRemoteDataSource
import com.gdad.bags.data.stock.ProductionStockManagementRepository
import com.gdad.bags.data.stock.SupabaseStockRemoteDataSource
import com.gdad.bags.data.sale.ProductionSaleCheckoutRepository
import com.gdad.bags.data.sale.SupabaseSaleRemoteDataSource
import com.gdad.bags.data.returning.ProductionSaleReturnRepository
import com.gdad.bags.data.returning.SupabaseSaleReturnRemoteDataSource
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.data.local.RoomCacheStore
import com.gdad.bags.data.local.MutationOutbox
import com.gdad.bags.data.local.OutboxProcessor
import com.gdad.bags.data.local.OutboxWork
import com.gdad.bags.data.remote.DefaultSupabaseClientFactory
import com.gdad.bags.data.remote.AuthSessionRefresher
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.SupabaseClientFactory
import com.gdad.bags.data.remote.SupabaseConfig
import com.gdad.bags.data.remote.SupabaseOutboxDispatcher
import com.gdad.bags.domain.auth.AuthenticateUser
import com.gdad.bags.domain.auth.LoginUseCase
import com.gdad.bags.domain.auth.LogoutUseCase
import com.gdad.bags.domain.auth.LogoutUser
import com.gdad.bags.domain.auth.RestoreSession
import com.gdad.bags.domain.auth.RestoreSessionUseCase
import com.gdad.bags.domain.account.AccountManagementRepository
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.purchase.PurchaseManagementRepository
import com.gdad.bags.domain.stock.StockManagementRepository
import com.gdad.bags.domain.sale.SaleCheckoutRepository
import com.gdad.bags.domain.returning.SaleReturnRepository
import com.gdad.bags.data.vendorfinance.ProductionVendorFinanceRepository
import com.gdad.bags.data.vendorfinance.SupabaseVendorFinanceRemoteDataSource
import com.gdad.bags.domain.vendorfinance.VendorFinanceRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

/** Application dependency graph. Tests can replace this interface with deterministic fakes. */
interface AppContainer {
    val authenticateUser: AuthenticateUser
    val restoreSession: RestoreSession
    val logoutUser: LogoutUser
    val mutationOutbox: MutationOutbox
    val accountManagementRepository: AccountManagementRepository
    val productCatalogRepository: ProductCatalogRepository
    val purchaseManagementRepository: PurchaseManagementRepository
    val stockManagementRepository: StockManagementRepository
    val saleCheckoutRepository: SaleCheckoutRepository
    val saleReturnRepository: SaleReturnRepository
    val vendorFinanceRepository: VendorFinanceRepository
}

/**
 * Process-wide production graph.
 *
 * Production authentication is selected here so no composable or ViewModel locates concrete
 * network, storage, or repository dependencies.
 */
class ProductionAppContainer(
    context: Context,
    private val supabaseConfig: SupabaseConfig,
    supabaseClientFactory: SupabaseClientFactory = DefaultSupabaseClientFactory(),
) : AppContainer {
    private val applicationContext = context.applicationContext
    private val sessionManager = EncryptedSessionManager(context)
    private val installationIdProvider = PersistentInstallationIdProvider(context)

    private val cacheDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomCacheDatabase.open(applicationContext)
    }
    private val sessionCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomCacheStore(cacheDatabase)
    }

    override val mutationOutbox by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MutationOutbox(cacheDatabase.outboxDao()) {
            OutboxWork.schedule(applicationContext)
        }
    }

    val supabaseClient: SupabaseClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        supabaseClientFactory.create(supabaseConfig, sessionManager)
    }

    private val remoteCalls by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RemoteCallExecutor(
            authSessionRefresher = AuthSessionRefresher {
                supabaseClient.auth.refreshCurrentSession()
            },
        )
    }

    val outboxProcessor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OutboxProcessor(
            database = cacheDatabase,
            dispatcher = SupabaseOutboxDispatcher(supabaseClient, remoteCalls),
        )
    }

    override val accountManagementRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductionAccountManagementRepository(
            remote = SupabaseAccountRemoteDataSource(supabaseClient, remoteCalls),
            store = AccountDirectoryStore(cacheDatabase),
        )
    }

    override val productCatalogRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductionProductCatalogRepository(
            remote = SupabaseProductRemoteDataSource(supabaseClient, remoteCalls),
            store = ProductCatalogStore(cacheDatabase),
            outbox = mutationOutbox,
        )
    }

    override val purchaseManagementRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductionPurchaseManagementRepository(
            remote = SupabasePurchaseRemoteDataSource(supabaseClient, remoteCalls),
            store = PurchaseDirectoryStore(cacheDatabase),
            products = productCatalogRepository,
        )
    }

    override val stockManagementRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductionStockManagementRepository(SupabaseStockRemoteDataSource(supabaseClient, remoteCalls), productCatalogRepository)
    }

    override val saleCheckoutRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductionSaleCheckoutRepository(SupabaseSaleRemoteDataSource(supabaseClient, remoteCalls), productCatalogRepository)
    }

    override val saleReturnRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductionSaleReturnRepository(SupabaseSaleReturnRemoteDataSource(supabaseClient, remoteCalls), productCatalogRepository)
    }

    override val vendorFinanceRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductionVendorFinanceRepository(
            SupabaseVendorFinanceRemoteDataSource(supabaseClient, remoteCalls),
            purchaseManagementRepository,
            productCatalogRepository,
        )
    }

    private val authRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (!supabaseConfig.isConfigured) {
            UnconfiguredAuthRepository()
        } else {
            ProductionAuthRepository(
                pinLogin = SupabasePinLoginRemoteDataSource(supabaseClient, remoteCalls),
                authSession = SupabaseAuthSessionDataSource(supabaseClient),
                identity = SupabaseAuthoritativeIdentityDataSource(supabaseClient, remoteCalls),
                installationIdProvider = installationIdProvider,
                sessionCache = sessionCache,
            )
        }
    }

    override val authenticateUser: AuthenticateUser by lazy {
        LoginUseCase(authRepository)
    }
    override val restoreSession: RestoreSession by lazy {
        RestoreSessionUseCase(authRepository)
    }
    override val logoutUser: LogoutUser by lazy {
        LogoutUseCase(authRepository)
    }
}
