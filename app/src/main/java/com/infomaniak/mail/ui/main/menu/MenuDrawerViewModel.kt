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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuDrawerViewModel @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    mailboxController: MailboxController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    val otherMailboxesLive = mailboxController.getMailboxesAsync(
        userId = AccountUtils.currentUserId,
        exceptionMailboxIds = listOf(AccountUtils.currentMailboxId),
    ).asLiveData(ioCoroutineContext)

    fun toggleFolderCollapsingState(folderId: String, shouldCollapse: Boolean) = viewModelScope.launch(ioCoroutineContext) {
        FolderController.updateFolderAndChildren(folderId, mailboxContentRealm()) {
            if (it.isRoot) it.isCollapsed = shouldCollapse else it.isHidden = shouldCollapse
        }
    }
}
