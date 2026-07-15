package com.gdad.bags.data.remote

import com.gdad.bags.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Application-wide Supabase entry point.
 *
 * Configuration is supplied through Gradle properties or environment variables named
 * SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY. The publishable key is designed for client
 * applications; never put a secret key or service-role key in the Android build.
 */
object SupabaseClientProvider {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        check(isConfigured) {
            "Supabase is not configured. Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Functions)
        }
    }

    val hasAuthenticatedSession: Boolean
        get() = isConfigured && client.auth.currentSessionOrNull() != null
}
