/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.mail.di

import android.app.Application
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.infomaniak.lib.stores.AppUpdateScheduler
import com.infomaniak.lib.stores.StoresLocalSettings
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.data.LocalSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {

    @Provides
    fun providesApplicationContext(@ApplicationContext context: Context) = context

    @Provides
    fun providesMainApplication(application: Application) = application as MainApplication

    @Provides
    @Singleton
    fun providesNotificationManagerCompat(appContext: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(appContext)
    }

    @Provides
    @Singleton
    fun providesWorkManager(appContext: Context) = WorkManager.getInstance(appContext)

    @Provides
    @Singleton
    fun providesGlobalCoroutineScope(@DefaultDispatcher defaultDispatcher: CoroutineDispatcher): CoroutineScope {
        return CoroutineScope(defaultDispatcher)
    }

    @Provides
    @Singleton
    fun providesLocalSettings(appContext: Context): LocalSettings = LocalSettings.getInstance(appContext)

    @Provides
    @Singleton
    fun providesStoresLocalSettings(appContext: Context): StoresLocalSettings = StoresLocalSettings.getInstance(appContext)

    @Provides
    @Singleton
    fun providesAppUpdateWorkerScheduler(
        appContext: Context,
        workManager: WorkManager,
    ): AppUpdateScheduler = AppUpdateScheduler(appContext, workManager)
}
