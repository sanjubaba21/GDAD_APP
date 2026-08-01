package com.gdad.bags.data.account

import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteHttpException
import com.gdad.bags.data.remote.RemoteOperation
import com.gdad.bags.data.remote.RemoteQueryWindow
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.data.remote.requireSupportedWindow
import com.gdad.bags.domain.account.AccountAction
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.domain.account.ManagedAccount
import com.gdad.bags.domain.account.ManagedShop
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface AccountRemoteDataSource {
    suspend fun load(session: UserSession): RemoteResult<AccountDirectory>
    suspend fun create(session: UserSession, requestId: String, input: CreateManagedAccount): RemoteResult<Unit>
    suspend fun administer(requestId: String, input: AdministerManagedAccount): RemoteResult<Unit>
}

class SupabaseAccountRemoteDataSource(
    private val client: SupabaseClient,
    private val remoteCalls: RemoteCallExecutor,
) : AccountRemoteDataSource {
    override suspend fun load(session: UserSession): RemoteResult<AccountDirectory> = remoteCalls.execute(
        RemoteOperation.LOAD_ACCOUNT_DIRECTORY,
        requiresAuth = true,
    ) {
        require(session.role != UserRole.SALESMAN)
        val memberships = client.from("shop_memberships").select(
            Columns.raw("shop_id,user_id,role,active"),
        ) {
            limit(RemoteQueryWindow.REQUEST_ROWS)
            order("user_id", Order.ASCENDING)
            filter {
                when (session.role) {
                    UserRole.SUPER_ADMIN -> eq("role", "owner")
                    UserRole.OWNER -> {
                        eq("role", "salesman")
                        eq("shop_id", requireNotNull(session.shopId))
                    }
                    UserRole.SALESMAN -> error("Salesmen cannot load account administration")
                }
            }
        }.decodeList<DirectoryMembershipDto>()
            .requireSupportedWindow("account memberships")
        val profiles = client.from("user_profiles").select(
            Columns.raw("user_id,login_id,display_name,platform_role,disabled"),
        ) {
            limit(RemoteQueryWindow.REQUEST_ROWS)
            order("user_id", Order.ASCENDING)
        }.decodeList<DirectoryProfileDto>()
            .requireSupportedWindow("account profiles")
            .associateBy { it.userId }
        val shops = client.from("shops").select(
            Columns.raw("id,slug,display_name,active"),
        ) {
            limit(RemoteQueryWindow.REQUEST_ROWS)
            order("id", Order.ASCENDING)
        }.decodeList<DirectoryShopDto>().requireSupportedWindow("managed shops")

        AccountDirectory(
            accounts = memberships.map { membership ->
                val profile = requireNotNull(profiles[membership.userId])
                ManagedAccount(
                    membership.userId,
                    membership.shopId,
                    profile.loginId,
                    profile.displayName,
                    if (membership.role == "owner") UserRole.OWNER else UserRole.SALESMAN,
                    profile.disabled,
                    membership.active,
                )
            },
            shops = shops.map { ManagedShop(it.id, it.slug, it.displayName, it.active) },
        )
    }

    override suspend fun create(
        session: UserSession,
        requestId: String,
        input: CreateManagedAccount,
    ): RemoteResult<Unit> = remoteCalls.execute(RemoteOperation.PROVISION_ACCOUNT, true) {
        val action = when (session.role) {
            UserRole.SUPER_ADMIN -> "create_owner"
            UserRole.OWNER -> "create_salesman"
            UserRole.SALESMAN -> error("Salesmen cannot provision accounts")
        }
        val response = client.functions.invoke(
            "manage-users",
            ProvisionAccountRequestDto(action, requestId, input.loginId, input.displayName, input.pin, input.shopId),
        )
        if (!response.status.isSuccess()) throw RemoteHttpException(response.status.value)
        val result = response.body<ProvisionAccountResponseDto>()
        require(result.code == "ACCOUNT_PROVISIONED" && result.loginId == input.loginId && result.shopId == input.shopId)
        Unit
    }

    override suspend fun administer(
        requestId: String,
        input: AdministerManagedAccount,
    ): RemoteResult<Unit> = remoteCalls.execute(RemoteOperation.ADMINISTER_ACCOUNT, true) {
        val response = client.functions.invoke(
            "manage-accounts",
            AdministerAccountRequestDto(
                action = when (input.action) {
                    AccountAction.DISABLE -> "disable_user"
                    AccountAction.ENABLE -> "enable_user"
                    AccountAction.RESET_PIN -> "reset_pin"
                },
                requestId = requestId,
                targetUserId = input.targetUserId,
                reauthPin = input.reauthPin,
                newPin = input.newPin,
            ),
        )
        if (!response.status.isSuccess()) throw RemoteHttpException(response.status.value)
        val result = response.body<AdministerAccountResponseDto>()
        require(
            result.code == "ACCOUNT_UPDATED" && result.requestId == requestId &&
                result.targetUserId == input.targetUserId,
        )
        Unit
    }
}

@Serializable private data class DirectoryMembershipDto(
    @SerialName("shop_id") val shopId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    val active: Boolean,
)
@Serializable private data class DirectoryProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("login_id") val loginId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("platform_role") val platformRole: String,
    val disabled: Boolean,
)
@Serializable private data class DirectoryShopDto(
    val id: String,
    val slug: String,
    @SerialName("display_name") val displayName: String,
    val active: Boolean,
)
@Serializable private data class ProvisionAccountRequestDto(
    val action: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("login_id") val loginId: String,
    @SerialName("display_name") val displayName: String,
    val pin: String,
    @SerialName("shop_id") val shopId: String,
)
@Serializable private data class ProvisionAccountResponseDto(
    val code: String,
    val status: String,
    @SerialName("user_id") val userId: String,
    @SerialName("login_id") val loginId: String,
    val role: String,
    @SerialName("shop_id") val shopId: String?,
)
@Serializable private data class AdministerAccountRequestDto(
    val action: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("reauth_pin") val reauthPin: String,
    @SerialName("new_pin") val newPin: String? = null,
)
@Serializable private data class AdministerAccountResponseDto(
    val code: String,
    val status: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("target_user_id") val targetUserId: String,
    val action: String,
    val disabled: Boolean,
)
