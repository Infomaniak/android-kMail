/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main.settings.mailbox

import androidx.lifecycle.*
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class SignatureSettingViewModel @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val coroutineContext = viewModelScope.coroutineContext + ioDispatcher

    lateinit var signaturesLive: LiveData<RealmResults<Signature>>
        private set

    lateinit var mailbox: Mailbox
        private set

    fun init(mailboxObjectId: String) = liveData(ioDispatcher) {
        mailbox = MailboxController.getMailbox(mailboxObjectId)!!
        signaturesLive = SignatureController.getSignaturesLive(mailbox.mailboxId).asLiveData(coroutineContext)

        emit(mailbox)
    }

    fun setDefaultSignature(signature: Signature) = liveData(ioDispatcher) {
        val apiResponse = ApiRepository.setDefaultSignature(mailbox.hostingId, mailbox.mailboxName, signature)
        emit(apiResponse.isSuccess())
    }
}
