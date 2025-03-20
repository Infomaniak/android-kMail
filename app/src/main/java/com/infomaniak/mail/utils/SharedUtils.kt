/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
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
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.main.settings.SettingRadioGroupView
import com.infomaniak.mail.utils.JsoupParserUtil.jsoupParseWithLog
import com.infomaniak.mail.utils.extensions.atLeastOneSucceeded
import com.infomaniak.mail.utils.extensions.getApiException
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.toRealmList
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

class SharedUtils @Inject constructor(
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val refreshController: RefreshController,
    private val messageController: MessageController,
    private val mailboxController: MailboxController,
) {

    @Inject
    lateinit var localSettings: LocalSettings

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
            null -> threads.flatMap(messageController::getUnseenMessages)
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

    suspend fun unsnoozeThreads(scope: CoroutineScope, mailbox: Mailbox, threads: List<Thread>) {
        val impactedFolders = unsnoozeThreadsWithoutRefresh(scope, mailbox, threads)
        if (impactedFolders.isNotEmpty()) {
            refreshFolders(mailbox = mailbox, messagesFoldersIds = ImpactedFolders(impactedFolders.toMutableSet()))
        }
    }

    fun getMessagesToMove(threads: List<Thread>, message: Message?) = when (message) {
        null -> threads.flatMap(messageController::getMovableMessages)
        else -> messageController.getMessageAndDuplicates(threads.first(), message)
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

    fun manageAiEngineSettings(
        fragment: Fragment,
        settingRadioGroupView: SettingRadioGroupView,
        matomoCategory: String,
        onClick: (() -> Unit)? = null,
    ) {
        settingRadioGroupView.initBijectionTable(
            R.id.falcon to LocalSettings.AiEngine.FALCON,
            R.id.chatGpt to LocalSettings.AiEngine.CHAT_GPT,
        )

        settingRadioGroupView.check(localSettings.aiEngine)

        settingRadioGroupView.onItemCheckedListener { _, _, engine ->
            localSettings.aiEngine = engine as LocalSettings.AiEngine
            fragment.trackEvent(matomoCategory, engine.matomoValue)
            onClick?.invoke()
        }
    }

    private class SignatureException(message: String?, cause: Throwable) : Exception(message, cause)

    companion object {

        suspend fun updateSignatures(mailbox: Mailbox, customRealm: Realm): Int? {
            return with(ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)) {
                return@with if (isSuccess()) {
                    val signaturesResult = data!!
                    customRealm.write {
                        MailboxController.getMailbox(mailbox.objectId, realm = this)?.let { mailbox ->
                            mailbox.signatures = signaturesResult.signatures.toMutableList().apply {
                                val defaultSignature = firstOrNull { it.id == signaturesResult.defaultSignatureId }
                                val defaultReplySignature = firstOrNull { it.id == signaturesResult.defaultReplySignatureId }
                                    ?: defaultSignature

                                defaultSignature?.isDefault = true
                                defaultReplySignature?.isDefaultReply = true
                            }.toRealmList()
                        }
                    }
                    null
                } else {
                    val apiException = getApiException()
                    if (apiException !is ApiController.NetworkException) {
                        Sentry.captureException(SignatureException(apiException.message, apiException))
                    }
                    translatedError
                }
            }
        }

        fun createHtmlForPlainText(text: String): String {
            jsoupParseWithLog("").apply {
                body().appendElement("pre").text(text).attr("style", "word-wrap: break-word; white-space: pre-wrap;")
                return html()
            }
        }

        fun unsnoozeThreadsWithoutRefresh(scope: CoroutineScope, mailbox: Mailbox, threads: List<Thread>): Set<String> {
            val messagesUids: MutableList<String> = mutableListOf()
            val impactedFolderIds: MutableSet<String> = mutableSetOf()

            threads.forEach { thread ->
                scope.ensureActive()

                val targetMessage = thread.messages.last(Message::isSnoozed) // TODO: Fix crash if message not found
                messagesUids += targetMessage.uid
                impactedFolderIds += targetMessage.folderId
            }

            if (messagesUids.isEmpty()) return emptySet()

            val apiResponses = ApiRepository.unsnoozeMessages(mailbox.uuid, messagesUids)
            scope.ensureActive()

            return if (apiResponses.atLeastOneSucceeded()) impactedFolderIds else emptySet()
        }
    }
}
