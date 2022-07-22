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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.api.ApiRepository.OFFSET_FIRST_PAGE
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import kotlinx.coroutines.launch

class MenuDrawerViewModel : ViewModel() {

    val currentMailbox = MutableLiveData<Mailbox?>()
    val mailboxes = MutableLiveData<List<Mailbox>?>()
    val folders = MutableLiveData<List<Folder>?>()

    fun listenToCurrentMailbox() {
        viewModelScope.launch {
            MailData.currentMailboxFlow.collect {
                currentMailbox.value = it
            }
        }
    }

    fun listenToMailboxes() {
        viewModelScope.launch {
            MailData.mailboxesFlow.collect {
                mailboxes.value = it
            }
        }
    }

    fun listenToFolders() {
        viewModelScope.launch {
            MailData.foldersFlow.collect {
                folders.value = it
            }
        }
    }

    fun openFolder(folderName: String, context: Context) {
        val folder = (MailData.foldersFlow.value?.find { it.getLocalizedName(context) == folderName } ?: return).also {
            if (it.id == MailData.currentFolderFlow.value?.id) return
        }
        val mailbox = MailData.currentMailboxFlow.value ?: return

        MailData.selectFolder(folder)
        MailData.loadThreads(folder, mailbox, OFFSET_FIRST_PAGE)
    }

    fun switchToMailbox(mailbox: Mailbox) {
        MailData.selectMailbox(mailbox)
        MailData.loadMailboxContent()
    }
}
