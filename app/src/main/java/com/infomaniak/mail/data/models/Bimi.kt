/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

import android.os.Parcel
import android.os.Parcelable
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
class Bimi() : EmbeddedRealmObject, Parcelable {
    @SerialName("svg_content")
    var svgContentUrl: String? = null
    @SerialName("is_certified")
    var isCertified: Boolean = false

    constructor(svgContentUrl: String, isCertified: Boolean) : this() {
        this.svgContentUrl = svgContentUrl
        this.isCertified = isCertified
    }

    companion object : Parceler<Bimi> {

        override fun create(parcel: Parcel): Bimi = with(parcel) {
            val svgContentUrl = readString()!!
            val isCertified = customReadBoolean()

            return Bimi(svgContentUrl, isCertified)
        }

        override fun Bimi.write(parcel: Parcel, flags: Int) = with(parcel) {
            writeString(svgContentUrl)
            customWriteBoolean(isCertified)
        }

        private fun Parcel.customWriteBoolean(value: Boolean) {
            writeInt(if (value) 1 else 0)
        }

        private fun Parcel.customReadBoolean(): Boolean = readInt() != 0
    }
}
