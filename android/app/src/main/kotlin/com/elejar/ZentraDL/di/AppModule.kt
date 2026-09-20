package com.elejar.ZentraDL.di

import android.content.Context
import androidx.room.Room
import com.elejar.ZentraDL.data.GateMonitor
import com.elejar.ZentraDL.data.SettingsStore
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.TorrentRepository
import com.elejar.ZentraDL.data.local.AppDatabase
import com.elejar.ZentraDL.data.local.CategoryDao
import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TorrentTaskDao
import com.elejar.ZentraDL.engine.http.HttpDownloader
import com.elejar.ZentraDL.engine.http.SpeedLimiter
import com.elejar.ZentraDL.engine.model.Downloader
import com.elejar.ZentraDL.engine.torrent.BtEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "zentradl.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTorrentTaskDao(db: AppDatabase): TorrentTaskDao = db.torrentTaskDao()

    @Provides
    @Singleton
    fun provideBtEngine(settings: SettingsStore, appScope: CoroutineScope): BtEngine {
        val engine = BtEngine()
        // Live for NEW sessions (running ones keep their config).
        appScope.launch {
            combine(settings.dhtEnabled, settings.maxPeers) { dht, max ->
                BtOptions(enableDht = dht, maxPeersPerTorrent = max)
            }.collect { engine.opts = it }
        }
        return engine
    }

    @Provides
    @Singleton
    fun provideTorrentRepository(
        dao: TaskDao,
        torrentDao: TorrentTaskDao,
        categoryDao: CategoryDao,
        engine: BtEngine,
        appScope: CoroutineScope,
        settings: SettingsStore,
        @ApplicationContext ctx: Context,
    ): TorrentRepository = TorrentRepository(
        dao, torrentDao, categoryDao, engine, appScope, ctx.filesDir, settings.seedGoal,
    )

    @Provides
    @Singleton
    fun provideDownloader(settings: SettingsStore, appScope: CoroutineScope): Downloader {
        val limiter = SpeedLimiter()
        // Global cap follows settings (KB/s → B/s); 0 = unlimited.
        appScope.launch { settings.speedLimitKbps.collect { limiter.limitBps.set(it * 1024L) } }
        return HttpDownloader(limiter = limiter)
    }

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
        categoryDao: CategoryDao,
        monitor: GateMonitor,
    ): TaskRepository = TaskRepository(
        dao, downloader, settings.connections, settings.maxRunning, appScope,
        ctx.filesDir.resolve("downloads"), categoryDao, settings.gatePolicy, monitor.state,
    )
}
