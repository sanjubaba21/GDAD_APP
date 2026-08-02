package com.gdad.bags.data.purchase

import androidx.room.withTransaction
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedAccountEntity
import com.gdad.bags.data.local.CachedVendorEntity
import com.gdad.bags.data.local.RoomCacheDatabase
import com.gdad.bags.domain.purchase.PurchaseAccount
import com.gdad.bags.domain.purchase.PurchaseDirectory
import com.gdad.bags.domain.purchase.Vendor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class PurchaseDirectoryStore(private val database: RoomCacheDatabase) {
    fun observe(owner: CacheOwner): Flow<PurchaseDirectory> = combine(
        database.readDao().observeVendors(owner.userId, owner.tenantKey),
        database.readDao().observeAccounts(owner.userId, owner.tenantKey),
    ) { vendors, accounts ->
        PurchaseDirectory(
            vendors.map { Vendor(it.id, it.name, it.phone, it.taxReference, it.notes, it.duePaisa, it.active) },
            accounts.map { PurchaseAccount(it.id, it.name, it.type, it.balancePaisa, it.active) },
        )
    }

    suspend fun replace(owner: CacheOwner, vendors: List<CachedVendorEntity>, accounts: List<CachedAccountEntity>) =
        database.withTransaction {
            val identity = database.identityDao().get()
            require(identity?.userId == owner.userId && identity.tenantKey == owner.tenantKey)
            require((vendors + accounts).all { it.ownerUserId == owner.userId && it.ownerTenantKey == owner.tenantKey })
            database.writeDao().clearVendors()
            database.writeDao().clearAccounts()
            database.writeDao().putVendors(vendors)
            database.writeDao().putAccounts(accounts)
        }
}
