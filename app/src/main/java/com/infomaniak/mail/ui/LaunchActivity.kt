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
package com.infomaniak.mail.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.login.LoginActivityArgs
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.observeNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            if (AccountUtils.requestCurrentUser() == null) loginUser() else startApp()
        }
    }

    private fun loginUser() {
        Intent(this, LoginActivity::class.java).apply {
            putExtras(LoginActivityArgs(isFirstAccount = true).toBundle())
            startActivity(this)
        }
    }

    private suspend fun startApp() {
        mainViewModel.updateUserInfo()
        mainViewModel.loadCurrentMailbox(UiSettings.getInstance(this).threadMode)

        withContext(Dispatchers.Main) {
            MainViewModel.currentMailboxObjectId.observeNotNull(this@LaunchActivity) {
                // TODO: If there is no Internet, the app won't be able to start.
                startActivity(Intent(this@LaunchActivity, MainActivity::class.java))
            }
        }
    }
}
