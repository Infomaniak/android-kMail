/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.stores.StoresSettingsRepository
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.appSettings.AppSettingsController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.extensions.getInfomaniakLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogoutUser @Inject constructor(
    private val appContext: Context,
    private val globalCoroutineScope: CoroutineScope,
    private val localSettings: LocalSettings,
    private val mailboxController: MailboxController,
    private val playServicesUtils: PlayServicesUtils,
    private val storesSettingsRepository: StoresSettingsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(user: User, shouldReload: Boolean = true) {

        user.logoutToken()
        AccountUtils.removeUser(user)
        MyKSuiteDataUtils.deleteKSuiteData(user.id)
        RealmDatabase.removeUserData(appContext, user.id)
        mailboxController.deleteUserMailboxes(user.id)
        localSettings.removeRegisteredFirebaseUser(userId = user.id)

        if (user.id == AccountUtils.currentUserId) {
            AccountUtils.currentUser = null // Inform that there is no more current user

            if (AccountUtils.getAllUsersCount() == 0) {
                resetSettings()
                playServicesUtils.deleteFirebaseToken()
            } else {
                updateCurrentMailboxId()
            }
            if (shouldReload) AccountUtils.reloadApp?.invoke()
        }
    }

    private suspend fun updateCurrentMailboxId() {
        mailboxController.getFirstValidMailbox(AccountUtils.requestCurrentUser()!!.id)?.mailboxId?.let {
            AccountUtils.currentMailboxId = it
        }
    }

    private fun User.logoutToken() = globalCoroutineScope.launch(ioDispatcher) {
        runCatching {
            appContext.getInfomaniakLogin().deleteToken(
                okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                token = apiToken,
            )?.let { errorStatus ->
                SentryLog.i(TAG, "API response error: $errorStatus")
            }
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            SentryLog.e(TAG, "Failure on logoutToken ", exception)
        }
    }

    private suspend fun resetSettings() {
        AppSettingsController.removeAppSettings()
        localSettings.removeSettings()
        storesSettingsRepository.clear()
        with(WorkManager.getInstance(appContext)) {
            cancelAllWork()
            pruneWork()
        }
        // Dismiss all current notifications
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    companion object {
        private val TAG = LogoutUser::class.java.simpleName
    }
}
