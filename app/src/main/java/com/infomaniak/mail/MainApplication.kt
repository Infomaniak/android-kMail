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
import android.os.Build
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
import com.facebook.stetho.Stetho
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matomo.sdk.Tracker
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltAndroidApp
open class MainApplication : Application(), ImageLoaderFactory, DefaultLifecycleObserver, Configuration.Provider {

    val matomoTracker: Tracker by lazy { buildTracker(shouldOptOut = !localSettings.isMatomoTrackingEnabled) }
    var isAppInBackground = true
        private set
    var lastAppClosingTime: Long? = FIRST_LAUNCH_TIME
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
        configureAccountUtils()
        configureAppReloading()
        configureInfomaniakCore()
        notificationUtils.initNotificationChannel()
        configureHttpClient()
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInBackground = false
        syncMailboxesWorkerScheduler.cancelWork()
        owner.lifecycleScope.launch { appUpdateWorkerScheduler.cancelWorkIfNeeded() }
    }

    override fun onStop(owner: LifecycleOwner) {
        lastAppClosingTime = Date().time
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detectContentUriWithoutPermission()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) detectCredentialProtectedWhileLocked()
            }.build()
        )
    }

    private fun configureSentry() {
        SentryAndroid.init(this) { options: SentryAndroidOptions ->
            // Register the callback as an option
            options.beforeSend = SentryOptions.BeforeSendCallback { event: SentryEvent, _: Any? ->
                val isNetworkException = event.exceptions?.any { it.type == "ApiController\$NetworkException" } ?: false
                /**
                 * Reasons to discard Sentry events :
                 * - Application is in Debug mode
                 * - User deactivated Sentry tracking in DataManagement settings
                 * - The exception was a NetworkException, and we don't want to send them to Sentry
                 */
                if (!BuildConfig.DEBUG && localSettings.isSentryTrackingEnabled && !isNetworkException) event else null
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

    private fun configureAccountUtils() {
        AccountUtils.init(this)
    }

    private fun configureAppReloading() {
        AccountUtils.reloadApp = { withContext(mainDispatcher) { startActivity(getLaunchIntent()) } }
    }

    private fun getLaunchIntent() = Intent(this, LaunchActivity::class.java).apply {
        clearStack()
    }

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
        HttpClientConfig.customInterceptors = listOf(UrlTraceInterceptor())
        HttpClient.init(tokenInterceptorListener())
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
        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
            AccountUtils.setUserToken(AccountUtils.currentUser!!, apiToken)
        }

        override suspend fun onRefreshTokenError() {
            refreshTokenError(AccountUtils.currentUser!!)
        }

        override suspend fun getApiToken(): ApiToken = AccountUtils.currentUser!!.apiToken
    }

    override fun newImageLoader(): ImageLoader = CoilUtils.newImageLoader(applicationContext, tokenInterceptorListener())

    fun resetLastAppClosing() {
        lastAppClosingTime = null
    }

    companion object {
        private const val FIRST_LAUNCH_TIME = 0L
    }
}
