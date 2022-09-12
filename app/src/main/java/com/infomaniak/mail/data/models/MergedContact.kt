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

import android.net.Uri
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Suppress("PROPERTY_WONT_BE_SERIALIZED")
@Parcelize
data class MergedContact(
    override var email : String,
    override var name: String,
    var avatar: Uri?
) : Correspondent {
// data class MergedContact(val contact: Contact? = null) : Correspondent {
    // override var email : String = ""
    // override var name: String = ""
    // var avatar: Uri? = null
    //
    // init {
    //
    // }

    // private fun findPhoneContact(email: String) {
    //
    // }

    // val color: String = ?

    // val firstName: String = ""
    // val lastName: String = ""

    companion object {
        // fun findContact(email: String): MergedContact {
        //
        // }
    }
}
