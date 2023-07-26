/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.MutableRealm
import io.sentry.Sentry
import kotlinx.serialization.encodeToString
import javax.inject.Inject

class SharedUtils @Inject constructor(
    private val folderController: FolderController,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val messageController: MessageController,
) {

    /**
     * Mark a Message or some Threads as read
     * @param mailbox The Mailbox where the Threads & Messages are located
     * @param threads The Threads to mark as read
     * @param message The Message to mark as read
     * @param started The callback for when the refresh of Threads starts
     * @param stopped The callback for when the refresh of Threads ends
     * @param shouldRefreshThreads Sometimes, we don't want to refresh Threads after doing this action. For example, when replying to a Message.
     */
    suspend fun markAsSeen(
        mailbox: Mailbox,
        threads: List<Thread>,
        message: Message? = null,
        started: (() -> Unit)? = null,
        stopped: (() -> Unit)? = null,
        shouldRefreshThreads: Boolean = true,
    ) {

        val messages = when (message) {
            null -> threads.flatMap(messageController::getUnseenMessages)
            else -> messageController.getMessageAndDuplicates(threads.first(), message)
        }

        val isSuccess = ApiRepository.markMessagesAsSeen(mailbox.uuid, messages.getUids()).isSuccess()

        if (isSuccess && shouldRefreshThreads) {
            refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                started = started,
                stopped = stopped,
            )
        }
    }

    fun getMessagesToMove(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getMovableMessages)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
    }

    suspend fun refreshFolders(
        mailbox: Mailbox,
        messagesFoldersIds: List<String>,
        destinationFolderId: String? = null,
        started: (() -> Unit)? = null,
        stopped: (() -> Unit)? = null,
    ) {

        // We always want to refresh the `destinationFolder` last, to avoid any blink on the UI.
        val foldersIds = messagesFoldersIds.toMutableSet()
        destinationFolderId?.let(foldersIds::add)

        foldersIds.forEach { folderId ->
            folderController.getFolder(folderId)?.let { folder ->
                RefreshController.refreshThreads(
                    refreshMode = RefreshMode.REFRESH_FOLDER,
                    mailbox = mailbox,
                    folder = folder,
                    realm = mailboxContentRealm(),
                    started = started,
                    stopped = stopped,
                )
            }
        }
    }

    companion object {

        fun MutableRealm.updateSignatures(mailbox: Mailbox) {
            with(ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)) {
                if (isSuccess()) {
                    SignatureController.update(data?.signatures ?: emptyList(), realm = this@updateSignatures)
                } else {
                    Sentry.captureException(ApiErrorException(ApiController.json.encodeToString(value = this)))
                }
            }
        }
    }
}
