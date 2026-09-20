package com.elejar.ZentraDL.di

import android.content.Context
import androidx.room.Room
import com.elejar.ZentraDL.data.SettingsStore
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.local.AppDatabase
import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.engine.http.HttpDownloader
import com.elejar.ZentraDL.engine.model.Downloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "zentradl.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideDownloader(): Downloader = HttpDownloader()

    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext ctx: Context): SettingsStore = SettingsStore(ctx)

    @Provides
    @Singleton
    fun provideRepository(
        dao: TaskDao,
        downloader: Downloader,
        settings: SettingsStore,
        appScope: CoroutineScope,
        @ApplicationContext ctx: Context,
    ): TaskRepository = TaskRepository(dao, downloader, settings.connections, settings.maxRunning, appScope, ctx.filesDir.resolve("downloads"))
}
