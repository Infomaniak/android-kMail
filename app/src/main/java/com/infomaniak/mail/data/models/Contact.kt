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
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class Contact : RealmObject {
    @PrimaryKey
    var id: String = ""
    var name: String = ""
    @SerialName("firstname")
    var firstName: String = ""
    @SerialName("lastname")
    var lastName: String = ""
    var color: String = ""
    var other: Boolean = false
    @SerialName("contacted_times")
    private var contactedTimes: Map<String?, Int?> = emptyMap()
    var emails: RealmList<String> = realmListOf()

    fun getContactedTimes(): ContactedTimes = with(contactedTimes) { ContactedTimes(keys.firstOrNull(), values.firstOrNull()) }

    data class ContactedTimes(
        val email: String?,
        val count: Int?,
    )
}
