/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.launch

class MenuDrawerViewModel : ViewModel() {

    val currentMailbox = MutableLiveData<Mailbox?>()
    val mailboxes = MutableLiveData<List<Mailbox>?>()
    val folders = MutableLiveData<List<Folder>?>()
    val currentFolder = MutableLiveData<Folder?>()

    fun listenToCurrentMailbox() = viewModelScope.launch {
        MainViewModel.currentMailboxFlow.collect {
            currentMailbox.value = it
        }
    }

    fun listenToMailboxes() = viewModelScope.launch {
        MailboxController.getMailboxesAsync(AccountUtils.currentUserId).collect {
            mailboxes.value = it.list
        }
    }

    fun listenToFolders() = viewModelScope.launch {
        FolderController.getFoldersAsync().collect {
            folders.value = it.list
        }
    }

    fun listenToCurrentFolder() = viewModelScope.launch {
        MainViewModel.currentFolderFlow.collect {
            currentFolder.value = it
        }
    }
}
