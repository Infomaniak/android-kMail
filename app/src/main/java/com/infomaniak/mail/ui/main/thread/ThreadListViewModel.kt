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
package com.infomaniak.mail.ui.main.thread

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ThreadListViewModel : ViewModel() {

    private companion object {
        val DEFAULT_FOLDER_ROLE = FolderRole.INBOX
    }

    var isInternetAvailable = MutableLiveData(true)

    val threadsFromAPI = MutableStateFlow<List<Thread>?>(null)

    fun getDataFromRealmThenFetchFromAPI(): List<Thread> {
        val threads = readDataFromRealm()
        fetchDataFromAPI()
        return threads
    }

    private fun readDataFromRealm(): List<Thread> {

        fun getCurrentMailbox(): Mailbox? {
            val mailboxes = MailRealm.readMailboxesFromRealm()
            return mailboxes.find { it.mailboxId == AccountUtils.currentMailboxId } ?: mailboxes.firstOrNull()
        }

        fun getFolder(mailbox: Mailbox, folderRole: FolderRole): Folder? =
            mailbox.readFoldersFromRealm().find { it.getRole() == folderRole }

        Log.e("Realm", "Start reading data")
        val mailbox = getCurrentMailbox() ?: return emptyList()
        mailbox.select()
        val folder = getFolder(mailbox, DEFAULT_FOLDER_ROLE) ?: return emptyList()
        folder.select()
        Log.e("Realm", "End of reading data")
        return folder.threads
    }

    private fun fetchDataFromAPI() {

        fun fetchCurrentMailbox(): Mailbox? {
            val mailboxes = MailRealm.fetchMailboxesFromApi()
            return with(mailboxes) {
                find { it.mailboxId == AccountUtils.currentMailboxId }
                // ?: find { it.email == "kevin.boulongne@ik.me" }
                    ?: find { it.email == "kevin.boulongne@infomaniak.com" }
                    ?: firstOrNull()
            }
        }

        fun fetchFolder(mailbox: Mailbox, folderRole: FolderRole): Folder? =
            mailbox.fetchFoldersFromAPI().find { it.getRole() == folderRole }

        viewModelScope.launch(Dispatchers.IO) {
            Log.e("API", "Start fetching data")
            val mailbox = fetchCurrentMailbox() ?: return@launch
            mailbox.select()
            val folder = fetchFolder(mailbox, DEFAULT_FOLDER_ROLE) ?: return@launch
            folder.updateAndSelect(mailbox.uuid)
            Log.e("API", "End of fetching data")
            threadsFromAPI.value = folder.threads
        }
    }
}
