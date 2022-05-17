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
package com.infomaniak.mail.data.models.addressBook

import io.realm.RealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AddressBook : RealmObject {
    var id: Int = 0
    @SerialName("user_id")
    var userId: Int = 0
    @SerialName("principal_uri")
    var principalUri: String = ""
    var name: String = ""
    var color: String = ""
    var uuid: String = ""
    var description: String = ""
    @SerialName("is_shared")
    var isShared: Boolean = false
    var rights: String = ""
    @SerialName("is_activated")
    var isActivated: Boolean = false
    @SerialName("is_hidden")
    var isHidden: Boolean = false
    @SerialName("is_pending")
    var isPending: Boolean = false
    // var categories: RealmList<Category> = realmListOf() // TODO: Add Category model
}
