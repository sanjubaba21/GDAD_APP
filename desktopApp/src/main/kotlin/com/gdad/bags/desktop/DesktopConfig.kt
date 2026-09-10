package com.gdad.bags.desktop

import com.gdad.bags.data.remote.SupabaseConfig
import java.util.Properties

data class DesktopConfig(val supabase: SupabaseConfig) {
    companion object {
        fun load(
            environment: Map<String, String> = System.getenv(),
            systemProperties: Properties = System.getProperties(),
            resourceProperties: Properties = loadResourceProperties(),
        ): DesktopConfig {
            fun value(name: String, resourceName: String): String =
                environment[name]?.takeIf { it.isNotBlank() }
                    ?: systemProperties.getProperty(name)?.takeIf { it.isNotBlank() }
                    ?: resourceProperties.getProperty(resourceName).orEmpty()

            return DesktopConfig(
                SupabaseConfig(
                    url = value("SUPABASE_URL", "supabase.url"),
                    publishableKey = value(
                        "SUPABASE_PUBLISHABLE_KEY",
                        "supabase.publishableKey",
                    ),
                ),
            )
        }

        internal fun fromValues(url: String, publishableKey: String) =
            DesktopConfig(SupabaseConfig(url, publishableKey))

        private fun loadResourceProperties(): Properties = Properties().apply {
            DesktopConfig::class.java.getResourceAsStream("/gdad-desktop.properties")?.use(::load)
        }
    }
}
