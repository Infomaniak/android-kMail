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
package com.infomaniak.mail.ui.main.user

import android.app.Application
import androidx.lifecycle.*
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ManageMailAddressViewModel(application: Application) : AndroidViewModel(application) {

    fun observeAccounts(): LiveData<List<Mailbox>> = liveData(Dispatchers.IO) {
        AccountUtils.currentUser?.let { user ->

            updateMailboxes(user)

            emitSource(MailboxController.getMailboxesAsync().map { mailboxes ->
                mailboxes.list.filter { it.userId == user.id }
            }.asLiveData())
        }

    }

    private fun updateMailboxes(user: User) = viewModelScope.launch(Dispatchers.IO) {
        val okHttpClient = user.id.let { AccountUtils.getHttpClient(it) }
        val mailboxes = ApiRepository.getMailboxes(okHttpClient).data ?: return@launch
        MailboxController.updateMailboxes(getApplication(), mailboxes, user.id)
    }
}
