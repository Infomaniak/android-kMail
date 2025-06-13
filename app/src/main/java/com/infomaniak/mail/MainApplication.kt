/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.facebook.stetho.Stetho
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.AccessTokenUsageInterceptor
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpClientConfig
import com.infomaniak.lib.core.utils.CoilUtils
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.stores.AppUpdateScheduler
import com.infomaniak.mail.MatomoMail.buildTracker
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.UrlTraceInterceptor
import com.infomaniak.mail.data.cache.appSettings.AppSettingsController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.workers.SyncMailboxesWorker
import dagger.hilt.android.HiltAndroidApp
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.fragment.FragmentLifecycleIntegration
import io.sentry.android.fragment.FragmentLifecycleState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.matomo.sdk.Tracker
import java.util.UUID
import javax.inject.Inject

@HiltAndroidApp
open class MainApplication : Application(), ImageLoaderFactory, DefaultLifecycleObserver, Configuration.Provider {

    val matomoTracker: Tracker by lazy { buildTracker(shouldOptOut = !localSettings.isMatomoTrackingEnabled) }
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
                if (SDK_INT >= 26) detectContentUriWithoutPermission()
                if (SDK_INT >= 29) detectCredentialProtectedWhileLocked()
            }.build()
        )
    }

    private fun configureSentry() {
        SentryAndroid.init(this) { options: SentryAndroidOptions ->
            // Register the callback as an option
            options.beforeSend = SentryOptions.BeforeSendCallback { event: SentryEvent, _: Any? ->
                val exception = event.throwable
                /**
                 * Reasons to discard Sentry events :
                 * - Application is in Debug mode
                 * - User deactivated Sentry tracking in DataManagement settings
                 * - The exception was an [ApiController.NetworkException], and we don't want to send them to Sentry
                 * - The exception was an [ApiErrorException] with an [ErrorCode.ACCESS_DENIED] or
                 *   [ErrorCode.NOT_AUTHORIZED] error code, and we don't want to send them to Sentry
                 */
                when {
                    BuildConfig.DEBUG -> null
                    !localSettings.isSentryTrackingEnabled -> null
                    exception is ApiController.NetworkException -> null
                    exception is ApiErrorException && exception.errorCode == ErrorCode.ACCESS_DENIED -> null
                    exception is ApiErrorException && exception.errorCode == ErrorCode.NOT_AUTHORIZED -> null
                    else -> event
                }
            }
            options.addIntegration(
                FragmentLifecycleIntegration(
                    application = this,
                    filterFragmentLifecycleBreadcrumbs = setOf(
                        FragmentLifecycleState.CREATED,
                        FragmentLifecycleState.STARTED,
                        FragmentLifecycleState.RESUMED,
                        FragmentLifecycleState.PAUSED,
                        FragmentLifecycleState.STOPPED,
                        FragmentLifecycleState.DESTROYED,
                    ),
                    enableAutoFragmentLifecycleTracing = true,
                )
            )
        }
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
    }

    private fun configureHttpClient() {
        AccountUtils.onRefreshTokenError = refreshTokenError
        val tokenInterceptorListener = tokenInterceptorListener()
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

    private fun tokenInterceptorListener() = object : TokenInterceptorListener {
        @OptIn(ExperimentalCoroutinesApi::class)
        val userTokenFlow = AppSettingsController.getCurrentUserIdFlow()
            .mapLatest { id -> id?.let { AccountUtils.getUserById(it)?.apiToken } }
            .filterNotNull()
            .shareIn(globalCoroutineScope, SharingStarted.Lazily, replay = 1)

        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
            if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
            AccountUtils.setUserToken(AccountUtils.currentUser!!, apiToken)
        }

        override suspend fun onRefreshTokenError() {
            if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
            refreshTokenError(AccountUtils.currentUser!!)
        }

        override suspend fun getApiToken(): ApiToken? = userTokenFlow.first()

        override fun getCurrentUserId(): Int = AccountUtils.currentUserId
    }

    override fun newImageLoader(): ImageLoader = CoilUtils.newImageLoader(applicationContext, tokenInterceptorListener())

    fun createSvgImageLoader(): ImageLoader {
        return CoilUtils.newImageLoader(
            applicationContext,
            tokenInterceptorListener(),
            customFactories = listOf(SvgDecoder.Factory())
        )
    }

    companion object {
        private const val FIRST_LAUNCH_TIME = 0L
    }
}
