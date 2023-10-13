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
package com.infomaniak.mail.ui.sync

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackBarManager
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltViewModel
class SyncAutoConfigViewModel @Inject constructor(
    application: Application,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private var credentialsJob: Job? = null
    val snackBarManager by lazy { SnackBarManager() }

    fun isSyncAppInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(SYNC_PACKAGE, PackageManager.GET_ACTIVITIES)
        true
    }.getOrElse {
        false
    }

    fun configureUserAutoSync(onSuccess: (Intent) -> Unit) {
        credentialsJob?.cancel()
        credentialsJob = viewModelScope.launch(ioCoroutineContext) {
            fetchCredentials(scope = this)?.let { password ->
                autoSync(password, onSuccess)
            }
        }
    }

    private fun fetchCredentials(scope: CoroutineScope): String? {

        val apiResponse = ApiRepository.getCredentialsPassword()
        scope.ensureActive()

        return apiResponse.data?.password.also { password ->
            if (password == null) snackBarManager.postValue(context.getString(R.string.errorGetCredentials))
        }
    }

    private fun autoSync(infomaniakPassword: String, onSuccess: (Intent) -> Unit) {

        val infomaniakLogin = AccountUtils.currentUser?.login

        if (infomaniakLogin?.isNotEmpty() == true && infomaniakPassword.isNotEmpty()) {
            Intent().apply {
                component = ComponentName(SYNC_PACKAGE, SYNC_CLASS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                action = SYNC_ACTION
                putExtra(SYNC_LOGIN_KEY, infomaniakLogin)
                putExtra(SYNC_PASSWORD_KEY, infomaniakPassword)
            }.also(onSuccess)
        }
    }

    companion object {
        const val SYNC_PACKAGE = "com.infomaniak.sync"
        private const val SYNC_CLASS = "at.bitfire.davdroid.ui.setup.LoginActivity"
        private const val SYNC_ACTION = "infomaniakSyncAutoConfig"
        private const val SYNC_LOGIN_KEY = "infomaniakLogin"
        private const val SYNC_PASSWORD_KEY = "infomaniakPassword"
    }
}
