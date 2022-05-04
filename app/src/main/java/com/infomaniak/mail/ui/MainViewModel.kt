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
package com.infomaniak.mail.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.cache.MailboxContentController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    fun getData() {
        viewModelScope.launch(Dispatchers.IO) {
            readDataFromRealm()
            val threadsFromRealm = MailRealm.currentFolderIdFlow.value?.let(MailboxContentController::getFolder)?.threads
            Log.e("Realm", "readData: ${threadsFromRealm?.size}")

            fetchDataFromAPI()
            val threadsFromAPI = MailRealm.currentFolderIdFlow.value?.let(MailboxContentController::getFolder)?.threads
            Log.e("Realm", "fetchData: ${threadsFromAPI?.size}")
        }
    }

    private fun readDataFromRealm() {

        fun getCurrentMailbox(): Mailbox? {
            val mailboxes = MailRealm.readMailboxesFromRealm()
            return mailboxes.find { it.mailboxId == AccountUtils.currentMailboxId } ?: mailboxes.firstOrNull()
        }

        fun getInbox(mailbox: Mailbox): Folder? =
            mailbox.readFoldersFromRealm().firstOrNull { it.getRole() == Folder.FolderRole.INBOX }

        Log.e("Realm", "Start reading data")

        getCurrentMailbox()?.let { mailbox ->
            mailbox.select()
            getInbox(mailbox)?.let { folder ->
                folder.select()
                Log.e("Realm", "End of reading data")
            }
        }
    }

    private fun fetchDataFromAPI() {

        fun getCurrentMailbox(): Mailbox? {
            val mailboxes = MailRealm.fetchMailboxesFromApi()
            return mailboxes.find { it.mailboxId == AccountUtils.currentMailboxId } ?: mailboxes.firstOrNull()
        }

        fun getInbox(mailbox: Mailbox): Folder? =
            mailbox.fetchFoldersFromAPI().firstOrNull { it.getRole() == Folder.FolderRole.INBOX }

        Log.e("API", "Start fetching data")

        getCurrentMailbox()?.let { mailbox ->
            mailbox.select()
            getInbox(mailbox)?.let { folder ->
                folder.fetchThreadsFromAPI()
                folder.select()
                Log.e("API", "End of fetching data")
            }
        }
    }
}
