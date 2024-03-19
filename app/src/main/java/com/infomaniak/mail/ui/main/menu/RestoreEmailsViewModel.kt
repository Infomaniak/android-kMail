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
package com.infomaniak.mail.ui.main.menu

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.BackupResult
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class RestoreEmailsViewModel @Inject constructor(
    private val mailboxController: MailboxController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private val mailbox by lazy { mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)!! }

    fun getBackups(): LiveData<ApiResponse<BackupResult>> = liveData(ioCoroutineContext) {
        emit(ApiRepository.getBackups(mailbox.hostingId, mailbox.mailboxName))
    }

    fun restoreEmails(date: String): LiveData<ApiResponse<Boolean>> = liveData(ioCoroutineContext) {
        emit(ApiRepository.restoreBackup(mailbox.hostingId, mailbox.mailboxName, date))
    }
}
