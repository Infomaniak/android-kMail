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
package com.infomaniak.mail.ui.newMessage

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.ai.AiMessage
import com.infomaniak.mail.data.models.ai.AiPromptOpeningStatus
import com.infomaniak.mail.data.models.ai.AiResult
import com.infomaniak.mail.data.models.ai.AssistantMessage
import com.infomaniak.mail.data.models.ai.ContextMessage
import com.infomaniak.mail.data.models.ai.UserMessage
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.newMessage.AiViewModel.PropositionStatus.CONTEXT_TOO_LONG
import com.infomaniak.mail.ui.newMessage.AiViewModel.PropositionStatus.ERROR
import com.infomaniak.mail.ui.newMessage.AiViewModel.PropositionStatus.MISSING_CONTENT
import com.infomaniak.mail.ui.newMessage.AiViewModel.PropositionStatus.PROMPT_TOO_LONG
import com.infomaniak.mail.ui.newMessage.AiViewModel.PropositionStatus.RATE_LIMIT_EXCEEDED
import com.infomaniak.mail.ui.newMessage.AiViewModel.PropositionStatus.SUCCESS
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.ErrorCode.MAX_SYNTAX_TOKENS_REACHED
import com.infomaniak.mail.utils.ErrorCode.TOO_MANY_REQUESTS
import com.infomaniak.mail.utils.SharedUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiViewModel @Inject constructor(
    private val sharedUtils: SharedUtils,
    aiSharedData: AiSharedData,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    lateinit var localSettings: LocalSettings

    var aiPrompt = ""
    private val history = mutableListOf<AiMessage>()
    private var conversationContextId: String? = null
    var aiPromptOpeningStatus = MutableLiveData<AiPromptOpeningStatus>()
    var previousMessageBodyPlainText by aiSharedData::previousMessageBodyPlainText

    val aiPropositionStatusLiveData = MutableLiveData<PropositionStatus>()
    val aiOutputToInsert = SingleLiveEvent<Pair<String?, String>>()

    fun generateNewAiProposition(
        currentMailboxUuid: String,
        formattedRecipientsString: String?,
    ) = viewModelScope.launch(ioDispatcher) {

        fun addVars(message: AiMessage) {
            formattedRecipientsString?.let { message.vars["recipient"] = it }
        }

        history.clear()

        val contextMessage = previousMessageBodyPlainText?.let { ContextMessage(it).also(::addVars) }
        val userMessage = UserMessage(aiPrompt).also(::addVars)

        contextMessage?.let(history::add)

        val apiResponse = ApiRepository.startNewConversation(
            contextMessage,
            userMessage,
            currentMailboxUuid,
        )

        ensureActive()
        handleAiResult(apiResponse, userMessage, isUsingPreviousMessageAsContext = previousMessageBodyPlainText != null)
    }

    fun splitBodyAndSubject(proposition: String): Pair<String?, String> {
        val match = MATCH_SUBJECT_REGEX.find(proposition)
        // The method get on MatchGroupCollection is not available on API25
        return splitBodyAndSubject(match, proposition)
    }

    private fun splitBodyAndSubject(match: MatchResult?, proposition: String): Pair<String?, String> {
        val content = match?.groups?.get("content")?.value ?: return null to proposition
        val subject = match.groups["subject"]?.value?.trim()

        if (subject.isNullOrBlank()) return null to proposition

        return subject to content
    }

    private fun handleAiResult(
        apiResponse: ApiResponse<AiResult>,
        promptMessage: AiMessage?,
        isUsingPreviousMessageAsContext: Boolean,
    ) = with(apiResponse) {
        val propositionStatus = when {
            isSuccess() -> updateHistoryAndContext(promptMessage!!)
            error?.code == MAX_SYNTAX_TOKENS_REACHED -> handleMaxTokenAndChooseStatus(isUsingPreviousMessageAsContext)
            error?.code == TOO_MANY_REQUESTS -> RATE_LIMIT_EXCEEDED
            else -> ERROR
        }

        aiPropositionStatusLiveData.postValue(propositionStatus)
    }

    private fun ApiResponse<AiResult>.updateHistoryAndContext(promptMessage: AiMessage): PropositionStatus {
        return data?.let { aiResult ->
            aiResult.contextId?.let { conversationContextId = it }
            history += promptMessage
            history += AssistantMessage(aiResult.content)
            SUCCESS
        } ?: MISSING_CONTENT
    }

    private fun handleMaxTokenAndChooseStatus(isUsingPreviousMessageAsContext: Boolean) = if (isUsingPreviousMessageAsContext) {
        previousMessageBodyPlainText = null // Discard previous mail context
        CONTEXT_TOO_LONG
    } else {
        PROMPT_TOO_LONG
    }

    fun performShortcut(shortcut: Shortcut, currentMailboxUuid: String) = viewModelScope.launch(ioDispatcher) {
        var apiResponse = ApiRepository.aiShortcutWithContext(conversationContextId!!, shortcut, currentMailboxUuid)
        ensureActive()

        val hasConversationExpired = apiResponse.error?.code == ErrorCode.OBJECT_NOT_FOUND
        if (hasConversationExpired) {
            apiResponse = ApiRepository.aiShortcutNoContext(shortcut, history.toList(), currentMailboxUuid)
            ensureActive()
        }

        handleAiResult(apiResponse, apiResponse.data?.promptMessage, isUsingPreviousMessageAsContext = false)
    }

    fun updateFeatureFlag(mailboxObjectId: String, mailboxUuid: String) = viewModelScope.launch(ioDispatcher) {
        sharedUtils.updateFeatureFlags(mailboxObjectId, mailboxUuid)
    }

    fun isHistoryEmpty(): Boolean = history.excludingContextMessage().isEmpty()

    private fun List<AiMessage>.excludingContextMessage(): List<AiMessage> = filterNot { it.type == "context" }

    fun getLastMessage(): String = history.last().content

    enum class Shortcut(@IdRes val menuId: Int, val apiRoute: String?, val matomoName: MatomoName) {
        MODIFY(R.id.modify, null, MatomoName.Edit),
        REGENERATE(R.id.regenerate, "redraw", MatomoName.Regenerate),
        SHORTEN(R.id.shorten, "shorten", MatomoName.Shorten),
        LENGTHEN(R.id.lengthen, "develop", MatomoName.Expand),
        SERIOUS_TONE(R.id.seriousTone, "tune-professional", MatomoName.SeriousWriting),
        FRIENDLY_TONE(R.id.friendlyTone, "tune-friendly", MatomoName.FriendlyWriting),
    }

    enum class PropositionStatus(@StringRes val errorRes: Int?) {
        SUCCESS(null),
        ERROR(R.string.aiErrorUnknown),
        PROMPT_TOO_LONG(R.string.aiErrorMaxTokenReached),
        CONTEXT_TOO_LONG(R.string.aiErrorContextMaxTokenReached),
        RATE_LIMIT_EXCEEDED(R.string.aiErrorTooManyRequests),
        MISSING_CONTENT(R.string.aiErrorUnknown),
    }

    companion object {
        private val MATCH_SUBJECT_REGEX = Regex("^[^:]+:(?<subject>.+?)\\n\\s*(?<content>.+)", RegexOption.DOT_MATCHES_ALL)
    }
}
