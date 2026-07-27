package com.gdad.bags.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Supabase Auth storage backed by a non-exportable Android Keystore AES-GCM key. */
class EncryptedSessionManager(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionManager {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutex = Mutex()

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(json.encodeToString(session).encodeToByteArray())
            val payload = listOf(cipher.iv, ciphertext).joinToString(SEPARATOR) {
                Base64.encodeToString(it, Base64.NO_WRAP)
            }
            preferences.edit(commit = true) {
                putString(SESSION_KEY, payload)
            }
        }
    }

    override suspend fun loadSession(): UserSession = withContext(Dispatchers.IO) {
        mutex.withLock {
            val payload = preferences.getString(SESSION_KEY, null)
                ?: throw IllegalStateException("No stored authentication session")
            try {
                val parts = payload.split(SEPARATOR, limit = 2)
                require(parts.size == 2) { "Invalid encrypted session payload" }
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
                json.decodeFromString<UserSession>(cipher.doFinal(ciphertext).decodeToString())
            } catch (error: Exception) {
                preferences.edit(commit = true) { remove(SESSION_KEY) }
                throw IllegalStateException("Stored authentication session is unreadable", error)
            }
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        mutex.withLock {
            preferences.edit(commit = true) { remove(SESSION_KEY) }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "gdad_auth_session_v1"
        const val SESSION_KEY = "encrypted_session"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gdad.auth.session.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = "."
    }
}
