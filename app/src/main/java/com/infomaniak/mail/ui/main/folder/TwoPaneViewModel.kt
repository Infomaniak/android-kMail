/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.thread.ThreadViewModel.SnoozeScheduleType
import com.infomaniak.mail.ui.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TwoPaneViewModel @Inject constructor(
    private val state: SavedStateHandle,
    private val draftController: DraftController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val currentThreadUid: LiveData<String?> = state.getLiveData(CURRENT_THREAD_UID_KEY)

    inline val isThreadOpen get() = currentThreadUid.value != null
    val rightPaneFolderName = MutableLiveData<String>()
    var previousFolderId: String? = null

    // Remember what type of snooze action the snooze schedule bottom sheet is used for,
    // so we know what call to execute when a date is chosen
    var snoozeScheduleType: SnoozeScheduleType? = null

    val newMessageArgs = SingleLiveEvent<NewMessageActivityArgs>()
    val navArgs = SingleLiveEvent<NavData>()

    fun openThread(uid: String) {
        state[CURRENT_THREAD_UID_KEY] = uid
    }

    fun closeThread() {
        state[CURRENT_THREAD_UID_KEY] = null
    }

    fun openDraft(thread: Thread) = viewModelScope.launch(ioDispatcher) {
        navigateToSelectedDraft(thread.messages.single())
    }

    private suspend fun navigateToSelectedDraft(message: Message) = runCatchingRealm {
        newMessageArgs.postValue(
            NewMessageActivityArgs(
                arrivedFromExistingDraft = true,
                draftLocalUuid = draftController.getDraftByMessageUid(message.uid)?.localUuid,
                draftResource = message.draftResource,
                messageUid = message.uid,
            ),
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
        newMessageArgs.postValue(
            NewMessageActivityArgs(
                draftMode = draftMode,
                previousMessageUid = previousMessageUid,
                shouldLoadDistantResources = shouldLoadDistantResources,
                arrivedFromExistingDraft = arrivedFromExistingDraft,
                draftLocalUuid = draftLocalUuid,
                draftResource = draftResource,
                messageUid = messageUid,
                mailToUri = mailToUri,
            ),
        )
    }

    fun safelyNavigate(@IdRes resId: Int, args: Bundle) {
        navArgs.value = NavData(resId, args)
    }

    data class NavData(
        @IdRes val resId: Int,
        val args: Bundle,
    )

    companion object {
        private const val CURRENT_THREAD_UID_KEY = "currentThreadUidKey"
    }
}
