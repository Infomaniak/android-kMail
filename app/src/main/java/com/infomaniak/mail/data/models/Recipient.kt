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

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// @RealmClass(embedded = true) // TODO: https://github.com/realm/realm-kotlin/issues/551
@Suppress("PROPERTY_WONT_BE_SERIALIZED")
@Parcelize
@Serializable
class Recipient : RealmObject, Correspondent {

    //region API data
    override var email: String = ""
    override var name: String = ""
    //endregion

    //region Local data (Transient)
    @Transient
    @PrimaryKey
    var objectId: String = "" // TODO: Remove this variable when we have EmbeddedObjects
    //endregion

    // TODO: Remove this method when we have EmbeddedObjects
    fun initLocalValues(): Recipient {
        objectId = "${email}_${name}"

        return this
    }

    override fun equals(other: Any?): Boolean = other is Recipient && other.objectId == objectId

    override fun hashCode(): Int = objectId.hashCode()
}
