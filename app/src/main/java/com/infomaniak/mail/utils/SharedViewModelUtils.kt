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

import android.content.Context
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
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
import io.sentry.SentryLevel
import javax.inject.Inject

class SharedViewModelUtils @Inject constructor(
    private val folderController: FolderController,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val messageController: MessageController,
) {

    /**
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

        fun updateSignatures(mailbox: Mailbox, realm: MutableRealm, context: Context) {
            with(ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)) {

                if (isSuccess()) {

                    val defaultSignaturesCount = data?.signatures?.count { it.isDefault } ?: -1
                    when {
                        data == null -> Sentry.withScope { scope ->
                            scope.level = SentryLevel.ERROR
                            scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                            scope.setExtra("apiResponse", toString())
                            scope.setExtra("status", result.name)
                            scope.setExtra("errorCode", "${error?.code}")
                            scope.setExtra("errorDescription", "${error?.description}")
                            scope.setExtra("errorTranslated", context.getString(translateError()))
                            Sentry.captureMessage("Signature: The call to get Signatures returned a `null` data")
                        }
                        data?.signatures?.isEmpty() == true -> Sentry.withScope { scope ->
                            scope.level = SentryLevel.ERROR
                            scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                            Sentry.captureMessage("Signature: This user doesn't have any Signature")
                        }
                        defaultSignaturesCount == 0 -> Sentry.withScope { scope ->
                            scope.level = SentryLevel.ERROR
                            scope.setExtra("signaturesCount", "${data?.signatures?.count()}")
                            scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                            Sentry.captureMessage("Signature: This user has Signatures, but no default one")
                        }
                        defaultSignaturesCount > 1 -> Sentry.withScope { scope ->
                            scope.level = SentryLevel.ERROR
                            scope.setExtra("defaultSignaturesCount", "$defaultSignaturesCount")
                            scope.setExtra("totalSignaturesCount", "${data?.signatures?.count()}")
                            scope.setExtra("email", AccountUtils.currentMailboxEmail.toString())
                            Sentry.captureMessage("Signature: This user has several default Signatures")
                        }
                    }

                    SignatureController.update(data?.signatures ?: emptyList(), realm)
                } else {
                    throwErrorAsException()
                }
            }
        }
    }
}
