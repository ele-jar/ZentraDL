package com.elejar.ZentraDL.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TaskRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
