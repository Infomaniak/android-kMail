/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.os.Parcelable
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Suppress("PROPERTY_WONT_BE_SERIALIZED")
class MergedContact : RealmObject, Correspondent, Parcelable {
    @PrimaryKey
    var id: Long? = null
    override var email: String = ""
    override var name: String = ""
    var avatar: String? = null

    @delegate:Ignore
    override val initials by lazy { computeInitials() }

    fun initLocalValues(email: String, name: String, avatar: String? = null): MergedContact {

        // We need an ID which is unique for each pair of email/name. Therefore we stick
        // together the two 32 bits hashcodes to make one unique 64 bits hashcode.
        this.id = (email.hashCode().toLong() shl Int.SIZE_BITS) + name.hashCode()

        this.email = email
        this.name = name
        avatar?.let { this.avatar = it }

        return this
    }

    override fun toString(): String = "{$avatar, $email, $name}"
}
