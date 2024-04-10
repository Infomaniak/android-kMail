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
package com.infomaniak.mail.ui.sync

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@HiltViewModel
class SyncAutoConfigViewModel @Inject constructor(
    application: Application,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val snackbarManager: SnackbarManager,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var credentialsJob: Job? = null

    fun isSyncAppUpToDate(): Boolean = runCatching {
        val packageInfo = appContext.packageManager.getPackageInfo(SYNC_PACKAGE, PackageManager.GET_ACTIVITIES)

        val versionCode = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            packageInfo.versionCode.toLong()
        } else {
            packageInfo.longVersionCode
        }

        versionCode >= SYNC_MINIMUM_VERSION
    }.getOrElse {
        false
    }

    fun configureUserAutoSync(launchAutoSyncIntent: (Intent) -> Unit) {
        credentialsJob?.cancel()
        credentialsJob = viewModelScope.launch(ioCoroutineContext) {
            fetchCredentials(scope = this)?.let { password ->
                setupAutoSyncIntent(password, launchAutoSyncIntent)
            }
        }
    }

    private fun fetchCredentials(scope: CoroutineScope): String? {

        val apiResponse = ApiRepository.getCredentialsPassword()
        scope.ensureActive()

        return apiResponse.data?.password.also { password ->
            if (password == null) snackbarManager.postValue(appContext.getString(R.string.errorGetCredentials))
        }
    }

    private fun setupAutoSyncIntent(infomaniakPassword: String, launchAutoSyncIntent: (Intent) -> Unit) {

        val infomaniakLogin = AccountUtils.currentUser?.login

        if (infomaniakLogin?.isNotEmpty() == true && infomaniakPassword.isNotEmpty()) {
            Intent().apply {
                component = ComponentName(SYNC_PACKAGE, SYNC_CLASS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                action = SYNC_ACTION
                putExtra(SYNC_LOGIN_KEY, infomaniakLogin)
                putExtra(SYNC_PASSWORD_KEY, infomaniakPassword)
            }.also(launchAutoSyncIntent)
        } else {
            snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
            SentryDebug.sendCredentialsIssue(infomaniakLogin, infomaniakPassword)
        }
    }

    companion object {
        const val SYNC_PACKAGE = "com.infomaniak.sync"
        private const val SYNC_MINIMUM_VERSION = 4_03_08_00_00L
        private const val SYNC_CLASS = "at.bitfire.davdroid.ui.setup.LoginActivity"
        private const val SYNC_ACTION = "infomaniakSyncAutoConfig"
        private const val SYNC_LOGIN_KEY = "infomaniakLogin"
        private const val SYNC_PASSWORD_KEY = "infomaniakPassword"
    }
}
