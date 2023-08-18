/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InvalidPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private val mailboxObjectId = savedStateHandle.get<String>(InvalidPasswordFragmentArgs::mailboxObjectId.name)!!
    private val mailbox = MailboxController.getMailbox(mailboxObjectId)!!

    val updatePasswordResult = SingleLiveEvent<Int>()
    val requestPasswordResult = SingleLiveEvent<Boolean>()
    val detachMailboxResult = SingleLiveEvent<Int>()

    fun updatePassword(password: String) = viewModelScope.launch(ioCoroutineContext) {
        val apiResponse = ApiRepository.updateMailboxPassword(mailbox.mailboxId, password)
        if (apiResponse.isSuccess()) {
            MailboxController.updateMailbox(mailboxObjectId) { it.isPasswordValid = true }
            AccountUtils.switchToMailbox(mailbox.mailboxId)
        } else {
            updatePasswordResult.postValue(apiResponse.translateError())
        }
    }

    fun requestPassword() = viewModelScope.launch(ioCoroutineContext) {
        requestPasswordResult.postValue(ApiRepository.requestMailboxPassword(mailbox.hostingId, mailbox.mailboxName).isSuccess())
    }

    fun detachMailbox() = viewModelScope.launch(ioCoroutineContext) {
        val apiResponse = ApiRepository.detachMailbox(mailbox.mailboxId)
        if (apiResponse.isSuccess()) {
            AccountUtils.switchToMailbox(
                MailboxController.getFirstValidMailbox(AccountUtils.currentUserId)?.mailboxId ?: AppSettings.DEFAULT_ID,
            )
        } else {
            detachMailboxResult.postValue(apiResponse.translateError())
        }
    }
}
