/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.utils.AccountUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.Realm
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @MailboxInfoRealm private val mailboxInfoRealm: Realm,
) : ViewModel() {

    val myKSuiteMailboxResult = SingleLiveEvent<Mailbox?>()

    fun getMyKSuiteMailbox(mailboxId: Int) = viewModelScope.launch {
        myKSuiteMailboxResult.postValue(
            MailboxController.getMailbox(
                userId = AccountUtils.currentUserId,
                mailboxId = mailboxId,
                realm = mailboxInfoRealm,
            )
        )
    }
}
