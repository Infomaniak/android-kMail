/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDeepLinkBuilder
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.applock.Utils.isKeyguardSecure
import com.infomaniak.mail.MatomoMail.trackUserId
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.login.LoginActivityArgs
import com.infomaniak.mail.ui.main.thread.ThreadFragmentArgs
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.resetLastAppClosing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchActivity : AppCompatActivity() {

    private val navigationArgs: LaunchActivityArgs? by lazy { intent?.extras?.let(LaunchActivityArgs::fromBundle) }

    private val localSettings by lazy { LocalSettings.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleNotificationDestinationIntent()

        lifecycleScope.launch(Dispatchers.IO) {
            val user = AccountUtils.requestCurrentUser()

            withContext(Dispatchers.Main) {
                if (user == null) {
                    loginUser()
                } else {
                    trackUserId(AccountUtils.currentUserId)

                    // When MailboxController is migrated
                    if (MailboxController.getMailboxesCount(user.id) == 0L) {
                        AccountUtils.updateUserAndMailboxes(this@LaunchActivity)
                    }
                    if (navigationArgs?.shouldLock != false && isKeyguardSecure() && localSettings.isAppLocked) {
                        startAppLockActivity()
                    } else {
                        startApp()
                    }
                }
            }
        }
    }

    private fun startApp() {
        navigationArgs?.openThreadUid?.let {
            NavDeepLinkBuilder(this)
                .setGraph(R.navigation.main_navigation)
                .setDestination(R.id.threadFragment, ThreadFragmentArgs(it).toBundle())
                .setComponentName(MainActivity::class.java)
                .createTaskStackBuilder().startActivities()
        } ?: run {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun startAppLockActivity() {
        LockActivity.startAppLockActivity(
            context = this,
            destinationClass = MainActivity::class.java,
            primaryColor = localSettings.accentColor.getPrimary(this)
        )
        application.resetLastAppClosing()
    }

    private fun loginUser() {
        Intent(this, LoginActivity::class.java).apply {
            putExtras(LoginActivityArgs(isFirstAccount = true).toBundle())
            startActivity(this)
        }
    }

    private fun handleNotificationDestinationIntent() {
        navigationArgs?.let {
            if (it.userId != AppSettings.DEFAULT_ID && it.mailboxId != AppSettings.DEFAULT_ID) {
                if (AccountUtils.currentUserId != it.userId) AccountUtils.currentUserId = it.userId
                if (AccountUtils.currentMailboxId != it.mailboxId) AccountUtils.currentMailboxId = it.mailboxId
                SentryDebug.addNotificationBreadcrumb("SyncMessages notification has been clicked")
            }
        }
    }
}
