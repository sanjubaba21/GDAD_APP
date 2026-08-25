package com.gdad.bags.data.account

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.account.AccountAction
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.AccountManagementRepository
import com.gdad.bags.domain.account.AccountOperationResult
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.domain.account.CreateManagedShop
import com.gdad.bags.domain.account.DeleteManagedShop
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ProductionAccountManagementRepository(
    private val remote: AccountRemoteDataSource,
    private val store: AccountDirectoryStore,
) : AccountManagementRepository {
    override fun observe(session: UserSession): Flow<AccountDirectory> = store.observe(session.owner())

    override suspend fun refresh(session: UserSession): AccountOperationResult {
        if (session.role == UserRole.SALESMAN) return denied()
        return when (val result = remote.load(session)) {
            is RemoteResult.Failure -> result.error.toFailure("Unable to refresh accounts.")
            is RemoteResult.Success -> {
                store.replace(session.owner(), result.value)
                AccountOperationResult.Success("Accounts refreshed.")
            }
        }
    }

    override suspend fun createShop(
        session: UserSession,
        requestId: String,
        input: CreateManagedShop,
    ): AccountOperationResult {
        if (session.role != UserRole.SUPER_ADMIN) return deniedShop()
        if (!requestId.isUuid() || !input.isValid()) return invalidShop()
        return when (val result = remote.createShop(session, requestId, input)) {
            is RemoteResult.Failure -> result.error.toFailure("Unable to create the shop.")
            is RemoteResult.Success -> refreshAfterMutation(
                session,
                "Shop created with system accounts and an immutable audit record.",
            )
        }
    }

    override suspend fun create(
        session: UserSession,
        requestId: String,
        input: CreateManagedAccount,
    ): AccountOperationResult {
        if (session.role == UserRole.SALESMAN) return denied()
        if (session.role == UserRole.OWNER && input.shopId != session.shopId) return denied()
        if (!requestId.isUuid() || !input.isValid()) return invalid()
        if (session.role == UserRole.SUPER_ADMIN && !store.isActiveShop(session.owner(), input.shopId)) {
            return AccountOperationResult.Failure(null, "Select an active shop.")
        }
        return when (val result = remote.create(session, requestId, input)) {
            is RemoteResult.Failure -> result.error.toFailure(
                "Unable to create the account.",
                conflictMessage = "This Login ID is already in use. Choose a different Login ID.",
            )
            is RemoteResult.Success -> refreshAfterMutation(
                session,
                if (session.role == UserRole.SUPER_ADMIN) "Owner account created and audited."
                else "Salesman account created and audited.",
            )
        }
    }

    override suspend fun deleteShop(
        session: UserSession,
        requestId: String,
        input: DeleteManagedShop,
    ): AccountOperationResult {
        if (session.role != UserRole.SUPER_ADMIN) return deniedShop()
        if (!requestId.isUuid() || !input.isValid()) return invalidShopDeletion()
        val target = store.findShop(session.owner(), input.shopId) ?: return deniedShop()
        if (!target.active || target.slug != input.confirmationSlug) return invalidShopDeletion()
        return when (val result = remote.deleteShop(session, requestId, input)) {
            is RemoteResult.Failure -> result.error.toShopDeletionFailure()
            is RemoteResult.Success -> refreshAfterMutation(
                session,
                "Shop deleted; its records and shop-only managed access were removed.",
            )
        }
    }

    override suspend fun administer(
        session: UserSession,
        requestId: String,
        input: AdministerManagedAccount,
    ): AccountOperationResult {
        if (session.role == UserRole.SALESMAN || !requestId.isUuid() || !input.isValid()) {
            return if (session.role == UserRole.SALESMAN) denied() else invalid()
        }
        val target = store.findAccount(session.owner(), input.targetUserId) ?: return denied()
        val targetAllowed = when (session.role) {
            UserRole.SUPER_ADMIN -> target.role == UserRole.OWNER
            UserRole.OWNER -> target.role == UserRole.SALESMAN && target.shopId == session.shopId
            UserRole.SALESMAN -> false
        }
        if (!targetAllowed) return denied()
        return when (val result = remote.administer(session, requestId, input)) {
            is RemoteResult.Failure -> result.error.toFailure("Unable to update the account.")
            is RemoteResult.Success -> refreshAfterMutation(
                session,
                when (input.action) {
                    AccountAction.DISABLE -> "Account disabled; refresh sessions were revoked."
                    AccountAction.ENABLE -> "Account re-enabled; the user may sign in again."
                    AccountAction.RESET_PIN -> "PIN reset; refresh sessions were revoked."
                },
            )
        }
    }

    private fun CreateManagedAccount.isValid(): Boolean =
        loginId.matches(Regex("^[a-z0-9][a-z0-9._-]{2,63}$")) &&
            displayName.trim().length in 1..120 && pin.matches(PIN) && shopId.isUuid()

    private fun CreateManagedShop.isValid(): Boolean =
        slug.matches(SHOP_SLUG) && displayName.trim().length in 1..120

    private fun AdministerManagedAccount.isValid(): Boolean =
        targetUserId.isUuid() && reauthPin.matches(VERIFICATION_PIN) &&
            (action != AccountAction.RESET_PIN || newPin?.matches(PIN) == true)

    private fun DeleteManagedShop.isValid(): Boolean =
        shopId.isUuid() && confirmationSlug.matches(SHOP_SLUG) &&
            reason == reason.trim() && reason.length in 8..500 && reauthPin.matches(VERIFICATION_PIN)

    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

    private suspend fun refreshAfterMutation(
        session: UserSession,
        successMessage: String,
    ): AccountOperationResult = when (refresh(session)) {
        is AccountOperationResult.Success -> AccountOperationResult.Success(successMessage)
        is AccountOperationResult.Failure -> AccountOperationResult.Success(
            "$successMessage Account list refresh is pending; tap Refresh.",
        )
    }

    private fun RemoteFailure.toFailure(
        defaultMessage: String,
        conflictMessage: String = "This account changed. Refresh and try again.",
    ) = AccountOperationResult.Failure(
        this,
        when (kind) {
            RemoteErrorKind.UNAUTHORIZED -> if (statusCode == 401) {
                "Your admin session could not be verified. Sign out and sign in again."
            } else {
                "You are not allowed to manage this account."
            }
            RemoteErrorKind.VALIDATION -> "Review the entered account details."
            RemoteErrorKind.CONFLICT -> conflictMessage
            RemoteErrorKind.OFFLINE -> "Connect to the internet and try again."
            RemoteErrorKind.TIMEOUT -> "The request timed out. Try again."
            RemoteErrorKind.RATE_LIMITED -> "Too many attempts. Wait before trying again."
            RemoteErrorKind.UNKNOWN -> defaultMessage
        },
    )

    private fun RemoteFailure.toShopDeletionFailure() = AccountOperationResult.Failure(
        this,
        when (kind) {
            RemoteErrorKind.UNAUTHORIZED -> if (statusCode == 401) {
                "Your Super Admin session could not be verified. Sign out and sign in again."
            } else {
                "Shop deletion was denied. Review the shop slug and your PIN."
            }
            RemoteErrorKind.VALIDATION -> "Review the shop slug, reason, and your PIN."
            RemoteErrorKind.CONFLICT -> "This shop changed. Refresh and review it before retrying."
            RemoteErrorKind.OFFLINE -> "Connect to the internet before deleting a shop."
            RemoteErrorKind.TIMEOUT -> "Shop deletion timed out. Retry with the same confirmation."
            RemoteErrorKind.RATE_LIMITED -> "Too many deletion attempts. Wait before trying again."
            RemoteErrorKind.UNKNOWN -> "Unable to delete the shop safely. Try again."
        },
    )

    private fun denied() = AccountOperationResult.Failure(null, "You are not allowed to manage accounts.")
    private fun invalid() = AccountOperationResult.Failure(null, "Review the entered account details.")
    private fun deniedShop() = AccountOperationResult.Failure(null, "You are not allowed to manage shops.")
    private fun invalidShop() = AccountOperationResult.Failure(null, "Review the entered shop details.")
    private fun invalidShopDeletion() = AccountOperationResult.Failure(
        null,
        "Review the exact shop slug, reason, and your PIN.",
    )
    private fun UserSession.owner() = CacheOwner(userId, shopId)

    private companion object {
        val PIN = Regex("^\\d{6,8}$")
        val VERIFICATION_PIN = Regex("^\\d{4,8}$")
        val SHOP_SLUG = Regex("^[a-z0-9][a-z0-9-]{2,62}$")
    }
}
