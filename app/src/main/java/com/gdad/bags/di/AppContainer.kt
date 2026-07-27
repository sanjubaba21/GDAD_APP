package com.gdad.bags.di

import android.content.Context
import com.gdad.bags.data.auth.EncryptedSessionManager
import com.gdad.bags.data.auth.PersistentInstallationIdProvider
import com.gdad.bags.data.auth.ProductionAuthRepository
import com.gdad.bags.data.auth.SupabaseAuthSessionDataSource
import com.gdad.bags.data.auth.SupabaseAuthoritativeIdentityDataSource
import com.gdad.bags.data.auth.SupabasePinLoginRemoteDataSource
import com.gdad.bags.data.auth.UnconfiguredAuthRepository
import com.gdad.bags.data.remote.DefaultSupabaseClientFactory
import com.gdad.bags.data.remote.SupabaseClientFactory
import com.gdad.bags.data.remote.SupabaseConfig
import com.gdad.bags.domain.auth.AuthenticateUser
import com.gdad.bags.domain.auth.LoginUseCase
import com.gdad.bags.domain.auth.LogoutUseCase
import com.gdad.bags.domain.auth.LogoutUser
import com.gdad.bags.domain.auth.RestoreSession
import com.gdad.bags.domain.auth.RestoreSessionUseCase
import io.github.jan.supabase.SupabaseClient

/** Application dependency graph. Tests can replace this interface with deterministic fakes. */
interface AppContainer {
    val authenticateUser: AuthenticateUser
    val restoreSession: RestoreSession
    val logoutUser: LogoutUser
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
    private val sessionManager = EncryptedSessionManager(context)
    private val installationIdProvider = PersistentInstallationIdProvider(context)

    val supabaseClient: SupabaseClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        supabaseClientFactory.create(supabaseConfig, sessionManager)
    }

    private val authRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (!supabaseConfig.isConfigured) {
            UnconfiguredAuthRepository()
        } else {
            ProductionAuthRepository(
                pinLogin = SupabasePinLoginRemoteDataSource(supabaseClient),
                authSession = SupabaseAuthSessionDataSource(supabaseClient),
                identity = SupabaseAuthoritativeIdentityDataSource(supabaseClient),
                installationIdProvider = installationIdProvider,
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
