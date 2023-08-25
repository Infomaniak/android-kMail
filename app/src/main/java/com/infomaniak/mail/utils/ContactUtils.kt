/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import android.Manifest
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Contactables
import android.provider.ContactsContract.CommonDataKinds.Email
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.correspondent.Contact
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import io.sentry.Sentry
import io.sentry.android.core.internal.util.Permissions

object ContactUtils {

    fun getPhoneContacts(context: Context): MutableMap<Recipient, MergedContact> {
        if (!Permissions.hasPermission(context, Manifest.permission.READ_CONTACTS)) return mutableMapOf()

        return runCatching {
            val emails = context.getLocalEmails()
            if (emails.isEmpty()) mutableMapOf() else context.getMergedEmailsContacts(emails)
        }.getOrElse { exception ->
            Sentry.captureException(exception)
            mutableMapOf()
        }
    }

    private fun Context.getLocalEmails(): Map<Long, Set<String>> {
        val mailDictionary: MutableMap<Long, MutableSet<String>> = mutableMapOf()
        val projection = arrayOf(Email.CONTACT_ID, Email.ADDRESS)
        val contentUri = Email.CONTENT_URI

        contentResolver.query(contentUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(Email.CONTACT_ID))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Email.ADDRESS))

                mailDictionary[id]?.add(address) ?: run { mailDictionary[id] = mutableSetOf(address) }
            }
        }

        return mailDictionary
    }

    private fun Context.getMergedEmailsContacts(emails: Map<Long, Set<String>>): MutableMap<Recipient, MergedContact> {
        val projection = arrayOf(Contactables.CONTACT_ID, Contactables.DISPLAY_NAME, Contactables.PHOTO_THUMBNAIL_URI)
        val contacts: MutableMap<Recipient, MergedContact> = mutableMapOf()

        contentResolver.query(Contactables.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(Contactables.CONTACT_ID))

                if (emails.contains(id)) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Contactables.DISPLAY_NAME))
                    val photoUri = cursor.getString(cursor.getColumnIndexOrThrow(Contactables.PHOTO_THUMBNAIL_URI))

                    emails[id]!!.forEach { email ->
                        val key = Recipient().initLocalValues(email, name)
                        contacts[key] = MergedContact().initLocalValues(email, name, photoUri)
                    }
                }
            }
        }

        return contacts
    }

    fun mergeApiContactsIntoPhoneContacts(apiContacts: List<Contact>, phoneMergedContacts: MutableMap<Recipient, MergedContact>) {
        apiContacts.forEach { apiContact ->
            apiContact.emails.forEach { email ->
                val key = Recipient().initLocalValues(email, apiContact.name)
                val contactAvatar = apiContact.avatar?.let { avatar -> ApiRoutes.resource(avatar) }
                if (phoneMergedContacts.contains(key)) { // If we have already encountered this user
                    if (phoneMergedContacts[key]?.avatar == null) { // Only replace the avatar if we didn't have any before
                        phoneMergedContacts[key]?.avatar = contactAvatar
                    }
                } else { // If we haven't yet encountered this user, add him
                    phoneMergedContacts[key] = MergedContact().initLocalValues(email, apiContact.name, contactAvatar)
                }
            }
        }
    }

    fun arrangeMergedContacts(contacts: List<MergedContact>): MergedContactDictionary {
        val contactMap = mutableMapOf<String, MutableMap<String, MergedContact>>()

        contacts.forEach { contact ->
            val mapOfContactsForThisEmail = contactMap[contact.email]
            if (mapOfContactsForThisEmail == null) {
                contactMap[contact.email] = mutableMapOf(contact.name to contact)
            } else {
                mapOfContactsForThisEmail[contact.name] = contact
            }
        }

        return contactMap
    }
}
