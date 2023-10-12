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
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.userInfo.FeatureFlagController
import com.infomaniak.mail.data.models.FeatureFlag.FeatureFlagType
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
    private val featureFlagController: FeatureFlagController,
    private val sharedUtils: SharedUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    var aiPrompt = ""
    private var history = mutableListOf<AiMessage>()
    private var conversationContextId: String? = null
    var aiPromptOpeningStatus = MutableLiveData<AiPromptOpeningStatus>()

    val aiProposition = MutableLiveData<Pair<PropositionStatus, String?>?>()
    val aiOutputToInsert = SingleLiveEvent<String>()

    fun generateNewAiProposition() = viewModelScope.launch(ioCoroutineContext) {
        history.clear()
        val userMessage = UserMessage(aiPrompt)
        with(ApiRepository.startNewConversation(userMessage)) {
            ensureActive()
            handleAiResult(apiResponse = this, userMessage)
        }
    }

    private fun handleAiResult(apiResponse: ApiResponse<AiResult>, promptMessage: AiMessage?) = with(apiResponse) {
        aiProposition.postValue(
            when {
                isSuccess() -> data?.let { aiResult ->
                    aiResult.contextId?.let { conversationContextId = it }
                    history += promptMessage!!
                    history += AssistantMessage(aiResult.content)
                    SUCCESS to aiResult.content
                } ?: (MISSING_CONTENT to null)
                error?.code == MAX_SYNTAX_TOKENS_REACHED -> PROMPT_TOO_LONG to null
                error?.code == TOO_MANY_REQUESTS -> RATE_LIMIT_EXCEEDED to null
                else -> ERROR to null
            }
        )
    }

    fun performShortcut(shortcut: Shortcut) = viewModelScope.launch(ioCoroutineContext) {

        var apiResponse = ApiRepository.aiShortcutWithContext(conversationContextId!!, shortcut)
        ensureActive()

        val hasConversationExpired = apiResponse.error?.code == ErrorCode.OBJECT_NOT_FOUND
        if (hasConversationExpired) {
            apiResponse = ApiRepository.aiShortcutNoContext(shortcut, history.toList())
            ensureActive()
        }

        handleAiResult(apiResponse = apiResponse, apiResponse.data?.promptMessage)
    }

    val aiFeatureFlag = featureFlagController.getFeatureFlagAsync(FeatureFlagType.AI).asLiveData()

    fun updateFeatureFlag() = viewModelScope.launch(ioCoroutineContext) {
        sharedUtils.updateAiFeatureFlag()
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
