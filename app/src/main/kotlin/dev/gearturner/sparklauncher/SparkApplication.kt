package dev.gearturner.sparklauncher

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SparkApplication : Application() {

    @Inject lateinit var janitor: WorkspaceJanitor

    override fun onCreate() {
        super.onCreate()
        janitor.start()
    }
}
