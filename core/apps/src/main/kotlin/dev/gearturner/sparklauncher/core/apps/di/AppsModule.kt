package dev.gearturner.sparklauncher.core.apps.di

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** a scope that lives as long as the process, for caches and system callbacks that outlive any screen */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppsModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideLauncherApps(@ApplicationContext context: Context): LauncherApps =
        context.getSystemService(LauncherApps::class.java)

    @Provides
    fun provideUserManager(@ApplicationContext context: Context): UserManager =
        context.getSystemService(UserManager::class.java)
}
