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
@file:UseSerializers(RealmListSerializer::class)

package com.infomaniak.mail.data.models

import com.infomaniak.mail.data.api.RealmListSerializer
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

@Suppress("PROPERTY_WONT_BE_SERIALIZED")
@Parcelize
@Serializable
class Contact : RealmObject, Correspondent {

    //region API data
    @PrimaryKey
    var id: String = ""
    override var name: String = ""
    @SerialName("firstname")
    var firstName: String = ""
    @SerialName("lastname")
    var lastName: String = ""
    var color: String = ""
    var other: Boolean = false
    @SerialName("contacted_times")
    private var contactedTimes: Map<String?, Int?> = emptyMap()
    var emails: RealmList<String> = realmListOf()
    @SerialName("addressbook_id")
    var addressBookId: Int = 0

    //region UI data (Ignore & Transient)
    @Ignore
    @Transient
    override var email: String = emails.firstOrNull() ?: ""
    //endregion

    fun getContactedTimes(): ContactedTimes = with(contactedTimes) { ContactedTimes(keys.firstOrNull(), values.firstOrNull()) }

    data class ContactedTimes(
        val email: String?,
        val count: Int?,
    )
}
