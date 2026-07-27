package com.gdad.bags.data.auth

import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession as SupabaseUserSession
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class PinLoginRequest(
    @SerialName("login_id") val loginId: String,
    val pin: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
private data class PinLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String,
)

class SupabasePinLoginRemoteDataSource(
    private val client: SupabaseClient,
) : PinLoginRemoteDataSource {
    override suspend fun login(
        loginId: String,
        pin: String,
        requestId: String,
        installationId: String,
    ): PinLoginRemoteResult {
        return try {
            val response = client.functions.invoke(
                function = "pin-login",
                body = PinLoginRequest(loginId, pin, requestId, installationId),
            )
            if (!response.status.isSuccess()) return failureForStatus(response.status.value)
            val payload = response.body<PinLoginResponse>()
            if (
                payload.accessToken.isBlank() || payload.refreshToken.isBlank() ||
                payload.expiresIn <= 0 || !payload.tokenType.equals("bearer", ignoreCase = true)
            ) {
                PinLoginRemoteResult.Failure(PinLoginFailure.SERVICE_UNAVAILABLE)
            } else {
                PinLoginRemoteResult.Success(
                    PinLoginTokens(
                        accessToken = payload.accessToken,
                        refreshToken = payload.refreshToken,
                        expiresInSeconds = payload.expiresIn,
                        tokenType = payload.tokenType,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RestException) {
            failureForStatus(failure.statusCode)
        } catch (_: Exception) {
            PinLoginRemoteResult.Failure(PinLoginFailure.SERVICE_UNAVAILABLE)
        }
    }

    private fun failureForStatus(status: Int): PinLoginRemoteResult.Failure =
        PinLoginRemoteResult.Failure(
            when (status) {
                400 -> PinLoginFailure.INVALID_REQUEST
                401 -> PinLoginFailure.INVALID_CREDENTIALS
                429 -> PinLoginFailure.RATE_LIMITED
                else -> PinLoginFailure.SERVICE_UNAVAILABLE
            },
        )
}

class SupabaseAuthSessionDataSource(
    private val client: SupabaseClient,
) : AuthSessionDataSource {
    override suspend fun importSession(tokens: PinLoginTokens): String {
        client.auth.importSession(
            session = SupabaseUserSession(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresIn = tokens.expiresInSeconds,
                tokenType = tokens.tokenType,
                user = null,
            ),
            autoRefresh = true,
        )
        return client.auth.retrieveUserForCurrentSession(updateSession = true).id
    }

    override suspend fun restoreSubjectOrNull(): String? {
        if (!client.auth.loadFromStorage(autoRefresh = true)) return null
        return client.auth.retrieveUserForCurrentSession(updateSession = true).id
    }

    override suspend fun logoutAndClear() {
        try {
            client.auth.signOut(SignOutScope.LOCAL)
        } finally {
            withContext(NonCancellable) {
                client.auth.clearSession()
            }
        }
    }

    override suspend fun clearLocalSession() {
        client.auth.clearSession()
    }
}

@Serializable
private data class ProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("platform_role") val platformRole: String,
    val disabled: Boolean,
)

@Serializable
private data class MembershipDto(
    @SerialName("shop_id") val shopId: String,
    val role: String,
    val active: Boolean,
)

@Serializable
private data class ShopDto(val id: String, val active: Boolean)

class SupabaseAuthoritativeIdentityDataSource(
    private val client: SupabaseClient,
) : AuthoritativeIdentityDataSource {
    override suspend fun load(subject: String): UserSession {
        val profiles = client.from("user_profiles").select(
            columns = Columns.raw("user_id,display_name,platform_role,disabled"),
        ) {
            filter { eq("user_id", subject) }
        }.decodeList<ProfileDto>()
        val profile = profiles.single()
        require(profile.userId == subject && !profile.disabled)

        if (profile.platformRole == "super_admin") {
            return UserSession(
                userId = subject,
                displayName = profile.displayName,
                role = UserRole.SUPER_ADMIN,
                shopId = null,
            )
        }
        require(profile.platformRole == "standard")

        val memberships = client.from("shop_memberships").select(
            columns = Columns.raw("shop_id,role,active"),
        ) {
            filter {
                eq("user_id", subject)
                eq("active", true)
            }
        }.decodeList<MembershipDto>()
        val membership = memberships.single()
        require(membership.active)

        val shops = client.from("shops").select(columns = Columns.raw("id,active")) {
            filter {
                eq("id", membership.shopId)
                eq("active", true)
            }
        }.decodeList<ShopDto>()
        require(shops.single().active)

        val role = when (membership.role) {
            "owner" -> UserRole.OWNER
            "salesman" -> UserRole.SALESMAN
            else -> error("Unsupported authoritative shop role")
        }
        return UserSession(
            userId = subject,
            displayName = profile.displayName,
            role = role,
            shopId = membership.shopId,
        )
    }
}
