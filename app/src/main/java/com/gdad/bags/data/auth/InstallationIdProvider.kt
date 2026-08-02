package com.gdad.bags.data.auth

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

fun interface InstallationIdProvider {
    fun getInstallationId(): String
}

class PersistentInstallationIdProvider(context: Context) : InstallationIdProvider {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun getInstallationId(): String = synchronized(preferences) {
        preferences.getString(INSTALLATION_ID_KEY, null)
            ?.takeIf(::isValidUuid)
            ?: UUID.randomUUID().toString().also { generated ->
                preferences.edit(commit = true) {
                    putString(INSTALLATION_ID_KEY, generated)
                }
            }
    }

    private fun isValidUuid(value: String): Boolean = runCatching {
        UUID.fromString(value).toString() == value.lowercase()
    }.getOrDefault(false)

    private companion object {
        const val PREFERENCES_NAME = "gdad_installation_v1"
        const val INSTALLATION_ID_KEY = "installation_id"
    }
}
