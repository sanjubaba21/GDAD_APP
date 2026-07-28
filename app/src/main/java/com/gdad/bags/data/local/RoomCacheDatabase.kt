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
        CachedManagedAccountEntity::class,
        CachedManagedShopEntity::class,
    ],
    version = RoomCacheDatabase.VERSION,
    exportSchema = true,
)
abstract class RoomCacheDatabase : RoomDatabase() {
    abstract fun identityDao(): CacheIdentityDao
    abstract fun readDao(): CacheReadDao
    abstract fun writeDao(): CacheWriteDao
    abstract fun outboxDao(): OutboxDao
    abstract fun accountDirectoryDao(): AccountDirectoryDao

    companion object {
        const val VERSION = 4
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `cached_managed_accounts` (`owner_user_id` TEXT NOT NULL, `owner_tenant_key` TEXT NOT NULL, `target_user_id` TEXT NOT NULL, `shop_id` TEXT NOT NULL, `login_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, `role` TEXT NOT NULL, `disabled` INTEGER NOT NULL, `membership_active` INTEGER NOT NULL, PRIMARY KEY(`owner_user_id`, `owner_tenant_key`, `target_user_id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_managed_accounts_owner_user_id_owner_tenant_key_role_display_name` ON `cached_managed_accounts` (`owner_user_id`, `owner_tenant_key`, `role`, `display_name`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `cached_managed_shops` (`owner_user_id` TEXT NOT NULL, `owner_tenant_key` TEXT NOT NULL, `shop_id` TEXT NOT NULL, `slug` TEXT NOT NULL, `display_name` TEXT NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`owner_user_id`, `owner_tenant_key`, `shop_id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_managed_shops_owner_user_id_owner_tenant_key_display_name` ON `cached_managed_shops` (`owner_user_id`, `owner_tenant_key`, `display_name`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cached_products` ADD COLUMN `low_stock_threshold` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun open(context: Context): RoomCacheDatabase =
            Room.databaseBuilder(context.applicationContext, RoomCacheDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
