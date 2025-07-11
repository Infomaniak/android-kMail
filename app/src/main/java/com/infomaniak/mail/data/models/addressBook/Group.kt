/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.addressBook

import com.infomaniak.mail.data.models.correspondent.ContactAutocompletable
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
class ContactGroup : RealmObject, ContactAutocompletable {
    @PrimaryKey
    var id: Int = 0
    var name: String = ""

    override var contactId: String = id.toString()
    override var autocompletableName: String = name

    override fun toString(): String = "{$name}"

    override fun ContactAutocompletable.isSameContactAutocompletable(contactAutoCompletable: ContactAutocompletable) = false
    companion object
}
