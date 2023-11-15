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
package com.infomaniak.mail.ui.newMessage

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.ai.*
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.newMessage.AiViewModel.PropositionStatus.*
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.ErrorCode.MAX_SYNTAX_TOKENS_REACHED
import com.infomaniak.mail.utils.ErrorCode.TOO_MANY_REQUESTS
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiViewModel @Inject constructor(
    private val sharedUtils: SharedUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    lateinit var localSettings: LocalSettings

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    var aiPrompt = ""
    private var history = mutableListOf<AiMessage>()
    private var conversationContextId: String? = null
    var aiPromptOpeningStatus = MutableLiveData<AiPromptOpeningStatus>()

    val aiPropositionStatusLiveData = MutableLiveData<PropositionStatus>()
    val aiOutputToInsert = SingleLiveEvent<Pair<String?, String>>()

    fun generateNewAiProposition(
        currentMailboxUuid: String,
        previousMessageBodyPlainText: String?,
    ) = viewModelScope.launch(ioCoroutineContext) {
        history.clear()

        val contextMessage = previousMessageBodyPlainText?.let { ContextMessage(it) }
        val userMessage = UserMessage(aiPrompt)

        contextMessage?.let(history::add)

        val apiResponse = ApiRepository.startNewConversation(
            contextMessage,
            userMessage,
            currentMailboxUuid,
            localSettings.aiEngine,
        )

        ensureActive()
        handleAiResult(apiResponse, userMessage)
    }

    private fun handleAiResult(apiResponse: ApiResponse<AiResult>, promptMessage: AiMessage?) = with(apiResponse) {
        aiPropositionStatusLiveData.postValue(
            when {
                isSuccess() -> data?.let { aiResult ->
                    aiResult.contextId?.let { conversationContextId = it }
                    history += promptMessage!!
                    history += AssistantMessage(aiResult.content)
                    SUCCESS
                } ?: MISSING_CONTENT
                error?.code == MAX_SYNTAX_TOKENS_REACHED -> PROMPT_TOO_LONG
                error?.code == TOO_MANY_REQUESTS -> RATE_LIMIT_EXCEEDED
                else -> ERROR
            }
        )
    }

    fun performShortcut(shortcut: Shortcut, currentMailboxUuid: String) = viewModelScope.launch(ioCoroutineContext) {
        val aiEngine = localSettings.aiEngine

        var apiResponse = ApiRepository.aiShortcutWithContext(conversationContextId!!, shortcut, currentMailboxUuid, aiEngine)
        ensureActive()

        val hasConversationExpired = apiResponse.error?.code == ErrorCode.OBJECT_NOT_FOUND
        if (hasConversationExpired) {
            apiResponse = ApiRepository.aiShortcutNoContext(shortcut, history.toList(), currentMailboxUuid, aiEngine)
            ensureActive()
        }

        handleAiResult(apiResponse, apiResponse.data?.promptMessage)
    }

    fun updateFeatureFlag(currentMailboxObjectId: String, mailboxUuid: String) = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.updateAiFeatureFlag(currentMailboxObjectId, mailboxUuid)
    }

    fun isHistoryEmpty(): Boolean = history.isEmpty()

    fun getLastMessage(): String = history.last().content

    enum class Shortcut(@IdRes val menuId: Int, val apiRoute: String?, val matomoValue: String) {
        MODIFY(R.id.modify, null, "edit"),
        REGENERATE(R.id.regenerate, "redraw", "regenerate"),
        SHORTEN(R.id.shorten, "shorten", "shorten"),
        LENGTHEN(R.id.lengthen, "develop", "expand"),
        SERIOUS_TONE(R.id.seriousTone, "tune-professional", "seriousWriting"),
        FRIENDLY_TONE(R.id.friendlyTone, "tune-friendly", "friendlyWriting"),
    }

    enum class PropositionStatus(@StringRes val errorRes: Int?) {
        SUCCESS(null),
        ERROR(R.string.aiErrorUnknown),
        PROMPT_TOO_LONG(R.string.aiErrorMaxTokenReached),
        RATE_LIMIT_EXCEEDED(R.string.aiErrorTooManyRequests),
        MISSING_CONTENT(R.string.aiErrorUnknown),
    }
}
