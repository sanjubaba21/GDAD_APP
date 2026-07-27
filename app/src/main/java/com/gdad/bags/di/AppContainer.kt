package com.gdad.bags.di

import com.gdad.bags.data.auth.PreviewAuthRepository
import com.gdad.bags.data.remote.DefaultSupabaseClientFactory
import com.gdad.bags.data.remote.SupabaseClientFactory
import com.gdad.bags.data.remote.SupabaseConfig
import com.gdad.bags.domain.auth.AuthRepository
import com.gdad.bags.domain.auth.AuthenticateUser
import com.gdad.bags.domain.auth.LoginUseCase
import io.github.jan.supabase.SupabaseClient

/** Application dependency graph. Tests can replace this interface with deterministic fakes. */
interface AppContainer {
    val authenticateUser: AuthenticateUser
}

/**
 * Process-wide production graph.
 *
 * Preview authentication remains the temporary implementation until execution-plan Task 4.2.
 * Its construction is centralized here so no composable or ViewModel locates dependencies.
 */
class ProductionAppContainer(
    supabaseConfig: SupabaseConfig,
    authRepository: AuthRepository = PreviewAuthRepository(),
    supabaseClientFactory: SupabaseClientFactory = DefaultSupabaseClientFactory(),
) : AppContainer {
    override val authenticateUser: AuthenticateUser = LoginUseCase(authRepository)

    val supabaseClient: SupabaseClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        supabaseClientFactory.create(supabaseConfig)
    }
}
