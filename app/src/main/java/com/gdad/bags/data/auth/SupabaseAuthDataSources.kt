package com.gdad.bags.data.auth

import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.data.remote.MembershipDto
import com.gdad.bags.data.remote.PinLoginRequestDto
import com.gdad.bags.data.remote.PinLoginResponseDto
import com.gdad.bags.data.remote.ProfileDto
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteHttpException
import com.gdad.bags.data.remote.RemoteOperation
import com.gdad.bags.data.remote.RemoteQueryWindow
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.ShopDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession as SupabaseUserSession
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class SupabasePinLoginRemoteDataSource(
    private val client: SupabaseClient,
    private val remoteCalls: RemoteCallExecutor,
) : PinLoginRemoteDataSource {
    override suspend fun login(
        loginId: String,
        pin: String,
        requestId: String,
        installationId: String,
    ): PinLoginRemoteResult {
        return when (val result = remoteCalls.execute(
            operation = RemoteOperation.PIN_LOGIN,
            requiresAuth = false,
        ) {
            val response = client.functions.invoke(
                function = "pin-login",
                body = PinLoginRequestDto(loginId, pin, requestId, installationId),
            )
            if (!response.status.isSuccess()) {
                throw RemoteHttpException(response.status.value)
            }
            val payload = response.body<PinLoginResponseDto>()
            require(
                payload.accessToken.isNotBlank() && payload.refreshToken.isNotBlank() &&
                    payload.expiresIn > 0 &&
                    payload.tokenType.equals("bearer", ignoreCase = true),
            )
            PinLoginTokens(
                accessToken = payload.accessToken,
                refreshToken = payload.refreshToken,
                expiresInSeconds = payload.expiresIn,
                tokenType = payload.tokenType,
            )
        }) {
            is RemoteResult.Success -> PinLoginRemoteResult.Success(result.value)
            is RemoteResult.Failure -> PinLoginRemoteResult.Failure(result.error)
        }
    }
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

class SupabaseAuthoritativeIdentityDataSource(
    private val client: SupabaseClient,
    private val remoteCalls: RemoteCallExecutor,
) : AuthoritativeIdentityDataSource {
    override suspend fun load(subject: String): RemoteResult<UserSession> = remoteCalls.execute(
        operation = RemoteOperation.LOAD_IDENTITY,
        requiresAuth = true,
    ) {
        val profiles = client.from("user_profiles").select(
            columns = Columns.raw("user_id,display_name,platform_role,disabled"),
        ) {
            limit(RemoteQueryWindow.SINGLETON_REQUEST_ROWS)
            filter { eq("user_id", subject) }
        }.decodeList<ProfileDto>()
        val profile = profiles.single()
        require(profile.userId == subject && !profile.disabled)

        if (profile.platformRole == "super_admin") {
            return@execute UserSession(
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
            limit(RemoteQueryWindow.SINGLETON_REQUEST_ROWS)
            filter {
                eq("user_id", subject)
                eq("active", true)
            }
        }.decodeList<MembershipDto>()
        val membership = memberships.single()
        require(membership.active)

        val shops = client.from("shops").select(columns = Columns.raw("id,active")) {
            limit(RemoteQueryWindow.SINGLETON_REQUEST_ROWS)
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
        UserSession(
            userId = subject,
            displayName = profile.displayName,
            role = role,
            shopId = membership.shopId,
        )
    }
}
