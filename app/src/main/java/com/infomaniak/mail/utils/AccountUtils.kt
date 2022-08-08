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
package com.infomaniak.mail.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.CredentialManager
import com.infomaniak.lib.core.auth.TokenAuthenticator
import com.infomaniak.lib.core.auth.TokenInterceptor
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.room.UserDatabase
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.cache.RealmController
import com.infomaniak.mail.data.cache.appSettings.AppSettingsController
import com.infomaniak.mail.data.models.AppSettings
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import io.sentry.protocol.User as SentryUser

object AccountUtils : CredentialManager {

    private lateinit var userDatabase: UserDatabase

    var reloadApp: ((bundle: Bundle) -> Unit)? = null

    fun init(context: Context) {
        userDatabase = UserDatabase.getDatabase(context)

        Sentry.setUser(SentryUser().apply { id = currentUserId.toString() })
    }

    var currentUser: User? = null
        set(user) {
            field = user
            currentUserId = user?.id ?: AppSettings.DEFAULT_ID
            Sentry.setUser(SentryUser().apply {
                id = currentUserId.toString()
                email = user?.email
            })
            InfomaniakCore.bearerToken = user?.apiToken?.accessToken.toString()
        }

    var currentUserId: Int = AppSettingsController.getAppSettings().currentUserId
        set(userId) {
            field = userId
            RealmController.closeUserInfos()
            AppSettingsController.updateAppSettings { appSettings -> appSettings.currentUserId = userId }
        }

    var currentMailboxId: Int = AppSettingsController.getAppSettings().currentMailboxId
        set(mailboxId) {
            field = mailboxId
            RealmController.closeMailboxContent()
            AppSettingsController.updateAppSettings { appSettings -> appSettings.currentMailboxId = mailboxId }
        }

    suspend fun requestCurrentUser(): User? {
        currentUser = getUserById(currentUserId)
        if (currentUser == null) {
            currentUser = userDatabase.userDao().getFirst()
        }

        return currentUser
    }

    suspend fun addUser(user: User) {
        currentUser = user
        userDatabase.userDao().insert(user)
    }

    fun reloadApp() {
        CoroutineScope(Dispatchers.Main).launch { reloadApp?.invoke(bundleOf()) }
    }

    private suspend fun requestUser(user: User) {
        TokenAuthenticator.mutex.withLock {
            if (currentUserId == user.id) {
                user.apply {
                    organizations = arrayListOf()
                    requestCurrentUser()?.let { user ->
                        setUserToken(this, user.apiToken)
                        currentUser = this
                    }
                }
            }
        }
    }

    suspend fun removeUser(context: Context, user: User) {
        userDatabase.userDao().delete(user)
        // FileController.deleteUserDriveFiles(userRemoved.id) // TODO?

        if (currentUserId == user.id) {
            requestCurrentUser()

            resetApp(context)
            CoroutineScope(Dispatchers.Main).launch { reloadApp?.invoke(bundleOf()) }

            // CloudStorageProvider.notifyRootsChanged(context) // TODO?
        }
    }

    override fun getAllUsers(): LiveData<List<User>> = userDatabase.userDao().getAll()

    private fun getAllUserCount(): Int = userDatabase.userDao().count()

    fun getAllUsersSync(): List<User> = userDatabase.userDao().getAllSync()

    suspend fun setUserToken(user: User?, apiToken: ApiToken) {
        user?.let {
            it.apiToken = apiToken
            userDatabase.userDao().update(it)
        }
    }

    suspend fun getHttpClientUser(userId: Int, timeout: Long?, onRefreshTokenError: (user: User) -> Unit): OkHttpClient {
        var user = getUserById(userId)
        return OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addNetworkInterceptor(StethoInterceptor())
            }
            timeout?.let {
                callTimeout(timeout, TimeUnit.SECONDS)
                readTimeout(timeout, TimeUnit.SECONDS)
                writeTimeout(timeout, TimeUnit.SECONDS)
                connectTimeout(timeout, TimeUnit.SECONDS)
            }
            val tokenInterceptorListener = object : TokenInterceptorListener {
                override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
                    setUserToken(user, apiToken)
                    if (currentUserId == userId) {
                        currentUser = user
                    }
                }

                override suspend fun onRefreshTokenError() {
                    user?.let {
                        onRefreshTokenError(it)
                    }
                }

                override suspend fun getApiToken(): ApiToken {
                    user = getUserById(userId)
                    return user?.apiToken!!
                }
            }
            addInterceptor(TokenInterceptor(tokenInterceptorListener))
            authenticator(TokenAuthenticator(tokenInterceptorListener))
        }.run {
            build()
        }
    }

    suspend fun getUserById(id: Int): User? = userDatabase.userDao().findById(id)

    private fun resetApp(context: Context) {
        if (getAllUserCount() == 0) {
            AppSettingsController.removeAppSettings()
            // UiSettings(context).removeUiSettings() // TODO?

            // Delete all app data
            with(context) {
                filesDir.deleteRecursively()
                cacheDir.deleteRecursively()
            }
            Log.i("AccountUtils", "resetApp> all user data has been deleted")
        }
    }
}
