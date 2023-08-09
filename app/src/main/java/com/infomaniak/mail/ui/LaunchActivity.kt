/*
 * Infomaniak ikMail - Android
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
import com.infomaniak.mail.MatomoMail.trackUserId
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MainDispatcher
import com.infomaniak.mail.ui.login.LoginActivityArgs
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentArgs
import com.infomaniak.mail.ui.main.thread.ThreadFragmentArgs
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.launchLoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class LaunchActivity : AppCompatActivity() {

    private val navigationArgs: LaunchActivityArgs? by lazy { intent?.extras?.let(LaunchActivityArgs::fromBundle) }

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleNotificationDestinationIntent()

        lifecycleScope.launch(ioDispatcher) {
            val user = AccountUtils.requestCurrentUser()

            withContext(mainDispatcher) {
                if (user == null) {
                    launchLoginActivity(args = LoginActivityArgs(isFirstAccount = true))
                } else {
                    trackUserId(AccountUtils.currentUserId)
                    startApp()
                }
                // After starting the destination activity, we run finish to make sure we close the LaunchScreen,
                // so that even when we return, the activity will still be closed.
                finish()
            }
        }
    }

    private fun startApp() {

        val openThreadUid = navigationArgs?.openThreadUid
        val replyToMessageUid = navigationArgs?.replyToMessageUid

        when {
            openThreadUid != null -> {
                NavDeepLinkBuilder(this)
                    .setGraph(R.navigation.main_navigation)
                    .setDestination(R.id.threadFragment, ThreadFragmentArgs(openThreadUid).toBundle())
                    .setComponentName(MainActivity::class.java)
                    .createTaskStackBuilder()
                    .startActivities()
            }
            replyToMessageUid != null -> {
                NavDeepLinkBuilder(this)
                    .setGraph(R.navigation.main_navigation)
                    .setDestination(
                        destId = R.id.threadListFragment,
                        args = ThreadListFragmentArgs(
                            replyToMessageUid = replyToMessageUid,
                            draftMode = navigationArgs?.draftMode!!,
                            notificationId = navigationArgs?.notificationId!!,
                        ).toBundle(),
                    )
                    .setComponentName(MainActivity::class.java)
                    .createTaskStackBuilder()
                    .startActivities()
            }
            else -> {
                startActivity(Intent(this, MainActivity::class.java))
            }
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
