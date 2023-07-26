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
package com.infomaniak.mail.ui.main.settings.mailbox

import androidx.lifecycle.*
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import com.infomaniak.mail.utils.throwErrorAsException
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
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

    private var customRealm: Realm? = null

    fun init(mailboxObjectId: String) = liveData(ioDispatcher) {
        mailbox = MailboxController.getMailbox(mailboxObjectId)!!
        customRealm = RealmDatabase.newMailboxContentInstance(AccountUtils.currentUserId, mailbox.mailboxId)
        signaturesLive = SignatureController.getSignaturesAsync(customRealm!!).asLiveData(coroutineContext)

        emit(mailbox)
    }

    fun setDefaultSignature(signature: Signature) = viewModelScope.launch(ioDispatcher) {
        val apiResponse = ApiRepository.setDefaultSignature(mailbox.hostingId, mailbox.mailboxName, signature)
        if (apiResponse.isSuccess()) updateSignatures() else apiResponse.throwErrorAsException()
    }

    fun updateSignatures() = viewModelScope.launch(ioDispatcher) {
        customRealm!!.writeBlocking { updateSignatures(mailbox) }
    }

    override fun onCleared() {
        customRealm?.close()
        super.onCleared()
    }
}
