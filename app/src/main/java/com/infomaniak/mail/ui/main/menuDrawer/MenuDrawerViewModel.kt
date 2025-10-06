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
package com.infomaniak.mail.ui.main.menuDrawer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController.Companion.updateFolder
import com.infomaniak.mail.data.models.FolderUi
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuDrawerViewModel @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    val areMailboxesExpanded = MutableLiveData(false)
    val areCustomFoldersExpanded = MutableLiveData(true)
    val areActionsExpanded = MutableLiveData(false)

    fun toggleFolderCollapsingState(rootFolderUi: FolderUi, shouldCollapse: Boolean) = viewModelScope.launch(ioCoroutineContext) {
        // When subfolders are set as folders with specific roles, they are not truly root according to Folder.isRoot but yet
        // they are the ones that can be collapsed. Therefore we must not rely on Folder.isRoot here.
        mailboxContentRealm().write {
            updateFolder(rootFolderUi.folder.id) {
                it.isCollapsed = shouldCollapse
            }
        }
    }

    fun toggleActionsCollapsingState() {
        areActionsExpanded.value = !areActionsExpanded.value!!
    }
}
