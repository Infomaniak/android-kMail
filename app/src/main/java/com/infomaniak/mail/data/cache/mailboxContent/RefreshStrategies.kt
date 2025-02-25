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
import com.infomaniak.mail.data.models.Folder
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
    fun otherFolderRolesToQueryThreads(): List<Folder.FolderRole> // TODO: Couple it tighter with queryFolderThreads()?

    fun getMessageFromShortUid(shortUid: String, folderId: String, realm: TypedRealm): Message?

    /**
     * @return The list of impacted threads that have changed and need to be recomputed. The list of impacted threads will also be
     * used to determine what folders need to have their unread count updated. If an extra folder needs its unread count updated
     * but no thread has that extra folder as [Thread.folderId], you can define the extra folder you want inside
     * [extraFolderIdsThatNeedToRefreshUnreadOnDeletedUid].
     */
    fun processDeletedMessage(
        scope: CoroutineScope,
        managedMessage: Message,
        context: Context,
        mailbox: Mailbox,
        realm: MutableRealm,
    ): Collection<Thread>

    fun extraFolderIdsThatNeedToRefreshUnreadOnDeletedUid(realm: TypedRealm): List<String>
    fun processDeletedThread(thread: Thread, realm: MutableRealm)
    fun shouldQueryFolderThreadsOnDeletedUid(): Boolean
}

interface DefaultRefreshStrategy : RefreshStrategy {
    override fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread> {
        return ThreadController.getThreadsByFolderId(folderId, realm)
    }

    override fun shouldForceUpdateMessagesWhenAdded(): Boolean = false
    override fun otherFolderRolesToQueryThreads(): List<Folder.FolderRole> = emptyList()

    override fun getMessageFromShortUid(shortUid: String, folderId: String, realm: TypedRealm): Message? {
        return MessageController.getMessage(shortUid.toLongUid(folderId), realm)
    }

    override fun processDeletedMessage(
        scope: CoroutineScope,
        managedMessage: Message,
        context: Context,
        mailbox: Mailbox,
        realm: MutableRealm,
    ): Collection<Thread> = buildSet {
        /**
         * This list is reversed because we'll delete items while looping over it.
         * Doing so for managed Realm objects will lively update the list we're iterating through, making us skip the next item.
         * Looping in reverse enables us to not skip any item.
         */
        managedMessage.threads.asReversed().forEach { thread ->
            scope.ensureActive()

            val isSuccess = thread.messages.remove(managedMessage)
            if (isSuccess) add(thread)
        }

        MessageController.deleteMessage(context, mailbox, managedMessage, realm)
    }

    override fun extraFolderIdsThatNeedToRefreshUnreadOnDeletedUid(realm: TypedRealm): List<String> = emptyList()

    override fun processDeletedThread(thread: Thread, realm: MutableRealm) {
        if (thread.getNumberOfMessagesInFolder() == 0) {
            realm.delete(thread)
        } else {
            thread.recomputeThread(realm)
        }
    }

    override fun shouldQueryFolderThreadsOnDeletedUid(): Boolean = false

    private fun String.toLongUid(folderId: String) = "${this}@${folderId}"
    private fun Thread.getNumberOfMessagesInFolder() = messages.count { message -> message.folderId == folderId }
}
