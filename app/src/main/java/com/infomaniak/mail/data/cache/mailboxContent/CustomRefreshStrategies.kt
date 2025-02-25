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
import com.infomaniak.mail.data.models.message.Message.MessageInitialState
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import kotlinx.coroutines.CoroutineScope

val defaultRefreshStrategy = object : DefaultRefreshStrategy {}

val inboxRefreshStrategy = object : DefaultRefreshStrategy {
    override fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread> {
        return ThreadController.getInboxThreadsWithSnoozeFilter(withSnooze = false, realm = realm)
    }

    override fun otherFolderRolesToQueryThreads(): List<Folder.FolderRole> = listOf(Folder.FolderRole.SNOOZED)
}

val snoozeRefreshStrategy = object : DefaultRefreshStrategy {
    override fun queryFolderThreads(folderId: String, realm: TypedRealm): List<Thread> {
        return ThreadController.getInboxThreadsWithSnoozeFilter(withSnooze = true, realm = realm)
    }

    override fun otherFolderRolesToQueryThreads(): List<Folder.FolderRole> = listOf(Folder.FolderRole.INBOX)

    override fun getMessageFromShortUid(shortUid: String, folderId: String, realm: TypedRealm): Message? {
        val inboxId = FolderController.getFolder(Folder.FolderRole.INBOX, realm)?.id ?: return null
        return super.getMessageFromShortUid(shortUid, inboxId, realm)
    }

    override fun processDeletedMessage(
        scope: CoroutineScope,
        managedMessage: Message,
        context: Context,
        mailbox: Mailbox,
        realm: MutableRealm,
    ): Collection<Thread> {
        managedMessage.apply {
            snoozeState = null
            snoozeEndDate = null
            snoozeAction = null
        }

        return managedMessage.threads
    }

    override fun extraFolderIdsThatNeedToRefreshUnreadOnDeletedUid(realm: TypedRealm): List<String> {
        return FolderController.getFolder(Folder.FolderRole.SNOOZED, realm)?.let { listOf(it.id) } ?: emptyList()
    }

    override fun processDeletedThread(thread: Thread, realm: MutableRealm) = thread.recomputeThread()
    override fun shouldQueryFolderThreadsOnDeletedUid(): Boolean = true

    /**
     * In the case of the Snooze refresh strategy, the Message could already exist (because it comes from the INBOX).
     * In this situation, we don't want to loose its data (for example the body).
     * So we take the [remoteMessage] (because it contains the up-to-date data about the snooze state),
     * we give it the localMessage local values, then we upsert it into Realm.
     */
    override fun handleAddedMessages(
        scope: CoroutineScope,
        remoteMessage: Message,
        isConversationMode: Boolean,
        impactedThreadsManaged: MutableSet<Thread>,
        realm: MutableRealm,
    ) {

        MessageController.getMessage(remoteMessage.uid, realm)?.let { localMessage ->
            remoteMessage.initLocalValues(
                messageInitialState = MessageInitialState(
                    date = localMessage.date,
                    isFullyDownloaded = localMessage.isFullyDownloaded(),
                    isTrashed = localMessage.isTrashed,
                    isFromSearch = localMessage.isFromSearch,
                    draftLocalUuid = localMessage.draftLocalUuid,
                ),
                messageIds = localMessage.messageIds,
            )
            remoteMessage.keepHeavyData(localMessage)
        }

        val updatedMessage = MessageController.upsertMessage(remoteMessage, realm)

        impactedThreadsManaged += updatedMessage.threads
    }
}
