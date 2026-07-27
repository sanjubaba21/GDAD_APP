package com.gdad.bags.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

/** Client-safe configuration supplied through Gradle properties or environment variables. */
data class SupabaseConfig(val url: String, val publishableKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank()
}

fun interface SupabaseClientFactory {
    fun create(config: SupabaseConfig): SupabaseClient
}

/** Stateless factory; the application-owned DI container controls the client lifetime. */
class DefaultSupabaseClientFactory : SupabaseClientFactory {
    override fun create(config: SupabaseConfig): SupabaseClient {
        check(config.isConfigured) {
            "Supabase is not configured. Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY."
        }
        return createSupabaseClient(
            supabaseUrl = config.url,
            supabaseKey = config.publishableKey,
        ) {
            install(Auth)
            install(Postgrest)
            install(Functions)
        }
    }
}
