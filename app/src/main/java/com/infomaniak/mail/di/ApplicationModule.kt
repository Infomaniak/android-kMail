package com.infomaniak.mail.di

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {

    @Provides
    fun providesApplicationContext(@ApplicationContext context: Context) = context

    @Singleton
    @Provides
    fun providesNotificationManagerCompat(appContext: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(appContext)
    }

    @Provides
    fun providesWorkManager(appContext: Context) = WorkManager.getInstance(appContext)
}
