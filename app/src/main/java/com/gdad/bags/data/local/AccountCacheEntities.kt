package com.gdad.bags.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_managed_accounts",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "target_user_id"],
    indices = [Index(value = ["owner_user_id", "owner_tenant_key", "role", "display_name"])],
)
data class CachedManagedAccountEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    @ColumnInfo(name = "target_user_id") val targetUserId: String,
    @ColumnInfo(name = "shop_id") val shopId: String,
    @ColumnInfo(name = "login_id") val loginId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val role: String,
    val disabled: Boolean,
    @ColumnInfo(name = "membership_active") val membershipActive: Boolean,
) : OwnedCacheRow

@Entity(
    tableName = "cached_managed_shops",
    primaryKeys = ["owner_user_id", "owner_tenant_key", "shop_id"],
    indices = [Index(value = ["owner_user_id", "owner_tenant_key", "display_name"])],
)
data class CachedManagedShopEntity(
    @ColumnInfo(name = "owner_user_id") override val ownerUserId: String,
    @ColumnInfo(name = "owner_tenant_key") override val ownerTenantKey: String,
    @ColumnInfo(name = "shop_id") val shopId: String,
    val slug: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val active: Boolean,
) : OwnedCacheRow
