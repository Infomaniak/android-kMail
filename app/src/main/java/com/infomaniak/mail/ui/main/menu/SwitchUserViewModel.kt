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

import androidx.lifecycle.ViewModel
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Mailbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class SwitchUserViewModel : ViewModel() {

    private var listenToMailboxesJob: Job? = null

    private val mutableUiMailboxesFlow: MutableStateFlow<List<Mailbox>?> = MutableStateFlow(null)
    val uiMailboxesFlow = mutableUiMailboxesFlow.asStateFlow()

    fun setup() {
        listenToMailboxes()
    }

    private fun listenToMailboxes() {
        if (listenToMailboxesJob != null) listenToMailboxesJob?.cancel()

        listenToMailboxesJob = CoroutineScope(Dispatchers.IO).launch {
            MailData.mailboxesFlow.filterNotNull().collect { mailboxes ->
                mutableUiMailboxesFlow.value = mailboxes
            }
        }
    }

    fun loadMailboxes() {
        MailData.loadMailboxes()
    }

    override fun onCleared() {
        listenToMailboxesJob?.cancel()
        listenToMailboxesJob = null

        super.onCleared()
    }
}
