/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.alertDialogs

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.webkit.WebView
import androidx.core.net.toUri
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.copyStringToClipboard
import com.infomaniak.mail.utils.extensions.safeStartActivity
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class PhoneContextualMenuAlertDialog @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : ContextualMenuAlertDialog(activityContext) {

    override val items = listOf(
        ContextualItem(R.string.contextMenuPhoneNumberDial) { phoneNumber, _ ->
            activityContext.dialPhoneNumber(phoneNumber)
        },
        ContextualItem(R.string.contextMenuPhoneNumberSms) { phoneNumber, _ ->
            activityContext.smsToPhoneNumber(phoneNumber)
        },
        ContextualItem(R.string.contextMenuPhoneNumberAddContact) { phoneNumber, _ ->
            activityContext.addPhoneNumberToContacts(phoneNumber)
        },
        ContextualItem(R.string.contextMenuPhoneNumberCopy) { phoneNumber, snackbarManager ->
            activityContext.copyStringToClipboard(phoneNumber, R.string.snackbarPhoneNumberCopiedToClipboard, snackbarManager)
        },
    )

    private fun Context.dialPhoneNumber(phoneNumber: String) {
        val telUri = (WebView.SCHEME_TEL + phoneNumber).toUri()
        val dialPhoneNumberIntent = Intent(Intent.ACTION_DIAL, telUri)
        safeStartActivity(dialPhoneNumberIntent)
    }

    private fun Context.smsToPhoneNumber(phoneNumber: String) {
        val smsToPhoneNumberIntent = Intent(Intent.ACTION_SENDTO, (Utils.SCHEME_SMSTO + phoneNumber).toUri())
        safeStartActivity(smsToPhoneNumberIntent)
    }

    private fun Context.addPhoneNumberToContacts(phoneNumber: String) {
        val addContactIntent = Intent(Intent.ACTION_INSERT_OR_EDIT)
        addContactIntent.type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        addContactIntent.putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
        safeStartActivity(addContactIntent)
    }
}
