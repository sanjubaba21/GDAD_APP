package com.gdad.bags.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CacheIdentityEntity::class,
        CachedProfileEntity::class,
        CachedMembershipEntity::class,
        CachedProductEntity::class,
        CachedStockSummaryEntity::class,
        CachedVendorEntity::class,
        CachedRecentSaleEntity::class,
        CachedAccountEntity::class,
        CachedDashboardSummaryEntity::class,
        CachedNotificationEntity::class,
    ],
    version = RoomCacheDatabase.VERSION,
    exportSchema = true,
)
abstract class RoomCacheDatabase : RoomDatabase() {
    abstract fun identityDao(): CacheIdentityDao
    abstract fun readDao(): CacheReadDao
    abstract fun writeDao(): CacheWriteDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "gdad-cache.db"

        /** Add every future version transition here. Destructive fallback is forbidden. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun open(context: Context): RoomCacheDatabase =
            Room.databaseBuilder(context.applicationContext, RoomCacheDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
