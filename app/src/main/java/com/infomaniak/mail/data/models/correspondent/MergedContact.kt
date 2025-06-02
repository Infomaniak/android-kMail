/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.correspondent

import android.os.Parcelable
import com.infomaniak.mail.data.api.ApiRoutes
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Suppress("PROPERTY_WONT_BE_SERIALIZED", "PARCELABLE_PRIMARY_CONSTRUCTOR_IS_EMPTY")
class MergedContact() : RealmObject, Correspondent, Parcelable {
    @PrimaryKey
    var id: Long? = null
        private set

    override var email: String = ""
    override var name: String = ""

    var canBeEncrypted: Boolean = false

    var avatar: String? = null
        private set

    var comesFromApi: Boolean = false // In opposition to coming from the phone's address book
        private set

    @delegate:Ignore
    override val initials by lazy { computeInitials() }

    var contactedTimes: Int? = null

    /**
     * This value represents a contact that is not in the user's contact list,
     * but with whom the user has communicated in the past. This communication
     * could be either:
     *   - The user has replied to a message from this contact.
     *   - The user has sent a message first to this contact.
     */
    var other: Boolean = false

    constructor(
        email: String,
        apiContact: Contact,
        comesFromApi: Boolean,
    ) : this() {
        this.email = email
        this.name = apiContact.name

        this.avatar = apiContact.avatar?.let { avatar -> ApiRoutes.resource(avatar) }

        this.contactedTimes = apiContact.contactedTimes?.get(email)
        this.other = apiContact.other

        this.comesFromApi = comesFromApi

        // We need an ID which is unique for each pair of email/name. Therefore we stick
        // together the two 32 bits hashcodes to make one unique 64 bits hashcode.
        this.id = (this.email.hashCode().toLong() shl Int.SIZE_BITS) + this.name.hashCode()
    }

    constructor(
        email: String,
        name: String?,
        avatar: String?,
        comesFromApi: Boolean,
    ) : this() {
        this.email = email
        this.name = name ?: ""
        this.avatar = avatar
        this.comesFromApi = comesFromApi

        // We need an ID which is unique for each pair of email/name. Therefore we stick
        // together the two 32 bits hashcodes to make one unique 64 bits hashcode.
        this.id = (this.email.hashCode().toLong() shl Int.SIZE_BITS) + this.name.hashCode()
    }

    // If any change is made to this contact based on the api contact, make sure the contact is considered as
    // "coming from the api". Only update contacts that represent the same name/email between the phone and the api.
    fun updatePhoneContactWithApiContact(apiContact: Contact) {
        if (avatar == null) { // Only replace the phone avatar with the api one if we didn't have any before
            avatar = apiContact.avatar
            comesFromApi = true
        }
    }

    override fun toString(): String = "{$avatar, $email, $name}"
}
