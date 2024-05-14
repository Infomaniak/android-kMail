/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import com.infomaniak.lib.core.utils.isNetworkException
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshCallbacks
import com.infomaniak.mail.data.cache.mailboxContent.RefreshController.RefreshMode
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.main.settings.SettingRadioGroupView
import com.infomaniak.mail.utils.extensions.getApiException
import com.infomaniak.mail.utils.extensions.getFoldersIds
import com.infomaniak.mail.utils.extensions.getUids
import io.realm.kotlin.Realm
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.jsoup.Jsoup
import javax.inject.Inject

class SharedUtils @Inject constructor(
    private val folderController: FolderController,
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
     * @param shouldRefreshThreads Sometimes, we don't want to refresh Threads after doing this action. For example, when replying to a Message.
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

        val isSuccess = ApiRepository.markMessagesAsSeen(mailbox.uuid, messages.getUids()).isSuccess()

        if (isSuccess && shouldRefreshThreads) {
            refreshFolders(
                mailbox = mailbox,
                messagesFoldersIds = messages.getFoldersIds(),
                currentFolderId = currentFolderId,
                callbacks = callbacks,
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
        currentFolderId: String? = null,
        callbacks: RefreshCallbacks? = null,
    ) {

        // We always want to refresh the `destinationFolder` last, to avoid any blink on the UI.
        val foldersIds = messagesFoldersIds.toMutableSet()
        destinationFolderId?.let(foldersIds::add)

        foldersIds.forEach { folderId ->
            folderController.getFolder(folderId)?.let { folder ->
                refreshController.refreshThreads(
                    refreshMode = RefreshMode.REFRESH_FOLDER,
                    mailbox = mailbox,
                    folder = folder,
                    realm = mailboxContentRealm(),
                    callbacks = if (folderId == currentFolderId) callbacks else null,
                )
            }
        }
    }

    fun updateAiFeatureFlag(mailboxObjectId: String, mailboxUuid: String) {
        with(ApiRepository.checkFeatureFlag(FeatureFlag.AI, mailboxUuid)) {
            if (isSuccess()) {
                val isEnabled = data?.get("is_enabled") == true
                mailboxController.updateMailbox(mailboxObjectId) {
                    if (isEnabled) it.featureFlags.add(FeatureFlag.AI) else it.featureFlags.remove(FeatureFlag.AI)
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

        fun updateSignatures(mailbox: Mailbox, customRealm: Realm): Int? {
            with(ApiRepository.getSignatures(mailbox.hostingId, mailbox.mailboxName)) {
                return if (isSuccess() && data?.signatures?.isNotEmpty() == true) {
                    customRealm.writeBlocking { SignatureController.update(data!!.signatures, realm = this) }
                    null
                } else {
                    when {
                        error?.exception?.isNetworkException() == true -> Unit
                        error?.exception != null -> {
                            val apiException = getApiException()
                            Sentry.captureException(SignatureException(apiException.message, apiException))
                        }
                        data?.signatures?.isEmpty() == true -> {
                            Sentry.captureMessage("Signatures list is empty", SentryLevel.ERROR)
                        }
                    }
                    translatedError
                }
            }
        }

        fun createHtmlForPlainText(text: String): String {
            Jsoup.parse("").apply {
                body().appendElement("pre").text(text).attr("style", "word-wrap: break-word; white-space: pre-wrap;")
                return html()
            }
        }
    }
}
