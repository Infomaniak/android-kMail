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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val coroutineContext = viewModelScope.coroutineContext + Dispatchers.IO

    val observeAccountsLive = MailboxController.getMailboxesAsync()
        .map { mailboxes -> mailboxes.list.filter { it.userId == AccountUtils.currentUserId } }
        .asLiveData(coroutineContext)

    fun updateMailboxes() = viewModelScope.launch(Dispatchers.IO) {
        val userId = AccountUtils.currentUserId
        val mailboxes = ApiRepository.getMailboxes(AccountUtils.getHttpClient(userId)).data ?: return@launch
        MailboxController.updateMailboxes(getApplication(), mailboxes, userId)
    }
}
