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
@file:UseSerializers(RealmListSerializer::class)

package com.infomaniak.mail.data.models.correspondent

import com.infomaniak.mail.data.api.RealmListSerializer
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Contact(
    val id: String = "",
    val name: String = "",
    @SerialName("firstname")
    val firstName: String = "",
    @SerialName("lastname")
    val lastName: String = "",
    val color: String = "",
    val other: Boolean = false,
    // @SerialName("contacted_times")
    // private val contactedTimes: Map<String?, Int?> = emptyMap(),
    val emails: RealmList<String> = realmListOf(),
    @SerialName("addressbook_id")
    val addressBookId: Int = 0,
    val avatar: String? = null,
) {
    // fun getContactedTimes(): ContactedTimes = with(contactedTimes) { ContactedTimes(keys.firstOrNull(), values.firstOrNull()) }

    // data class ContactedTimes(
    //     val email: String?,
    //     val count: Int?,
    // )
}
