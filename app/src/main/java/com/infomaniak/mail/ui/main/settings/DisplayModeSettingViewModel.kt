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
package com.infomaniak.mail.ui.main.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.LocalSettings.ThreadMode
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.createMultiMessagesThreads
import com.infomaniak.mail.data.cache.mailboxContent.MessageController.createSingleMessageThreads
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DisplayModeSettingViewModel : ViewModel() {

    fun dropThreads(threadMode: ThreadMode, mailboxObjectId: String) = viewModelScope.launch(Dispatchers.IO) {
        RealmDatabase.mailboxContent().write {

            ThreadController.deleteThreads(realm = this)

            val messages = MessageController.getMessages(realm = this)
            when (threadMode) {
                ThreadMode.THREADS -> createMultiMessagesThreads(messages, mailboxObjectId)
                ThreadMode.MESSAGES -> createSingleMessageThreads(messages, mailboxObjectId)
            }
        }
    }
}
