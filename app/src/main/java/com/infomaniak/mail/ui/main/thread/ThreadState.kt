/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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

import com.infomaniak.mail.ui.main.thread.ThreadAdapter.SuperCollapsedBlock
import com.infomaniak.mail.utils.MessageBodyUtils.SplitBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ThreadAdapterState {
    val isExpandedMap: MutableMap<String, Boolean>
    val isThemeTheSameMap: MutableMap<String, Boolean>
    val verticalScroll: Int?
    val isCalendarEventExpandedMap: MutableMap<String, Boolean>
}

class ThreadState {

    val isExpandedMap: MutableMap<String, Boolean> = mutableMapOf()
    val isThemeTheSameMap: MutableMap<String, Boolean> = mutableMapOf()
    private val _hasSuperCollapsedBlockBeenClicked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val hasSuperCollapsedBlockBeenClicked: StateFlow<Boolean> = _hasSuperCollapsedBlockBeenClicked.asStateFlow()
    var verticalScroll: Int? = null
    val isCalendarEventExpandedMap: MutableMap<String, Boolean> = mutableMapOf()
    val treatedMessagesForCalendarEvent: MutableSet<String> = mutableSetOf()
    val cachedSplitBodies: MutableMap<String, SplitBody> = mutableMapOf()
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
        isFirstOpening = true
        superCollapsedBlock = null
    }

    fun clickSuperCollapsedBlock() {
        _hasSuperCollapsedBlockBeenClicked.value = true
    }
}
