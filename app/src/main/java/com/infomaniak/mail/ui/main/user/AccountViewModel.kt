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
package com.infomaniak.mail.ui.main.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.mailbox.MailboxLinkedResult
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    application: Application,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    val shouldStartNoMailboxActivity = SingleLiveEvent<Unit>()

    suspend fun updateMailboxes(): Boolean {
        val mailboxes = ApiRepository.getMailboxes(AccountUtils.getHttpClient(AccountUtils.currentUserId)).data ?: return false
        MailboxController.updateMailboxes(context, mailboxes)

        return AccountUtils.manageMailboxesEdgeCases(
            context = context,
            mailboxes = mailboxes,
            launchNoMailboxActivity = { shouldStartNoMailboxActivity.postValue(Unit) },
        )
    }

    fun attachNewMailbox(
        address: String,
        password: String,
    ): LiveData<ApiResponse<MailboxLinkedResult>> = liveData(ioCoroutineContext) {
        emit(ApiRepository.addNewMailbox(address, password))
    }

    fun switchToNewMailbox(newMailboxId: Int) = viewModelScope.launch(ioCoroutineContext) {
        val shouldStop = updateMailboxes()
        if (shouldStop) return@launch

        AccountUtils.switchToMailbox(newMailboxId)
    }
}
