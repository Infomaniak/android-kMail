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
package com.infomaniak.mail.ui.main.thread

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.userInfo.AddressBookController
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.ui.main.SnackBarManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.infomaniak.lib.core.R as RCore

class DetailedContactViewModel(application: Application) : AndroidViewModel(application) {

    val snackBarManager by lazy { SnackBarManager() }

    fun addContact(recipient: Recipient) = viewModelScope.launch(Dispatchers.IO) {
        val apiResponse = ApiRepository.addContact(AddressBookController.getDefaultAddressBook().id, recipient)

        val snackbarTitle = if (apiResponse.isSuccess()) {
            R.string.snackbarContactSaved
        } else {
            RCore.string.anErrorHasOccurred
        }
        
        snackBarManager.postValue(getApplication<Application>().getString(snackbarTitle))
    }
}
