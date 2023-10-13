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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncAutoConfigViewModel @Inject constructor(
    application: Application,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    fun getCredentials() = viewModelScope.launch(ioCoroutineContext) {

        val apiResponse = ApiRepository.getCredentialsPassword()

        val infomaniakLogin = AccountUtils.currentUser?.login
        val infomaniakPassword = apiResponse.data?.password

        if (infomaniakLogin?.isNotEmpty() == true && infomaniakPassword?.isNotEmpty() == true) {
            Intent().apply {
                component = ComponentName("com.infomaniak.sync", "at.bitfire.davdroid.ui.setup.LoginActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = "syncAutoConfig"
                putExtra("login", infomaniakLogin)
                putExtra("password", infomaniakPassword)
            }.also(context::startActivity)
        }
    }
}
