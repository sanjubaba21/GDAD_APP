package com.gdad.bags.domain.account

import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

data class ManagedShop(
    val id: String,
    val slug: String,
    val displayName: String,
    val active: Boolean,
)

data class ManagedAccount(
    val userId: String,
    val shopId: String,
    val loginId: String,
    val displayName: String,
    val role: UserRole,
    val disabled: Boolean,
    val membershipActive: Boolean,
)

data class AccountDirectory(
    val accounts: List<ManagedAccount> = emptyList(),
    val shops: List<ManagedShop> = emptyList(),
)

data class CreateManagedAccount(
    val loginId: String,
    val displayName: String,
    val pin: String,
    val shopId: String,
)

enum class AccountAction { DISABLE, ENABLE, RESET_PIN }

data class AdministerManagedAccount(
    val targetUserId: String,
    val action: AccountAction,
    val reauthPin: String,
    val newPin: String? = null,
)

sealed interface AccountOperationResult {
    data class Success(val safeMessage: String) : AccountOperationResult
    data class Failure(val error: RemoteFailure?, val safeMessage: String) : AccountOperationResult
}

interface AccountManagementRepository {
    fun observe(session: UserSession): Flow<AccountDirectory>
    suspend fun refresh(session: UserSession): AccountOperationResult
    suspend fun create(
        session: UserSession,
        requestId: String,
        input: CreateManagedAccount,
    ): AccountOperationResult
    suspend fun administer(
        session: UserSession,
        requestId: String,
        input: AdministerManagedAccount,
    ): AccountOperationResult
}
