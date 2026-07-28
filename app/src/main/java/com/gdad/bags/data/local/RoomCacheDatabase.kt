package com.gdad.bags.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        OutboxEntity::class,
    ],
    version = RoomCacheDatabase.VERSION,
    exportSchema = true,
)
abstract class RoomCacheDatabase : RoomDatabase() {
    abstract fun identityDao(): CacheIdentityDao
    abstract fun readDao(): CacheReadDao
    abstract fun writeDao(): CacheWriteDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "gdad-cache.db"

        /** Add every future version transition here. Destructive fallback is forbidden. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `mutation_outbox` (`idempotency_key` TEXT NOT NULL, `owner_user_id` TEXT NOT NULL, `owner_tenant_key` TEXT NOT NULL, `operation` TEXT NOT NULL, `payload_json` TEXT NOT NULL, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, `attempt_count` INTEGER NOT NULL, `state` TEXT NOT NULL, `next_attempt_at_epoch_ms` INTEGER NOT NULL, `last_error_kind` TEXT, PRIMARY KEY(`idempotency_key`))""",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_mutation_outbox_owner_user_id_owner_tenant_key_state_next_attempt_at_epoch_ms` ON `mutation_outbox` (`owner_user_id`, `owner_tenant_key`, `state`, `next_attempt_at_epoch_ms`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_mutation_outbox_state_updated_at_epoch_ms` ON `mutation_outbox` (`state`, `updated_at_epoch_ms`)")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun open(context: Context): RoomCacheDatabase =
            Room.databaseBuilder(context.applicationContext, RoomCacheDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
