/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.userInfos.AddressBookController
import com.infomaniak.mail.data.cache.userInfos.ContactController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    val isInternetAvailable = MutableLiveData(false)

    fun loadAddressBooksAndContacts() = viewModelScope.launch(Dispatchers.IO) {
        loadAddressBooks()
        loadContacts()
    }

    private fun loadAddressBooks() {

        // Get current data
        Log.d("API", "AddressBooks: Get current data")
        val realmAddressBooks = AddressBookController.getAddressBooksSync()
        val apiAddressBooks = ApiRepository.getAddressBooks().data?.addressBooks ?: emptyList()

        // Get outdated data
        Log.d("API", "AddressBooks: Get outdated data")
        // val deletableAddressBooks = ContactsController.getDeletableAddressBooks(apiAddressBooks)
        val deletableAddressBooks = realmAddressBooks.filter { realmContact ->
            apiAddressBooks.none { it.id == realmContact.id }
        }

        // Save new data
        Log.d("API", "AddressBooks: Save new data")
        AddressBookController.upsertAddressBooks(apiAddressBooks)

        // Delete outdated data
        Log.d("API", "AddressBooks: Delete outdated data")
        AddressBookController.deleteAddressBooks(deletableAddressBooks)
    }

    private fun loadContacts() {

        // Get current data
        Log.d("API", "Contacts: Get current data")
        val realmContacts = ContactController.getContactsSync()
        val apiContacts = ApiRepository.getContacts().data ?: emptyList()

        // Get outdated data
        Log.d("API", "Contacts: Get outdated data")
        // val deletableContacts = ContactsController.getDeletableContacts(apiContacts)
        val deletableContacts = realmContacts.filter { realmContact ->
            apiContacts.none { it.id == realmContact.id }
        }

        // Save new data
        Log.d("API", "Contacts: Save new data")
        ContactController.upsertContacts(apiContacts)

        // Delete outdated data
        Log.d("API", "Contacts: Delete outdated data")
        ContactController.deleteContacts(deletableContacts)
    }
}
