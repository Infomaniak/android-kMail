/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.emojicomponents.data.Reaction
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.DraftInitManager
import com.infomaniak.mail.utils.EmojiReactionUtils.hasAvailableReactionSlot
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.NetworkManager
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.infomaniak.core.legacy.R as RCore

@HiltViewModel
class EmojiReactionsViewModel @Inject constructor(
    application: Application,
    private val draftController: DraftController,
    private val draftInitManager: DraftInitManager,
    private val draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler,
    private val messageController: MessageController,
    private val networkManager: NetworkManager,
    private val snackbarManager: SnackbarManager,
) : AndroidViewModel(application) {
    /**
     * Wrapper method to send an emoji reaction to the api. This method will check if the emoji reaction is allowed before
     * initiating an api call. This is the entry point to add an emoji reaction anywhere in the app.
     *
     * If sending is allowed, the caller place can fake the emoji reaction locally thanks to [onAllowed].
     * If sending is not allowed, it will display the error directly to the user and avoid doing the api call.
     */
    val hasNetwork: Boolean get() = networkManager.hasNetwork

    fun trySendEmojiReply(
        emoji: String,
        messageUid: String,
        reactions: Map<String, Reaction>,
        mailbox: Mailbox,
        onAllowed: () -> Unit = {},
    ) {
        viewModelScope.launch {
            when (val status = reactions.getEmojiSendStatus(emoji)) {
                EmojiSendStatus.Allowed -> {
                    onAllowed()
                    sendEmojiReply(emoji, messageUid, mailbox)
                }
                is EmojiSendStatus.NotAllowed -> snackbarManager.postValue(appContext.getString(status.errorMessageRes))
            }
        }
    }

    private fun Map<String, Reaction>.getEmojiSendStatus(emoji: String): EmojiSendStatus = when {
        this[emoji]?.hasReacted == true -> EmojiSendStatus.NotAllowed.AlreadyUsed
        hasAvailableReactionSlot().not() -> EmojiSendStatus.NotAllowed.MaxReactionReached
        hasNetwork.not() -> EmojiSendStatus.NotAllowed.NoInternet
        else -> EmojiSendStatus.Allowed
    }

    /**
     * The actual logic of sending an emoji reaction to the api. This method initializes a [Draft] instance, stores it into the
     * database and schedules the [DraftsActionsWorker] so the draft is uploaded on the api.
     */
    private suspend fun sendEmojiReply(emoji: String, messageUid: String, mailbox: Mailbox) {
        val targetMessage = messageController.getMessage(messageUid) ?: return
        val (fullMessage, hasFailedFetching) = draftController.fetchHeavyDataIfNeeded(targetMessage)
        if (hasFailedFetching) return

        val draftMode = Draft.DraftMode.REPLY_ALL
        val draft = Draft().apply {
            with(draftInitManager) {
                setPreviousMessage(draftMode, fullMessage)
            }

            val quote = draftInitManager.createQuote(draftMode, fullMessage, attachments)
            body = EMOJI_REACTION_PLACEHOLDER + quote

            with(draftInitManager) {
                // We don't want to send the HTML code of the signature for an emoji reaction but we still need to send the
                // identityId stored in a Signature
                val signature = chooseSignature(mailbox.email, mailbox.signatures, draftMode, fullMessage)
                setSignatureIdentity(signature)
            }

            mimeType = Utils.TEXT_HTML

            action = Draft.DraftAction.SEND_REACTION
            emojiReaction = emoji
        }

        draftController.upsertDraft(draft)

        draftsActionsWorkerScheduler.scheduleWork(draft.localUuid, AccountUtils.currentMailboxId, AccountUtils.currentUserId)
    }

    private sealed interface EmojiSendStatus {
        data object Allowed : EmojiSendStatus

        sealed class NotAllowed(@StringRes val errorMessageRes: Int) : EmojiSendStatus {
            data object AlreadyUsed : NotAllowed(ErrorCode.EmojiReactions.alreadyUsed.translateRes)
            data object MaxReactionReached : NotAllowed(ErrorCode.EmojiReactions.maxReactionReached.translateRes)
            data object NoInternet : NotAllowed(RCore.string.noConnection)
        }
    }

    companion object {
        private const val EMOJI_REACTION_PLACEHOLDER = "<div>__REACTION_PLACEMENT__<br></div>"
    }
}
