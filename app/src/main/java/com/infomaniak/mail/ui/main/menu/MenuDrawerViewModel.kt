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

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.mailboxInfos.QuotasController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Quotas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class MenuDrawerViewModel : ViewModel() {

    fun getFolders(): LiveData<List<Folder>> = liveData(Dispatchers.IO) {
        emitSource(
            FolderController.getFoldersAsync()
                .map { it.list }
                .asLiveData()
        )
    }

    fun getMailbox(objectId: String): LiveData<Mailbox?> = liveData(Dispatchers.IO) {
        emit(MailboxController.getMailboxSync(objectId))
    }

    fun getQuotas(mailboxObjectId: String): LiveData<Quotas?> = liveData(Dispatchers.IO) {
        emitSource(
            QuotasController.getQuotasAsync(mailboxObjectId)
                .map { it.obj }
                .asLiveData()
        )
    }
}
