/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.folder

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.Attachment.AttachmentType
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.main.thread.DetailedContactBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.DownloadAttachmentProgressDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.MessageActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ReplyBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.thread.actions.ThreadActionsBottomSheetDialogArgs
import com.infomaniak.mail.ui.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TwoPaneViewModel @Inject constructor(
    private val draftController: DraftController,
) : ViewModel() {

    val triggerSlidingButAlsoCarryingThreadUid = MutableLiveData<String?>(null)
    val currentThreadUid = MutableLiveData<String?>(null)

    inline val isThreadOpen get() = currentThreadUid.value != null
    val rightPaneFolderName = MutableLiveData<String>()
    var previousFolderId: String? = null

    val downloadAttachmentArgs = SingleLiveEvent<DownloadAttachmentProgressDialogArgs>()
    val newMessageArgs = SingleLiveEvent<NewMessageActivityArgs>()
    val replyBottomSheetArgs = SingleLiveEvent<ReplyBottomSheetDialogArgs>()
    val threadActionsArgs = SingleLiveEvent<ThreadActionsBottomSheetDialogArgs>()
    val messageActionsArgs = SingleLiveEvent<MessageActionsBottomSheetDialogArgs>()
    val detailedContactArgs = SingleLiveEvent<DetailedContactBottomSheetDialogArgs>()

    fun openThread(uid: String) {
        triggerSlidingButAlsoCarryingThreadUid.value = uid
    }

    fun closeThread() {
        triggerSlidingButAlsoCarryingThreadUid.value = null
    }

    fun openDraft(thread: Thread) {
        navigateToSelectedDraft(thread.messages.single())
    }

    fun navigateToDownloadAttachment(resource: String, name: String, type: AttachmentType) {
        downloadAttachmentArgs.value = DownloadAttachmentProgressDialogArgs(resource, name, type)
    }

    private fun navigateToSelectedDraft(message: Message) = runCatchingRealm {
        newMessageArgs.value = NewMessageActivityArgs(
            arrivedFromExistingDraft = true,
            draftLocalUuid = draftController.getDraftByMessageUid(message.uid)?.localUuid,
            draftResource = message.draftResource,
            messageUid = message.uid,
        )
    }

    fun navigateToNewMessage(
        draftMode: DraftMode = DraftMode.NEW_MAIL,
        previousMessageUid: String? = null,
        shouldLoadDistantResources: Boolean = false,
        arrivedFromExistingDraft: Boolean = false,
        draftLocalUuid: String? = null,
        draftResource: String? = null,
        messageUid: String? = null,
        mailToUri: Uri? = null,
    ) {
        newMessageArgs.value = NewMessageActivityArgs(
            draftMode = draftMode,
            previousMessageUid = previousMessageUid,
            shouldLoadDistantResources = shouldLoadDistantResources,
            arrivedFromExistingDraft = arrivedFromExistingDraft,
            draftLocalUuid = draftLocalUuid,
            draftResource = draftResource,
            messageUid = messageUid,
            mailToUri = mailToUri,
        )
    }

    fun navigateToReply(messageUid: String, shouldLoadDistantResources: Boolean) {
        replyBottomSheetArgs.value = ReplyBottomSheetDialogArgs(messageUid, shouldLoadDistantResources)
    }

    fun navigateToThreadActions(threadUid: String, shouldLoadDistantResources: Boolean, messageUidToReplyTo: String) {
        threadActionsArgs.value = ThreadActionsBottomSheetDialogArgs(threadUid, shouldLoadDistantResources, messageUidToReplyTo)
    }

    fun navigateToMessageAction(messageUid: String, isThemeTheSame: Boolean, shouldLoadDistantResources: Boolean) {
        messageActionsArgs.value = MessageActionsBottomSheetDialogArgs(
            messageUid = messageUid,
            threadUid = currentThreadUid.value ?: return,
            isThemeTheSame = isThemeTheSame,
            shouldLoadDistantResources = shouldLoadDistantResources,
        )
    }

    fun navigateToDetailContact(recipient: Recipient) {
        detailedContactArgs.value = DetailedContactBottomSheetDialogArgs(recipient)
    }
}
