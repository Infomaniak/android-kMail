/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
@file:OptIn(ExperimentalSplittiesApi::class)

package com.infomaniak.mail.ui.main.settings.mailbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfo.SignatureController
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.utils.SharedUtils.Companion.updateSignatures
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.Realm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import splitties.coroutines.suspendLazy
import splitties.experimental.ExperimentalSplittiesApi
import javax.inject.Inject
import com.infomaniak.core.legacy.R as RCore

@HiltViewModel
class SignatureSettingViewModel @Inject constructor(
    mailboxController: MailboxController,
    signatureController: SignatureController,
    private val savedStateHandle: SavedStateHandle,
    @MailboxInfoRealm private val mailboxInfoRealm: Realm,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val coroutineContext = viewModelScope.coroutineContext + ioDispatcher

    private val mailboxObjectId inline get() = savedStateHandle.get<String>(SignatureSettingFragmentArgs::mailboxObjectId.name)!!
    val mailbox = viewModelScope.suspendLazy { mailboxController.getMailbox(mailboxObjectId)!! }

    val signaturesLive = signatureController.getSignaturesAsync(mailboxObjectId).asLiveData(coroutineContext)
    val showError = SingleLiveEvent<Int>() // StringRes

    fun setDefaultSignature(signature: Signature?) = viewModelScope.launch(ioDispatcher) {
        with(ApiRepository.setDefaultSignature(mailbox().hostingId, mailbox().mailboxName, signature)) {
            if (isSuccess()) {
                updateSignatures()
            } else {
                showError.postValue(translateError())
            }
        }
    }

    fun updateSignatures() = viewModelScope.launch(ioDispatcher) {
        updateSignatures(mailbox(), mailboxInfoRealm)?.also { translatedError ->
            val title = if (translatedError == 0) RCore.string.anErrorHasOccurred else translatedError
            showError.postValue(title)
        }
    }
}
