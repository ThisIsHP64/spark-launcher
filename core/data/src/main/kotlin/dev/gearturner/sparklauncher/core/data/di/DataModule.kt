package dev.gearturner.sparklauncher.core.data.di

import android.content.Context
import dev.gearturner.sparklauncher.core.data.db.MANUAL_MIGRATIONS
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gearturner.sparklauncher.core.data.ActiveLayout
import dev.gearturner.sparklauncher.core.data.SettingsActiveLayout
import dev.gearturner.sparklauncher.core.data.db.AppOverrideDao
import dev.gearturner.sparklauncher.core.data.db.SparkDatabase
import dev.gearturner.sparklauncher.core.data.db.WorkspaceDao
import dev.gearturner.sparklauncher.core.data.settings.SettingsSerializer
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataBindings {
    @Binds
    fun bindActiveLayout(impl: SettingsActiveLayout): ActiveLayout
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SparkDatabase =
        Room.databaseBuilder(context, SparkDatabase::class.java, SparkDatabase.NAME)
            .addMigrations(*MANUAL_MIGRATIONS)
            .build()

    @Provides
    fun provideWorkspaceDao(database: SparkDatabase): WorkspaceDao = database.workspaceDao()

    @Provides
    fun provideAppOverrideDao(database: SparkDatabase): AppOverrideDao = database.appOverrideDao()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<LauncherSettings> =
        DataStoreFactory.create(
            serializer = SettingsSerializer,
            // a corrupt file falls back to defaults instead of crashing the home screen on every boot
            corruptionHandler = ReplaceFileCorruptionHandler { LauncherSettings() },
            produceFile = { context.dataStoreFile("launcher_settings.json") },
        )
}
