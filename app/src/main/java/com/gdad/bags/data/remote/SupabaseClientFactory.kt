package com.gdad.bags.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import java.net.URI

/** Client-safe configuration supplied through Gradle properties or environment variables. */
data class SupabaseConfig(val url: String, val publishableKey: String) {
    val normalizedUrl: String get() = url.trim().removeSuffix("/")
    val normalizedPublishableKey: String get() = publishableKey.trim()

    val validationError: String?
        get() {
            if (url.isBlank() || publishableKey.isBlank()) {
                return "SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY are required."
            }
            val parsed = runCatching { URI(normalizedUrl) }.getOrNull()
                ?: return "SUPABASE_URL must be a valid HTTPS origin."
            if (
                parsed.scheme?.lowercase() != "https" ||
                parsed.host.isNullOrBlank() ||
                parsed.userInfo != null ||
                parsed.query != null ||
                parsed.fragment != null ||
                (parsed.path.isNotEmpty() && parsed.path != "/")
            ) {
                return "SUPABASE_URL must be an HTTPS origin without credentials, path, query, or fragment."
            }
            if (!PUBLISHABLE_KEY_PATTERN.matches(normalizedPublishableKey)) {
                return "SUPABASE_PUBLISHABLE_KEY must be a client-safe sb_publishable_ key."
            }
            return null
        }

    val isConfigured: Boolean get() = validationError == null

    private companion object {
        val PUBLISHABLE_KEY_PATTERN = Regex("^sb_publishable_[A-Za-z0-9_-]{20,240}$")
    }
}

fun interface SupabaseClientFactory {
    fun create(config: SupabaseConfig, sessionManager: SessionManager): SupabaseClient
}

/** Stateless factory; the application-owned DI container controls the client lifetime. */
class DefaultSupabaseClientFactory : SupabaseClientFactory {
    override fun create(config: SupabaseConfig, sessionManager: SessionManager): SupabaseClient {
        check(config.isConfigured) {
            "Supabase configuration is invalid: ${config.validationError}"
        }
        return createSupabaseClient(
            supabaseUrl = config.normalizedUrl,
            supabaseKey = config.normalizedPublishableKey,
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
                this.sessionManager = sessionManager
            }
            install(Postgrest)
            install(Functions)
        }
    }
}
