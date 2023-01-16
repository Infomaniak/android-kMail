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

package com.infomaniak.mail.data.models.addressBook

import com.infomaniak.mail.data.api.RealmListSerializer
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class AddressBook : RealmObject {
    @PrimaryKey
    var id: Int = 0
    @SerialName("user_id")
    var userId: Int = 0
    var name: String = ""
    @SerialName("principal_uri")
    var principalUri: String = ""
    var description: String = ""
    var color: String = ""
    @SerialName("is_activated")
    var isActivated: Boolean = false
    @SerialName("is_hidden")
    var isHidden: Boolean = false
    @SerialName("is_pending")
    var isPending: Boolean = false
    @SerialName("is_shared")
    var isShared: Boolean = false
}
