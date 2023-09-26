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
package com.infomaniak.mail.ui.main.newMessage

import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.utils.SingleLiveEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.newMessage.AiViewModel.PropositionStatus.*
import com.infomaniak.mail.utils.ErrorCode.MAX_TOKEN_REACHED
import com.infomaniak.mail.utils.ErrorCode.TOO_MANY_REQUESTS
import com.infomaniak.mail.utils.coroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiViewModel @Inject constructor(@IoDispatcher private val ioDispatcher: CoroutineDispatcher) : ViewModel() {

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    var aiPrompt = ""
    var isAiPromptOpened = false

    val aiProposition = MutableLiveData<Pair<PropositionStatus, String?>?>()
    val aiOutputToInsert = SingleLiveEvent<String>()

    fun generateAiProposition() = viewModelScope.launch(ioCoroutineContext) {
        with(ApiRepository.generateAiProposition(aiPrompt)) {
            ensureActive()
            aiProposition.postValue(
                when {
                    isSuccess() -> data?.content?.let { SUCCESS to it } ?: (MISSING_CONTENT to null)
                    error?.code == MAX_TOKEN_REACHED -> MAX_TOKEN_EXCEEDED to null
                    error?.code == TOO_MANY_REQUESTS -> RATE_LIMIT_EXCEEDED to null
                    else -> ERROR to null
                }
            )
        }
    }

    enum class PropositionStatus(@StringRes val errorRes: Int?) {
        SUCCESS(null),
        ERROR(R.string.anErrorHasOccurred),
        MAX_TOKEN_EXCEEDED(R.string.anErrorHasOccurred),
        RATE_LIMIT_EXCEEDED(R.string.anErrorHasOccurred),
        MISSING_CONTENT(R.string.anErrorHasOccurred),
    }
}
