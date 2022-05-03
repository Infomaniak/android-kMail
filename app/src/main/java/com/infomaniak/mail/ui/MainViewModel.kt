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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    fun fetchMailboxesAndFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            val mailboxes = MailRealm.getMailboxes()
            val mailbox1 = mailboxes.find { it.mailboxId == AccountUtils.currentMailboxId } ?: mailboxes.firstOrNull()
//          val mailbox =  if (mailbox1 == null) {
//                val mailbox2 =
//                    mailboxes.firstOrNull()
//                mailbox2?.mailboxId?.let { AccountUtils.currentMailboxId = it }
//                MailRealm.currentMailboxFlow.value = mailbox2
            // MailRealm.currentMailbox = mailbox2
//            } else {
//                MailRealm.currentMailboxFlow.value = mailbox1
            // MailRealm.currentMailbox = mailbox1
//            }

            val inbox = mailbox1?.getFolders()?.firstOrNull { it.getRole() == Folder.FolderRole.INBOX }
            inbox?.getThreads()
//            MailRealm.currentFolderFlow.value = inbox
        }

//    val mailboxes: List<Mailbox> by lazy { MailRealm.getMailboxes() }
    }
}
