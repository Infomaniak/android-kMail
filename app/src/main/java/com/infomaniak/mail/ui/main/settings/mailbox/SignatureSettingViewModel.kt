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
package com.infomaniak.mail.ui.main.settings.mailbox

import androidx.lifecycle.*
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@HiltViewModel
class SignatureSettingViewModel @Inject constructor(
    mailboxController: MailboxController,
    private val savedStateHandle: SavedStateHandle,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val coroutineContext = viewModelScope.coroutineContext + ioDispatcher

    private val mailboxObjectId inline get() = savedStateHandle.get<String>(SignatureSettingFragmentArgs::mailboxObjectId.name)!!
    val mailbox = mailboxController.getMailbox(mailboxObjectId)!!
    private var customRealm = RealmDatabase.newMailboxContentInstance(AccountUtils.currentUserId, mailbox.mailboxId)

    val signaturesLive = SignatureController.getSignaturesAsync(customRealm).asLiveData(coroutineContext)
    val showError = SingleLiveEvent<Int>() // StringRes

    fun setDefaultSignature(signature: Signature) = viewModelScope.launch(ioDispatcher) {
        with(ApiRepository.setDefaultSignature(mailbox.hostingId, mailbox.mailboxName, signature)) {
            if (isSuccess()) {
                updateSignatures()
            } else {
                showError.postValue(translatedError)
            }
        }
    }

    fun updateSignatures() = viewModelScope.launch(ioDispatcher) {
        updateSignatures(mailbox, customRealm)?.also { translatedError ->
            val title = if (translatedError == 0) RCore.string.anErrorHasOccurred else translatedError
            showError.postValue(title)
        }
    }

    override fun onCleared() {
        customRealm.close()
        super.onCleared()
    }
}
