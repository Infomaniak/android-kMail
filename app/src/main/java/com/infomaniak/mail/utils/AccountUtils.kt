/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.CredentialManager
import com.infomaniak.lib.core.auth.TokenAuthenticator
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.room.UserDatabase
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.appSettings.AppSettingsController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.mailbox.Mailbox
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import io.sentry.protocol.User as SentryUser

object AccountUtils : CredentialManager() {

    const val NO_MAILBOX_USER_ID_KEY = "noMailboxUserId"

    override lateinit var userDatabase: UserDatabase

    var reloadApp: (suspend () -> Unit)? = null

    fun init(context: Context) {
        userDatabase = UserDatabase.getDatabase(context)

        Sentry.setUser(SentryUser().apply { id = currentUserId.toString() })
    }

    override var currentUser: User? = null
        set(user) {
            field = user
            currentUserId = user?.id ?: AppSettings.DEFAULT_ID
            Sentry.setUser(SentryUser().apply {
                id = currentUserId.toString()
                email = user?.email
            })
            InfomaniakCore.bearerToken = user?.apiToken?.accessToken.toString()
        }

    override var currentUserId: Int = AppSettingsController.getAppSettings().currentUserId
        set(userId) {
            field = userId
            RealmDatabase.resetUserInfo()
            AppSettingsController.updateAppSettings { appSettings -> appSettings.currentUserId = userId }
        }

    var currentMailboxId: Int = AppSettingsController.getAppSettings().currentMailboxId
        set(mailboxId) {
            field = mailboxId
            RealmDatabase.resetMailboxContent()
            AppSettingsController.updateAppSettings { appSettings -> appSettings.currentMailboxId = mailboxId }
        }

    var currentMailboxEmail: String? = null

    suspend fun switchToMailbox(mailboxId: Int) {
        RealmDatabase.backUpPreviousMailboxContent()
        currentMailboxId = mailboxId
        reloadApp?.invoke()
    }

    suspend fun manageMailboxesEdgeCases(context: Context, mailboxes: List<Mailbox>): Boolean {

        val shouldStop = when {
            mailboxes.isEmpty() -> {
                Dispatchers.Main { context.launchNoMailboxActivity(currentUserId, shouldStartLoginActivity = true) }
                true
            }
            mailboxes.none { it.isValid } -> {
                Dispatchers.Main { context.launchNoValidMailboxesActivity() }
                true
            }
            mailboxes.none { it.mailboxId == currentMailboxId } -> {
                reloadApp?.invoke()
                true
            }
            else -> false
        }

        return shouldStop
    }

    suspend fun requestCurrentUser(): User? {
        return (getUserById(currentUserId) ?: userDatabase.userDao().getFirst()).also { currentUser = it }
    }

    suspend fun addUser(user: User) {
        currentUser = user
        userDatabase.userDao().insert(user)
    }

    suspend fun updateCurrentUser(okHttpClient: OkHttpClient = HttpClient.okHttpClient) {
        with(ApiRepository.getUserProfile(okHttpClient)) {
            if (result != ApiResponseStatus.ERROR) requestUser(remoteUser = data ?: return)
        }
    }

    private suspend fun requestUser(remoteUser: User) {
        TokenAuthenticator.mutex.withLock {
            if (remoteUser.id == currentUserId) {
                remoteUser.organizations = arrayListOf()
                requestCurrentUser()?.let { localUser ->
                    setUserToken(remoteUser, localUser.apiToken)
                }
            }
        }
    }

    suspend fun removeUser(user: User) {
        userDatabase.userDao().delete(user)
    }

    fun getAllUsersSync(): List<User> = userDatabase.userDao().getAllSync()

}
