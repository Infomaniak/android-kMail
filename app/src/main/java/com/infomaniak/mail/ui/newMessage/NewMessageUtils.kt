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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

object NewMessageUtils {

    fun <T1, T2> waitInitMediator(liveData1: LiveData<T1>, liveData2: LiveData<T2>): MediatorLiveData<Pair<T1, T2>> {

        fun areLiveDataInitialized() = liveData1.isInitialized && liveData2.isInitialized

        fun MediatorLiveData<Pair<T1, T2>>.postIfInit() {
            @Suppress("UNCHECKED_CAST")
            if (areLiveDataInitialized()) postValue((liveData1.value as T1) to (liveData2.value as T2))
        }

        return MediatorLiveData<Pair<T1, T2>>().apply {
            addSource(liveData1) { postIfInit() }
            addSource(liveData2) { postIfInit() }
        }
    }
}
