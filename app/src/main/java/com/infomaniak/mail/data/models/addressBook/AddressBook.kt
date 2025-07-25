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
@file:UseSerializers(RealmListKSerializer::class)

package com.infomaniak.mail.data.models.addressBook

import com.infomaniak.mail.data.models.correspondent.ContactAutocompletable
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class AddressBook : RealmObject, ContactAutocompletable {
    @PrimaryKey
    var uuid: String = ""
    var id: Int = 0
    override var name: String = ""
    @SerialName("default")
    var isDefault: Boolean = false
    @SerialName("account_name")
    var organization: String = ""
    @SerialName("categories")
    var contactGroups: RealmList<ContactGroup> = realmListOf<ContactGroup>()
    @SerialName("is_dynamic_organisation_member_directory")
    var isDynamicOrganisationMemberDirectory: Boolean = false

    override var contactId: String = id.toString()

    override fun toString(): String = "{$name}"

    companion object
}
