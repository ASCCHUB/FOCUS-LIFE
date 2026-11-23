package com.focuslife.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.focuslife.app.data.FocusDao
import com.focuslife.app.data.FocusDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Inject
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory

@HiltAndroidApp class FocusLifeApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate() { super.onCreate(); getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("focuslife", "Focus Life reminders", NotificationManager.IMPORTANCE_DEFAULT)) }
}
@Module @InstallIn(SingletonComponent::class) object DataModule {
    @Provides @Singleton fun database(app: Application): FocusDatabase = FocusDatabase.get(app)
    @Provides fun dao(db: FocusDatabase): FocusDao = db.dao()
}
