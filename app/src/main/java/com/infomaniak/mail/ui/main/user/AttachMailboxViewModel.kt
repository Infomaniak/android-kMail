/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.mail.data.api.ApiRepository
import kotlinx.coroutines.Dispatchers

class AttachMailboxViewModel : ViewModel() {

    fun attachNewMailbox(address: String, password: String): LiveData<ApiError?> = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.addNewMailbox(address, password)
        emit(apiResponse.error)
    }
}
