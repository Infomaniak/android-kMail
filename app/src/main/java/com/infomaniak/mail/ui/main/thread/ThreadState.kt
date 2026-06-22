/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2026 Infomaniak Network SA
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

import com.infomaniak.mail.data.models.message.SplitBody
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.SuperCollapsedBlock
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

interface ThreadAdapterState {
    val isExpandedMap: MutableMap<String, Boolean>
    val isThemeTheSameMap: MutableMap<String, Boolean>
    val verticalScroll: Int?
    val isCalendarEventExpandedMap: MutableMap<String, Boolean>
}

@ActivityRetainedScoped
class ThreadState @Inject constructor() {

    val isExpandedMap: MutableMap<String, Boolean> = mutableMapOf()
    val isThemeTheSameMap: MutableMap<String, Boolean> = mutableMapOf()
    private val _hasSuperCollapsedBlockBeenClicked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val hasSuperCollapsedBlockBeenClicked: StateFlow<Boolean> = _hasSuperCollapsedBlockBeenClicked.asStateFlow()
    var verticalScroll: Int? = null
    val isCalendarEventExpandedMap: MutableMap<String, Boolean> = mutableMapOf()
    val treatedMessagesForCalendarEvent: MutableSet<String> = mutableSetOf()
    val cachedSplitBodies: MutableMap<String, SplitBody> = mutableMapOf()
    val cachedTranslatedSplitBodies: MutableMap<String, SplitBody> = mutableMapOf()
    var isFirstOpening: Boolean = true
    var superCollapsedBlock: SuperCollapsedBlock? = null

    fun reset() {
        isExpandedMap.clear()
        isThemeTheSameMap.clear()
        _hasSuperCollapsedBlockBeenClicked.value = false
        verticalScroll = null
        isCalendarEventExpandedMap.clear()
        treatedMessagesForCalendarEvent.clear()
        cachedSplitBodies.clear()
        cachedTranslatedSplitBodies.clear()
        isFirstOpening = true
        superCollapsedBlock = null
    }

    fun clickSuperCollapsedBlock() {
        _hasSuperCollapsedBlockBeenClicked.value = true
    }
}

sealed class AiProcessState {
    data object Loading : AiProcessState()
    data class Retrying(val isLoaderVisible: Boolean = false) : AiProcessState()
    data class Success(val content: String = "") : AiProcessState()
    data class Error(
        val canRetry: Boolean,
        val hasAlreadyRetried: Boolean = false,
        val wasLoaderShown: Boolean = false,
        val targetSameAsSource: Boolean = false
    ) : AiProcessState()

    data object Dismissed : AiProcessState()
}
