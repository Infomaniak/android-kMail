/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.mail

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.facebook.stetho.Stetho
import com.infomaniak.core.auth.AuthConfiguration
import com.infomaniak.core.coil.ImageLoaderProvider
import com.infomaniak.core.network.NetworkConfiguration
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.core.sentryconfig.SentryConfig.configureSentry
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.AccessTokenUsageInterceptor
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpClientConfig
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.lib.stores.AppUpdateScheduler
import com.infomaniak.mail.TokenInterceptorListenerProvider.legacyTokenInterceptorListener
import com.infomaniak.mail.TokenInterceptorListenerProvider.tokenInterceptorListener
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.UrlTraceInterceptor
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ApiErrorException
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.LogoutUser
import com.infomaniak.mail.utils.MyKSuiteDataUtils
import com.infomaniak.mail.utils.NotificationUtils
import com.infomaniak.mail.utils.PlayServicesUtils
import com.infomaniak.mail.workers.SyncMailboxesWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.injectAsAppCtx
import java.util.UUID
import javax.inject.Inject

@HiltAndroidApp
open class MainApplication : Application(), SingletonImageLoader.Factory, DefaultLifecycleObserver, Configuration.Provider {

    init {
        injectAsAppCtx() // Ensures it is always initialized
    }

    var isAppInBackground = true
        private set

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var globalCoroutineScope: CoroutineScope

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var logoutUser: LogoutUser

    @Inject
    lateinit var notificationUtils: NotificationUtils

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var playServicesUtils: PlayServicesUtils

    @Inject
    lateinit var syncMailboxesWorkerScheduler: SyncMailboxesWorker.Scheduler

    @Inject
    lateinit var appUpdateWorkerScheduler: AppUpdateScheduler

    @Inject
    lateinit var myKSuiteDataUtils: MyKSuiteDataUtils

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super<Application>.onCreate()

        HttpClientConfig.cacheDir = applicationContext.cacheDir

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        if (BuildConfig.DEBUG) configureDebugMode()

        configureSentry()
        enforceAppTheme()
        configureRoomDatabases()
        configureAppReloading()
        configureInfomaniakCore()
        notificationUtils.initNotificationChannel()
        configureHttpClient()

        localSettings.storageBannerDisplayAppLaunches++
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInBackground = false
        syncMailboxesWorkerScheduler.cancelWork()
        owner.lifecycleScope.launch { appUpdateWorkerScheduler.cancelWorkIfNeeded() }
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInBackground = true
        owner.lifecycleScope.launch {
            syncMailboxesWorkerScheduler.scheduleWorkIfNeeded()
            appUpdateWorkerScheduler.scheduleWorkIfNeeded()
        }
    }

    private fun configureDebugMode() {
        Stetho.initializeWithDefaults(this)

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().apply {
                detectActivityLeaks()
                detectLeakedClosableObjects()
                detectLeakedRegistrationObjects()
                detectFileUriExposure()
                detectContentUriWithoutPermission()
                if (SDK_INT >= 29) detectCredentialProtectedWhileLocked()
            }.build()
        )

        MatomoMail.addTrackingCallbackForDebugLog()
    }


    /**
     * Reasons to discard Sentry events :
     * - The exception was an [ApiErrorException] with an [ErrorCode.ACCESS_DENIED] or
     * - [ErrorCode.NOT_AUTHORIZED] error code, and we don't want to send them to Sentry
     */
    private fun configureSentry() {
        this.configureSentry(
            isDebug = BuildConfig.DEBUG,
            isSentryTrackingEnabled = localSettings.isSentryTrackingEnabled,
            isFilteredException = { exception: Throwable? ->
                when {
                    exception is ApiErrorException && exception.errorCode == ErrorCode.ACCESS_DENIED -> true
                    exception is ApiErrorException && exception.errorCode == ErrorCode.NOT_AUTHORIZED -> true
                    else -> false
                }
            },
        )
    }

    private fun enforceAppTheme() {
        AppCompatDelegate.setDefaultNightMode(localSettings.theme.mode)
    }

    private fun configureRoomDatabases() {
        AccountUtils.init()
        myKSuiteDataUtils.initDatabase(this)
    }

    private fun configureAppReloading() {
        AccountUtils.reloadApp = { withContext(mainDispatcher) { startActivity(getLaunchIntent()) } }
    }

    private fun getLaunchIntent() = Intent(this, LaunchActivity::class.java).clearStack()

    private fun configureInfomaniakCore() {
        // Legacy configuration
        InfomaniakCore.apply {
            init(
                appId = BuildConfig.APPLICATION_ID,
                appVersionCode = BuildConfig.VERSION_CODE,
                appVersionName = BuildConfig.VERSION_NAME,
                clientId = BuildConfig.CLIENT_ID,
            )
            apiErrorCodes = ErrorCode.apiErrorCodes
            accessType = null
        }

        // New modules configuration
        NetworkConfiguration.init(
            appId = BuildConfig.APPLICATION_ID,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
        )

        AuthConfiguration.init(
            appId = BuildConfig.APPLICATION_ID,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            clientId = BuildConfig.CLIENT_ID,
        )
    }

    private fun configureHttpClient() {
        AccountUtils.onRefreshTokenError = refreshTokenError
        val tokenInterceptorListener = legacyTokenInterceptorListener(refreshTokenError, globalCoroutineScope)
        HttpClientConfig.customInterceptors = listOf(
            UrlTraceInterceptor(),
            AccessTokenUsageInterceptor(
                previousApiCall = localSettings.accessTokenApiCallRecord,
                updateLastApiCall = { localSettings.accessTokenApiCallRecord = it },
            ),
        )
        HttpClient.init(tokenInterceptorListener)
    }

    private val refreshTokenError: (User) -> Unit = { user ->
        val openAppIntent = getLaunchIntent()
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificationText = getString(R.string.refreshTokenError)

        if (hasPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))) {
            val builder = notificationUtils.buildGeneralNotification(notificationText).setContentIntent(pendingIntent)
            @Suppress("MissingPermission")
            notificationManagerCompat.notify(UUID.randomUUID().hashCode(), builder.build())
        } else {
            globalCoroutineScope.launch(mainDispatcher) { showToast(notificationText) }
        }

        globalCoroutineScope.launch(ioDispatcher) { logoutUser(user) }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoaderProvider.newImageLoader(context, tokenInterceptorListener(refreshTokenError, globalCoroutineScope))
    }

    companion object {
        private const val FIRST_LAUNCH_TIME = 0L
    }
}
