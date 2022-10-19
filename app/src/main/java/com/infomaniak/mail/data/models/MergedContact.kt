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

import com.infomaniak.mail.data.models.correspondent.Correspondent
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.parcelize.Parcelize

@Suppress("PROPERTY_WONT_BE_SERIALIZED")
@Parcelize
class MergedContact : RealmObject, Correspondent {
    @PrimaryKey
    var id = ""
    override var email: String = ""
    override var name: String = ""
    var avatar: String? = null

    override val initials by lazy { computeInitials() }

    fun initLocalValues(email: String, name: String, avatar: String? = null): MergedContact {
        this.id = "${email.hashCode()}_$name"
        this.email = email
        this.name = name
        avatar?.let { this.avatar = it }
        return this
    }

    override fun toString(): String = "{$avatar, $email, $name}"
}
