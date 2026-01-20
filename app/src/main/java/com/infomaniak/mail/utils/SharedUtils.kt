/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.exceptions.NetworkException
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshCallbacks
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResponse.Companion.computeSnoozeResult
import com.infomaniak.mail.data.models.snooze.BatchSnoozeResult
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.JsoupParserUtil.jsoupParseWithLog
import com.infomaniak.mail.utils.SharedUtils.Companion.unsnoozeThreadsWithoutRefresh
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getApiException
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.toRealmList
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import java.util.Date
import javax.inject.Inject

class SharedUtils @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val refreshController: RefreshController,
    private val messageController: MessageController,
    private val mailboxController: MailboxController,
) {
    /**
     * Mark a Message or some Threads as read
     * @param mailbox The Mailbox where the Threads & Messages are located
     * @param threads The Threads to mark as read
     * @param message The Message to mark as read
     * @param callbacks The callbacks for when the refresh of Threads begins/ends
     * @param shouldRefreshThreads Sometimes, we don't want to refresh Threads after doing this action. For example, when replying
     * to a Message.
     */
    suspend fun markAsSeen(
        mailbox: Mailbox,
        threads: List<Thread>,
        message: Message? = null,
        currentFolderId: String? = null,
        callbacks: RefreshCallbacks? = null,
        shouldRefreshThreads: Boolean = true,
    ) {

        val messages = when (message) {
            null -> threads.flatMap { messageController.getUnseenMessages(it) }
            else -> messageController.getMessageAndDuplicates(threads.first(), message)
        }

        val threadsUids = threads.map { it.uid }
        val messagesUids = messages.map { it.uid }

        updateSeenStatus(threadsUids, messagesUids, isSeen = true)

        val apiResponses = ApiRepository.markMessagesAsSeen(mailbox.uuid, messages.getUids())

        if (apiResponses.atLeastOneSucceeded() && shouldRefreshThreads) {
            refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                currentFolderId = currentFolderId,
                callbacks = callbacks,
            )
        }

        if (!apiResponses.atLeastOneSucceeded()) updateSeenStatus(threadsUids, messagesUids, isSeen = false)
    }

    private suspend fun updateSeenStatus(threadsUids: List<String>, messagesUids: List<String>, isSeen: Boolean) {
        mailboxContentRealm().write {
            MessageController.updateSeenStatus(messagesUids, isSeen, realm = this)
            ThreadController.updateSeenStatus(threadsUids, isSeen, realm = this)
        }
    }

    suspend fun getMessagesToMove(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap { messageController.getMovableMessages(it) }
        else -> listOf(message)
    }

    suspend fun refreshFolders(
        mailbox: Mailbox,
        messagesFoldersIds: ImpactedFolders,
        destinationFolderId: String? = null,
        currentFolderId: String? = null,
        callbacks: RefreshCallbacks? = null,
    ) {
        val realm = mailboxContentRealm()

        // We always want to refresh the `destinationFolder` last, to avoid any blink on the UI.
        val foldersIds = messagesFoldersIds.getFolderIds(realm).toMutableSet()
        destinationFolderId?.let(foldersIds::add)

        foldersIds.forEach { folderId ->
            refreshController.refreshThreads(
                refreshMode = RefreshMode.REFRESH_FOLDER,
                mailbox = mailbox,
                folderId = folderId,
                realm = realm,
                callbacks = if (folderId == currentFolderId) callbacks else null,
            )
        }
    }

    suspend fun updateFeatureFlags(mailboxObjectId: String, mailboxUuid: String) {
        with(ApiRepository.getFeatureFlags(mailboxUuid)) {
            if (isSuccess()) {
                mailboxController.updateMailbox(mailboxObjectId) {
                    it.featureFlags.setFeatureFlags(featureFlags = data ?: emptyList())
                }
            }
        }
    }

    private class SignatureException(message: String?, cause: Throwable) : Exception(message, cause)

    companion object {

        suspend fun updateSignatures(mailbox: Mailbox, customRealm: Realm, okHttpClient: OkHttpClient? = null): Int? {
            return with(ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName, okHttpClient)) {
                return@with if (isSuccess()) {
                    val signaturesResult = data!!
                    customRealm.write {
                        MailboxController.getMailboxBlocking(mailbox.objectId, realm = this)?.let { mailbox ->
                            mailbox.signatures = signaturesResult.signatures.toMutableList().apply {
                                val defaultSignature = firstOrNull { it.id == signaturesResult.defaultSignatureId }
                                val defaultReplySignature = firstOrNull { it.id == signaturesResult.defaultReplySignatureId }
                                    ?: defaultSignature

                                defaultSignature?.isDefault = true
                                defaultReplySignature?.isDefaultReply = true
                            }.toRealmList()

                            mailbox.haveSignatureNeverBeenFetched = false
                        }
                    }
                    null
                } else {
                    val apiException = getApiException()
                    if (apiException !is NetworkException) {
                        Sentry.captureException(SignatureException(apiException.message, apiException))
                    }
                    translateError()
                }
            }
        }

        fun createHtmlForPlainText(text: String): String {
            jsoupParseWithLog("").apply {
                body().appendElement("pre").text(text).attr("style", "word-wrap: break-word; white-space: pre-wrap;")
                return html()
            }
        }

        /**
         * Will manually switch to the single uuid api call when batching over a single message. This is needed to receive api
         * errors from the api, because batches will never return api errors for now.
         */
        suspend fun rescheduleSnoozedThreads(
            mailboxUuid: String,
            snoozeUuids: List<String>,
            newDate: Date,
            impactedFolders: ImpactedFolders,
        ): BatchSnoozeResult {
            return if (snoozeUuids.count() == 1) {
                val snoozeUuid = snoozeUuids.single()
                ApiRepository.rescheduleSnoozedThread(mailboxUuid, snoozeUuid, newDate).computeSnoozeResult(impactedFolders)
            } else {
                ApiRepository.rescheduleSnoozedThreads(mailboxUuid, snoozeUuids, newDate).computeSnoozeResult(impactedFolders)
            }
        }

        /**
         * Will manually switch to the single uuid api call when batching over a single message. This is needed to receive api
         * errors from the api, because batches will never return api errors for now.
         */
        suspend fun unsnoozeThreads(
            mailboxUuid: String,
            snoozeUuids: List<String>,
            impactedFolders: ImpactedFolders
        ): BatchSnoozeResult {
            return if (snoozeUuids.count() == 1) {
                ApiRepository.unsnoozeThread(mailboxUuid, snoozeUuids.single()).computeSnoozeResult(impactedFolders)
            } else {
                ApiRepository.unsnoozeThreads(mailboxUuid, snoozeUuids).computeSnoozeResult(impactedFolders)
            }
        }

        /**
         * When manually unsnoozing threads, we need to know if a thread we've tried to unsnoozed failed to be unsnoozed because
         * of a unrecoverable reason. This way we can stop trying to unsnooze it again and again. To know this, we need to use the
         * api call that takes a single snooze uuid or we won't get the ApiResponse's error code that we need to detect this case.
         *
         * Start using [unsnoozeThreadsWithoutRefresh] again if we find a way to get this info with the batch call.
         */
        suspend fun unsnoozeThreadWithoutRefresh(mailbox: Mailbox, thread: Thread): AutomaticUnsnoozeResult {
            val targetMessage = thread.messages.lastOrNull(Message::isSnoozed) ?: return AutomaticUnsnoozeResult.OtherError
            val targetMessageSnoozeUuid = targetMessage.snoozeUuid ?: return AutomaticUnsnoozeResult.OtherError

            val apiResponse = ApiRepository.unsnoozeThread(mailbox.uuid, targetMessageSnoozeUuid)

            return when {
                apiResponse.isSuccess() -> AutomaticUnsnoozeResult.Success(
                    // targetMessage.folderId will never return the folder "snooze". We need to add it manually
                    ImpactedFolders(mutableSetOf(targetMessage.folderId), mutableSetOf(FolderRole.SNOOZED)),
                )
                apiResponse.willNeverSucceed() -> AutomaticUnsnoozeResult.CannotBeUnsnoozedError
                else -> AutomaticUnsnoozeResult.OtherError
            }
        }

        private fun ApiResponse<Boolean>.willNeverSucceed() = error?.let {
            it.code == ErrorCode.MAIL_MESSAGE_NOT_SNOOZED || it.code == ErrorCode.OBJECT_NOT_FOUND
        } ?: false

        /**
         * @param scope Is needed for the thread algorithm that handles cancellation by passing down a scope to everyone.
         * Outside of this algorithm, the scope doesn't need to be defined and the method can be used like any other.
         */
        suspend fun unsnoozeThreadsWithoutRefresh(
            scope: CoroutineScope?,
            mailbox: Mailbox,
            threads: Collection<Thread>,
        ): BatchSnoozeResult {
            val snoozeUuids: MutableList<String> = mutableListOf()
            val impactedFolderIds: MutableSet<String> = mutableSetOf()

            for (thread in threads) {
                scope?.ensureActive()

                val targetMessage = thread.messages.lastOrNull(Message::isSnoozed)
                val targetMessageSnoozeUuid = targetMessage?.snoozeUuid ?: continue

                snoozeUuids += targetMessageSnoozeUuid
                impactedFolderIds += targetMessage.folderId
            }

            if (snoozeUuids.isEmpty()) return BatchSnoozeResult.Error.Unknown

            // When removing the snooze state of a thread, we absolutely need to refresh the snooze folder. Refreshing the
            // snooze folder is the only way of updating the snooze status of messages. The folder snooze will never be
            // returned inside impactedFolders because no message ever mentions the snooze folder, we need to add it manually.
            return unsnoozeThreads(
                mailboxUuid = mailbox.uuid,
                snoozeUuids = snoozeUuids,
                impactedFolders = ImpactedFolders(impactedFolderIds, mutableSetOf(FolderRole.SNOOZED))
            )
        }

        fun shouldDisplaySnoozeActions(
            mainViewModel: MainViewModel,
            localSettings: LocalSettings,
            currentFolderRole: FolderRole?,
        ): Boolean {
            fun isSnoozeAvailable() = FeatureAvailability.isSnoozeAvailable(mainViewModel.featureFlagsLive.value, localSettings)
            return currentFolderRole == FolderRole.INBOX || currentFolderRole == FolderRole.SNOOZED && isSnoozeAvailable()
        }

        sealed interface AutomaticUnsnoozeResult {
            data class Success(val impactedFolders: ImpactedFolders) : AutomaticUnsnoozeResult
            data object CannotBeUnsnoozedError : AutomaticUnsnoozeResult
            data object OtherError : AutomaticUnsnoozeResult
        }
    }
}
