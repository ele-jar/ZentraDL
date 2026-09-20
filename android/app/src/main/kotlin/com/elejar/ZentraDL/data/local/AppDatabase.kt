package com.elejar.ZentraDL.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskRecord::class, Category::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN error TEXT")
            }
        }

        /** P3 schema: categories table + task category/vault/checksum columns. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories (" +
                        "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                        "folder TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE tasks ADD COLUMN categoryId TEXT NOT NULL DEFAULT 'other'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN vaulted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN expectedSha256 TEXT")
            }
        }
    }
}
