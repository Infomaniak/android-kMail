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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.MailRealm
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.notifications.InitialResults
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResults
import io.realm.query
import io.realm.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadListViewModel : ViewModel() {

    var isInternetAvailable = MutableLiveData(true)

    private val _threads = MutableStateFlow<ResultsChange<Thread>?>(null)
    val threads = _threads.asStateFlow().filterNotNull()

    fun fetchThreads() {
        viewModelScope.launch(Dispatchers.IO) {
            MailRealm.currentFolderFlow.filterNotNull().collect {
                MailRealm.mailboxContent.query<Thread>().sort("date", Sort.DESCENDING).asFlow().collect { threadsResults ->
                    when (threadsResults) {
                        is InitialResults -> if (_threads.value == null) _threads.value = threadsResults
                        is UpdatedResults -> _threads.value = threadsResults
                    }
                }
            }
        }
    }
}
