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
package com.infomaniak.mail.data.models.addressBooks

import com.google.gson.annotations.SerializedName
import io.realm.RealmObject

open class AddressBook(
    var id: Int = 0,
    @SerializedName("user_id")
    var userId: Int = 0,
    @SerializedName("principal_uri")
    var principalUri: String = "",
    var name: String = "",
    var color: String = "",
    var uuid: String = "",
    var description: String = "",
    @SerializedName("is_shared")
    var isShared: Boolean = false,
    var rights: String = "",
    @SerializedName("is_activated")
    var isActivated: Boolean = false,
    @SerializedName("is_hidden")
    var isHidden: Boolean = false,
    @SerializedName("is_pending")
    var isPending: Boolean = false,
    // var categories: RealmList<Category> = RealmList(),
) : RealmObject()