package com.gdad.bags.data.account

import androidx.room.withTransaction
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedManagedAccountEntity
import com.gdad.bags.data.local.CachedManagedShopEntity
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.ManagedAccount
import com.gdad.bags.domain.account.ManagedShop
import com.gdad.bags.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AccountDirectoryStore(private val database: RoomCacheDatabase) {
    private val dao get() = database.accountDirectoryDao()

    fun observe(owner: CacheOwner): Flow<AccountDirectory> = combine(
        dao.observeAccounts(owner.userId, owner.tenantKey),
        dao.observeShops(owner.userId, owner.tenantKey),
    ) { accounts, shops ->
        AccountDirectory(
            accounts.map { row ->
                ManagedAccount(
                    userId = row.targetUserId,
                    shopId = row.shopId,
                    loginId = row.loginId,
                    displayName = row.displayName,
                    role = UserRole.valueOf(row.role),
                    disabled = row.disabled,
                    membershipActive = row.membershipActive,
                )
            },
            shops.map { ManagedShop(it.shopId, it.slug, it.displayName, it.active) },
        )
    }

    suspend fun replace(owner: CacheOwner, directory: AccountDirectory) = database.withTransaction {
        val identity = database.identityDao().get()
        require(identity?.userId == owner.userId && identity.tenantKey == owner.tenantKey) {
            "Account directory owner is not the active cache identity"
        }
        dao.clearAccounts()
        dao.clearShops()
        dao.putAccounts(directory.accounts.map {
            CachedManagedAccountEntity(
                owner.userId, owner.tenantKey, it.userId, it.shopId, it.loginId,
                it.displayName, it.role.name, it.disabled, it.membershipActive,
            )
        })
        dao.putShops(directory.shops.map {
            CachedManagedShopEntity(owner.userId, owner.tenantKey, it.id, it.slug, it.displayName, it.active)
        })
    }

    suspend fun findAccount(owner: CacheOwner, targetUserId: String): ManagedAccount? =
        dao.getAccount(owner.userId, owner.tenantKey, targetUserId)?.let { row ->
            ManagedAccount(
                row.targetUserId, row.shopId, row.loginId, row.displayName,
                UserRole.valueOf(row.role), row.disabled, row.membershipActive,
            )
        }

    suspend fun isActiveShop(owner: CacheOwner, shopId: String): Boolean =
        dao.getShop(owner.userId, owner.tenantKey, shopId)?.active == true
}
