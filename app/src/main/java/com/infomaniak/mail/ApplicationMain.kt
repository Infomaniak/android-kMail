/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.mail.data.models.UiSettings
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.KMailHttpClient
import com.infomaniak.mail.utils.NotificationUtils.initNotificationChannel
import com.infomaniak.mail.utils.NotificationUtils.showGeneralNotification
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.*

class ApplicationMain : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) configureDebugMode()
        configureSentry()
        enforceAppTheme()
        configureAccountUtils()
        configureAppReloading()
        configureInfomaniakCore()
        initNotificationChannel()
        configureHttpClient()
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
            options.beforeSend = SentryOptions.BeforeSendCallback { event: SentryEvent?, _: Any? ->
                // If the application is in debug mode, discard the events
                if (BuildConfig.DEBUG) null else event
            }
        }
    }

    private fun enforceAppTheme() {
        AppCompatDelegate.setDefaultNightMode(UiSettings(this).nightMode)
    }

    private fun configureAccountUtils() {
        AccountUtils.init(this@ApplicationMain)
    }

    private fun configureAppReloading() {
        AccountUtils.reloadApp = { startActivity(Intent(this, LaunchActivity::class.java).clearStack()) }
    }

    private fun configureInfomaniakCore() {
        InfomaniakCore.init(
            appVersionName = BuildConfig.VERSION_NAME,
            clientId = BuildConfig.CLIENT_ID,
            credentialManager = null,
            isDebug = BuildConfig.DEBUG,
        )
    }

    private fun configureHttpClient() {
        KMailHttpClient.onRefreshTokenError = refreshTokenError
        HttpClient.init(tokenInterceptorListener())
    }

    private val refreshTokenError: (User) -> Unit = { user ->
        val openAppIntent = Intent(this, LaunchActivity::class.java).clearStack()
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notificationManagerCompat = NotificationManagerCompat.from(this)
        showGeneralNotification(getString(R.string.refreshTokenError)).apply {
            setContentIntent(pendingIntent)
            notificationManagerCompat.notify(UUID.randomUUID().hashCode(), build())
        }

        CoroutineScope(Dispatchers.IO).launch { AccountUtils.removeUser(this@ApplicationMain, user) }
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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .crossfade(true)
            .okHttpClient {
                OkHttpClient.Builder().apply {
                    addInterceptor(Interceptor { chain ->
                        chain.request().newBuilder().headers(HttpUtils.getHeaders())
                            .removeHeader("Cache-Control")
                            .removeHeader("Authorization")
                            .build()
                            .let(chain::proceed)
                    })
                    if (com.infomaniak.lib.core.BuildConfig.DEBUG) {
                        addNetworkInterceptor(StethoInterceptor())
                    }
                }.build()
            }
            .memoryCache {
                MemoryCache.Builder(applicationContext).build()
            }
            .diskCache {
                DiskCache.Builder().directory(applicationContext.cacheDir.resolve(COIL_CACHE_DIR)).build()
            }
            .build()
    }

    private companion object {
        const val COIL_CACHE_DIR = "coil_cache"
    }
}
