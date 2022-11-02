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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.mail.MatomoMail.trackUserId
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.login.LoginActivityArgs
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            AccountUtils.requestCurrentUser()
                .also { trackUserId(AccountUtils.currentUserId) }
                ?.let { startApp() }
                ?: run { loginUser() }
        }
    }

    private fun startApp() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun loginUser() {
        Intent(this, LoginActivity::class.java).apply {
            putExtras(LoginActivityArgs(isFirstAccount = true).toBundle())
            startActivity(this)
        }
    }
}
