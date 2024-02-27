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
package com.infomaniak.mail.ui.main.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SwitchUserViewModel @Inject constructor(
    application: Application,
    private val mailboxController: MailboxController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private inline val context get() = getApplication<Application>()

    val allUsers = AccountUtils.getAllUsers().map { users -> users.sortedBy { it.displayName } }

    fun switchAccount(user: User) = viewModelScope.launch(ioDispatcher) {
        if (user.id != AccountUtils.currentUserId) {
            context.trackAccountEvent("switch")
            RealmDatabase.backupPreviousRealms()
            AccountUtils.currentUser = user
            AccountUtils.currentMailboxId = mailboxController.getFirstValidMailbox(user.id)?.mailboxId ?: AppSettings.DEFAULT_ID
            AccountUtils.reloadApp?.invoke()
        }
    }
}
