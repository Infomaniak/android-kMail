/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.data.cache.mailboxContent

import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm

val defaultRefreshStrategy = object : DefaultRefreshStrategy {}

val inboxRefreshStrategy = object : DefaultRefreshStrategy {
    override fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread> {
        return ThreadController.getInboxThreadsWithSnoozeFilter(withSnooze = false, realm = realm)
    }
}

val snoozeRefreshStrategy = object : DefaultRefreshStrategy {
    override fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread> {
        return ThreadController.getInboxThreadsWithSnoozeFilter(withSnooze = true, realm = realm)
    }

    override fun updateExistingMessageWhenAdded(remoteMessage: Message, realm: MutableRealm) {
        MessageController.updateMessage(remoteMessage.uid, realm = realm) { localMessage ->
            localMessage?.snoozeState = remoteMessage.snoozeState
            localMessage?.snoozeEndDate = remoteMessage.snoozeEndDate
            localMessage?.snoozeAction = remoteMessage.snoozeAction
        }
    }
}
