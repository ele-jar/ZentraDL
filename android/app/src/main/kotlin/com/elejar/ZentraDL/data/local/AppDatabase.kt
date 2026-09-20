package com.elejar.ZentraDL.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskRecord::class, Category::class, TorrentTask::class, Bookmark::class, HistoryEntry::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun torrentTaskDao(): TorrentTaskDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao

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

        /** P4 schema: torrent extras table + task kind. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS torrent_tasks (" +
                        "id TEXT NOT NULL PRIMARY KEY, magnet TEXT, torrentPath TEXT, " +
                        "selectedPaths TEXT NOT NULL DEFAULT '', sequential INTEGER NOT NULL DEFAULT 0, " +
                        "name TEXT NOT NULL DEFAULT '', sizeBytes INTEGER NOT NULL DEFAULT -1)",
                )
                db.execSQL("ALTER TABLE tasks ADD COLUMN kind TEXT NOT NULL DEFAULT 'http'")
            }
        }

        /** P5 schema: browser bookmarks/history + task extras. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bookmarks (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, " +
                        "url TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS history (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, " +
                        "url TEXT NOT NULL, visitedAt INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE tasks ADD COLUMN extra TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
