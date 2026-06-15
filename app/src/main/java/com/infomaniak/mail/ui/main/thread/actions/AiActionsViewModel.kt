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
@file:OptIn(ExperimentalSplittiesApi::class, ExperimentalCoroutinesApi::class)

package com.infomaniak.mail.ui.main.thread.actions

import android.app.Application
import android.os.CountDownTimer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.AiProcessState
import com.infomaniak.mail.ui.main.thread.ThreadState
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ErrorCode
import com.infomaniak.mail.utils.MessageBodyUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.coroutineContext
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.getCurrentLanguageCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import splitties.coroutines.suspendLazy
import splitties.experimental.ExperimentalSplittiesApi
import javax.inject.Inject
import com.infomaniak.core.common.R as RCore
import com.infomaniak.core.legacy.utils.Utils as UtilsLegacy

@HiltViewModel
class AiActionsViewModel @Inject constructor(
    application: Application,
    private val snackbarManager: SnackbarManager,
    private val mailboxContentRealm: RealmDatabase.MailboxContent,
    private val mailboxController: MailboxController,
    private val messageController: MessageController,
    private val threadState: ThreadState,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val mailbox = viewModelScope.suspendLazy {
        mailboxController.getMailbox(AccountUtils.currentUserId, AccountUtils.currentMailboxId)
    }

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)
    private val aiSummaryRetryTimers = mutableMapOf<String, CountDownTimer>()
    private val aiTranslateRetryTimers = mutableMapOf<String, CountDownTimer>()

    val aiStateUpdates = SingleLiveEvent<AiStateUpdate>()

    private suspend fun updateLocalMessageBody(messageUid: String, updateAction: (Message?) -> Unit) {
        mailboxContentRealm().write {
            MessageController.updateMessageBlocking(messageUid, realm = this) { localMessage ->
                updateAction(localMessage)
            }
        }
    }

    fun updateSummary(messageUid: String, summaryText: String? = null) = viewModelScope.launch(ioCoroutineContext) {
        updateLocalMessageBody(messageUid) { it?.body?.summary = summaryText }
    }

    fun updateTranslation(messageUid: String, translatedHtml: String? = null) = viewModelScope.launch(ioCoroutineContext) {
        updateLocalMessageBody(messageUid) { it?.body?.translatedValue = translatedHtml }
        if (translatedHtml == null) threadState.cachedTranslatedSplitBodies.remove(messageUid)
    }

    fun doAiAction(messageUid: String, aiAction: AiAction) {
        val isRetrying = (getStateMap(aiAction)[messageUid] as? AiProcessState.Error)?.canRetry == true

        cancelRetryTimer(messageUid, aiAction)

        val initialState = if (isRetrying) AiProcessState.Retrying(isLoaderVisible = false) else AiProcessState.Loading
        updateAiProcessState(messageUid, aiAction, initialState)

        startRetryTimerIfNeeded(messageUid, aiAction, isRetrying)

        viewModelScope.launch(ioCoroutineContext) { processAiApiCall(messageUid, aiAction, isRetrying) }
    }

    fun dismissAiAction(messageUid: String, aiAction: AiAction) {
        cancelRetryTimer(messageUid, aiAction)

        val bodyUpdate = when (aiAction) {
            AiAction.SUMMARY -> {
                updateSummary(messageUid)
                AiBodyUpdate.NONE
            }
            AiAction.TRANSLATE -> {
                updateTranslation(messageUid)
                AiBodyUpdate.SHOW_ORIGINAL
            }
        }

        updateAiProcessState(messageUid, aiAction, AiProcessState.Dismissed, bodyUpdate)
    }

    private suspend fun processAiApiCall(messageUid: String, aiAction: AiAction, isRetrying: Boolean) {
        val mailbox = mailbox() ?: run {
            snackbarManager.postValue(appContext.getString(RCore.string.anErrorHasOccurred))
            return
        }

        val languageCode = appContext.getCurrentLanguageCode()
        val apiResponse = when (aiAction) {
            AiAction.SUMMARY -> ApiRepository.aiSummary(languageCode, mailbox.uuid, messageUid)
            AiAction.TRANSLATE -> ApiRepository.aiTranslate(languageCode, mailbox.uuid, messageUid)
        }

        cancelRetryTimer(messageUid, aiAction)

        val currentState = getStateMap(aiAction)[messageUid]
        if (currentState is AiProcessState.Dismissed) return

        val wasLoaderShown = currentState is AiProcessState.Retrying && currentState.isLoaderVisible

        val finalState = mapApiResponseToState(apiResponse, isRetrying, wasLoaderShown)

        var bodyUpdate = AiBodyUpdate.NONE
        if (finalState is AiProcessState.Success) {
            when (aiAction) {
                AiAction.TRANSLATE -> {
                    handleTranslateSuccess(messageUid, apiResponse.data)
                    bodyUpdate = AiBodyUpdate.SHOW_TRANSLATED
                }
                AiAction.SUMMARY -> handleSummarySuccess(messageUid, apiResponse.data)
            }
        }
        updateAiProcessState(messageUid, aiAction, finalState, bodyUpdate)
    }

    private suspend fun handleTranslateSuccess(messageUid: String, data: String?) {
        val translatedHtml = data ?: ""
        updateTranslation(messageUid, translatedHtml)

        val message = messageController.getMessage(messageUid)
        val bodyType = message?.body?.type ?: Utils.TEXT_HTML

        val translatedBody = Body().apply {
            value = translatedHtml
            type = bodyType
        }

        val translatedSplitBody = MessageBodyUtils.splitContentAndQuote(translatedBody)
        threadState.cachedTranslatedSplitBodies[messageUid] = translatedSplitBody

        message?.splitBody = translatedSplitBody
    }

    private fun handleSummarySuccess(messageUid: String, summaryContent: String?) {
        if (summaryContent != null) updateSummary(messageUid, summaryContent)
    }

    private fun mapApiResponseToState(
        apiResponse: ApiResponse<String>?,
        isRetrying: Boolean,
        wasLoaderShown: Boolean,
    ): AiProcessState {
        return when {
            apiResponse == null -> AiProcessState.Error(
                canRetry = true,
                hasAlreadyRetried = isRetrying,
                wasLoaderShown = wasLoaderShown,
                targetSameAsSource = false,
            )
            apiResponse.result == ApiResponseStatus.SUCCESS -> AiProcessState.Success(apiResponse.data ?: "")
            else -> {
                AiProcessState.Error(
                    canRetry = apiResponse.error?.code == ErrorCode.TRANSLATION_API_NOT_AVAILABLE,
                    hasAlreadyRetried = isRetrying,
                    wasLoaderShown = wasLoaderShown,
                    targetSameAsSource = apiResponse.error?.code == ErrorCode.TRANSLATION_TARGET_SAME_AS_SOURCE,
                )
            }
        }
    }

    private fun updateAiProcessState(
        messageUid: String,
        aiAction: AiAction,
        newState: AiProcessState,
        bodyUpdate: AiBodyUpdate = AiBodyUpdate.NONE,
    ) {
        viewModelScope.launch {
            getStateMap(aiAction)[messageUid] = newState
            aiStateUpdates.postValue(AiStateUpdate(messageUid, aiAction, bodyUpdate))
        }
    }

    private fun getStateMap(action: AiAction) = when (action) {
        AiAction.SUMMARY -> threadState.aiSummaryStateMap
        AiAction.TRANSLATE -> threadState.aiTranslateStateMap
    }

    private fun getTimerMap(action: AiAction) = when (action) {
        AiAction.SUMMARY -> aiSummaryRetryTimers
        AiAction.TRANSLATE -> aiTranslateRetryTimers
    }

    private fun cancelRetryTimer(messageUid: String, aiAction: AiAction) {
        val timersMap = getTimerMap(aiAction)
        timersMap[messageUid]?.cancel()
        timersMap.remove(messageUid)
    }

    private fun startRetryTimerIfNeeded(messageUid: String, aiAction: AiAction, isRetrying: Boolean) {
        if (!isRetrying) return

        val timer = UtilsLegacy.createRefreshTimer {
            val state = getStateMap(aiAction)[messageUid]
            if (state is AiProcessState.Retrying) {
                updateAiProcessState(messageUid, aiAction, AiProcessState.Retrying(isLoaderVisible = true))
            }
        }

        getTimerMap(aiAction)[messageUid] = timer.also { it.start() }
    }

    private fun cancelAllAiRetryTimers() {
        (aiSummaryRetryTimers.values + aiTranslateRetryTimers.values).forEach { it.cancel() }
        aiSummaryRetryTimers.clear()
        aiTranslateRetryTimers.clear()
    }

    override fun onCleared() {
        cancelAllAiRetryTimers()
        super.onCleared()
    }

    data class AiStateUpdate(
        val messageUid: String,
        val aiAction: AiAction,
        val bodyUpdate: AiBodyUpdate,
    )

    enum class AiAction {
        SUMMARY,
        TRANSLATE,
    }

    enum class AiBodyUpdate {
        NONE,
        SHOW_TRANSLATED,
        SHOW_ORIGINAL,
    }
}
