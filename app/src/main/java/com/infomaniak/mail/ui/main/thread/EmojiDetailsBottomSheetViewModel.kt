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
package com.infomaniak.mail.ui.main.thread

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.emojicomponents.data.EmojiDetail
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmojiDetailsBottomSheetViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val messageController: MessageController,
) : ViewModel() {
    private val messageUidFlow = MutableSharedFlow<String>(replay = 1)

    // val _emojiDetails = MutableSharedFlow<List<EmojiDetail>>()
    // val emojiDetails: SharedFlow<List<EmojiDetail>> = _emojiDetails.asSharedFlow()
    val emojiDetails = messageUidFlow.mapNotNull {
        val messageUid = it
        Log.e("gibran", "flow - messageUid: ${messageUid}")
        messageUid?.let {
            val message = messageController.getMessage(it) ?: return@mapNotNull null
            val emojiDetails = message.emojiReactions
                .entries
                .filterOutNullStates()
                .map { (emoji, state) -> EmojiDetail(emoji, state.count.toString()) }

            emojiDetails
        }
    }

    fun loadEmojiDetailsFor(messageUid: String) {
        viewModelScope.launch {
            Log.e("gibran", "loadEmojiDetailsFor - emitting messageUid: ${messageUid}")
            messageUidFlow.emit(messageUid)
        }
    }
}
