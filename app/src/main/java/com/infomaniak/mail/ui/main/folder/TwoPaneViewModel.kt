/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.ui.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.canDisplayOnlyOnePane
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TwoPaneViewModel @Inject constructor(
    private val state: SavedStateHandle,
    private val draftController: DraftController,
) : ViewModel() {

    val currentThreadUid: LiveData<String?> = state.getLiveData(CURRENT_THREAD_UID_KEY)

    inline val isThreadOpen get() = currentThreadUid.value != null
    val rightPaneFolderName = MutableLiveData<String>()
    var previousFolderId: String? = null

    val newMessageArgs = SingleLiveEvent<NewMessageActivityArgs>()
    val navArgs = SingleLiveEvent<NavData>()

    var isOnlyOneShown: Boolean = true
    var isOnlyLeftShown: Boolean = true
    var isOnlyRightShown: Boolean = false

    fun openThread(uid: String) {
        state[CURRENT_THREAD_UID_KEY] = uid
    }

    fun closeThread() {
        state[CURRENT_THREAD_UID_KEY] = null
    }

    fun openDraft(thread: Thread) {
        navigateToSelectedDraft(thread.messages.single())
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

    fun isInThreadInPhoneMode(context: Context): Boolean = isThreadOpen && context.canDisplayOnlyOnePane()

    data class NavData(
        @IdRes val resId: Int,
        val args: Bundle,
    )

    companion object {
        private const val CURRENT_THREAD_UID_KEY = "currentThreadUidKey"
    }
}
