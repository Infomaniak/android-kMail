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
package com.infomaniak.mail.data.models.correspondent

import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
@Suppress("PROPERTY_WONT_BE_SERIALIZED")
class Recipient : EmbeddedRealmObject, Correspondent {
    override var email: String = ""
    override var name: String = ""

    override val initials by lazy { computeInitials() }

    fun initLocalValues(email: String? = null, name: String? = null): Recipient {
        email?.let { this.email = it }
        name?.let { this.name = it }

        return this
    }

    override fun toString(): String = "($email -> $name)"

    override fun equals(other: Any?): Boolean = other is Recipient && other.email == email && other.name == name

    override fun hashCode(): Int {
        var result = email.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
