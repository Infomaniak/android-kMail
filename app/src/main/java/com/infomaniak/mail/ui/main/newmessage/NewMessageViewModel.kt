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
package com.infomaniak.mail.ui.main.newmessage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction

class NewMessageViewModel : ViewModel() {
    val recipients = mutableListOf<UiContact>()
    val cc = mutableListOf<UiContact>()
    val bcc = mutableListOf<UiContact>()
    var areAdvancedFieldsOpened = false
    var isEditorExpanded = false
    val editorAction = MutableLiveData<EditorAction>()

    fun getAllContacts(): List<UiContact> {
        val contacts = mutableListOf<UiContact>()
        MailData.contactsFlow.value?.forEach { contact ->
            contact.emails.forEach { email -> contacts.add(UiContact(email, contact.name)) }
        }
        return contacts
    }

    fun sendMail() {

    }
}
