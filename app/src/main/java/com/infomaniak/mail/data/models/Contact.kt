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
package com.infomaniak.mail.data.models

import com.google.gson.annotations.SerializedName
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.realmListOf

class Contact : RealmObject {
    var id: String = ""
    var name: String = ""

    @SerializedName("firstname")
    var firstName: String = ""

    @SerializedName("lastname")
    var lastName: String = ""
    var color: String = ""
    var other: Boolean = false

    @Ignore // TODO: Why is this field ignored?
    @SerializedName("contacted_times")
    private var contactedTimes: Map<String?, Int?> = mapOf()
    var emails: RealmList<String> = realmListOf()

    fun getContactedTimes(): ContactedTimes = with(contactedTimes) { ContactedTimes(keys.firstOrNull(), values.firstOrNull()) }

    data class ContactedTimes(
        val email: String?,
        val count: Int?,
    )
}
