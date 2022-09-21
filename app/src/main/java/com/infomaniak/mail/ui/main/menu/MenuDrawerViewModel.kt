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

import androidx.lifecycle.*
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxInfos.QuotasController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class MenuDrawerViewModel : ViewModel() {

    val currentMailboxObjectId = MutableLiveData<String>()

    val folders = Transformations.switchMap(currentMailboxObjectId) {
        liveData(Dispatchers.IO) { emitSource(FolderController.getFoldersAsync().map { it.list }.asLiveData()) }
    }

    val quotas = Transformations.switchMap(currentMailboxObjectId) { mailboxObjectId ->
        liveData(Dispatchers.IO) { emitSource(QuotasController.getQuotasAsync(mailboxObjectId).asLiveData()) }
    }
}
