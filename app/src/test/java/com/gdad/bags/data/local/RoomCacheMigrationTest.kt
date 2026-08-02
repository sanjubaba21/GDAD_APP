package com.gdad.bags.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomCacheMigrationTest {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(DATABASE)
    }

    @Test
    fun migrationOneToTwoCreatesExpectedOutboxTableAndIndexes() {
        val db = helper.writableDatabase
        RoomCacheDatabase.MIGRATION_1_2.migrate(db)

        db.query("PRAGMA table_info(`mutation_outbox`)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(names.containsAll(setOf("idempotency_key", "owner_user_id", "owner_tenant_key", "payload_json", "state", "last_error_kind")))
        }
        db.query("PRAGMA index_list(`mutation_outbox`)").use { cursor ->
            var indexes = 0
            while (cursor.moveToNext()) indexes += 1
            assertEquals(3, indexes) // Two explicit indexes plus the primary-key auto-index.
        }
    }

    @Test
    fun migrationTwoToThreeCreatesOwnerScopedAccountDirectoryTables() {
        val db = helper.writableDatabase
        RoomCacheDatabase.MIGRATION_2_3.migrate(db)

        db.query("PRAGMA table_info(`cached_managed_accounts`)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(names.containsAll(setOf("owner_user_id", "owner_tenant_key", "target_user_id", "shop_id", "disabled")))
        }
        db.query("PRAGMA table_info(`cached_managed_shops`)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(names.containsAll(setOf("owner_user_id", "owner_tenant_key", "shop_id", "display_name", "active")))
        }
    }

    @Test
    fun migrationThreeToFourAddsProductLowStockThreshold() {
        val db = helper.writableDatabase
        db.execSQL("CREATE TABLE cached_products (id TEXT NOT NULL PRIMARY KEY)")

        RoomCacheDatabase.MIGRATION_3_4.migrate(db)

        db.query("PRAGMA table_info(`cached_products`)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(names.contains("low_stock_threshold"))
        }
    }

    @Test
    fun migrationFourToFiveAddsVendorDetailFields() {
        val db = helper.writableDatabase
        db.execSQL("CREATE TABLE cached_vendors (id TEXT NOT NULL PRIMARY KEY)")
        RoomCacheDatabase.MIGRATION_4_5.migrate(db)
        db.query("PRAGMA table_info(`cached_vendors`)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(names.containsAll(setOf("tax_reference", "notes")))
        }
    }

    @Test
    fun migrationFiveToSixAddsNotificationSourceFields() {
        val db = helper.writableDatabase
        db.execSQL("CREATE TABLE cached_notifications (id TEXT NOT NULL PRIMARY KEY)")
        RoomCacheDatabase.MIGRATION_5_6.migrate(db)
        db.query("PRAGMA table_info(`cached_notifications`)").use { cursor ->
            val defaults = buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")),
                    )
                }
            }
            assertTrue(defaults.keys.containsAll(setOf("shop_id", "record_type", "record_id")))
            assertEquals("''", defaults["shop_id"])
            assertEquals("'system'", defaults["record_type"])
        }
    }

    private companion object {
        const val DATABASE = "migration-1-2-test.db"
    }
}
