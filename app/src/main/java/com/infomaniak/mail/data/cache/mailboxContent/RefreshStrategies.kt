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

import android.content.Context
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive

interface RefreshStrategy {
    fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread>
    fun shouldForceUpdateMessagesWhenAdded(): Boolean
    fun MutableRealm.processDeletedMessage(
        scope: CoroutineScope,
        message: Message,
        context: Context,
        mailbox: Mailbox,
    ): List<Pair<Thread, String>>
}

interface DefaultRefreshStrategy : RefreshStrategy {
    override fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread> {
        return ThreadController.getThreadsByFolderId(folderId, realm)
    }

    override fun shouldForceUpdateMessagesWhenAdded(): Boolean = false

    override fun MutableRealm.processDeletedMessage(
        scope: CoroutineScope,
        message: Message,
        context: Context,
        mailbox: Mailbox,
    ): List<Pair<Thread, String>> {
        for (thread in message.threads.asReversed()) {
            scope.ensureActive()

            val isSuccess = thread.messages.remove(message)
            val numberOfMessagesInFolder = thread.messages.count { it.folderId == thread.folderId }

            // We need to save this value because the Thread could be deleted before we use this `folderId`.
            val threadFolderId = thread.folderId

            if (numberOfMessagesInFolder == 0) {
                threads.removeIf { it.uid == thread.uid }
                delete(thread)
            } else if (isSuccess) {
                threads += thread
            } else {
                continue
            }

            impactedFolders.add(threadFolderId)
        }

        MessageController.deleteMessage(context, mailbox, message, realm = this)
    }
}
