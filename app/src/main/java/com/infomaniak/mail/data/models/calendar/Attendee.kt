/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.calendar

import android.os.Parcel
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.Correspondent
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Ignore
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Parcelize
@Serializable
class Attendee() : EmbeddedRealmObject, Correspondent {
    @SerialName("address")
    override var email: String = ""
    override var name: String = ""
    @SerialName("organizer")
    var isOrganizer = false
    @SerialName("state")
    var _state = ""

    @Transient
    @Ignore // TODO : Put this enum in realm
    var state = AttendanceState.NEEDS_ACTION

    @delegate:Ignore
    override val initials by lazy { computeInitials() }

    constructor(
        email: String,
        name: String,
        isOrganizer: Boolean,
        state: AttendanceState,
    ) : this() {
        this.email = email
        this.name = name
        this.isOrganizer = isOrganizer
        this.state = state
    }

    enum class AttendanceState(val id: Int, @DrawableRes val icon: Int?, @ColorRes val iconColor: Int?) {
        ACCEPTED(0, R.drawable.ic_check_rounded, R.color.greenSuccess),
        NEEDS_ACTION(1, null, null),
        TENTATIVE(2, R.drawable.ic_calendar_maybe, R.color.iconColorSecondaryText),
        DECLINED(3, R.drawable.ic_calendar_no, R.color.redDestructiveAction),
    }

    companion object : Parceler<Attendee> {
        override fun create(parcel: Parcel): Attendee {
            val email = parcel.readString()!!
            val name = parcel.readString()!!
            val isOrganizer = parcel.customReadBoolean()
            val stateId = parcel.readInt()
            val state = enumValues<AttendanceState>().single { it.id == stateId }

            return Attendee(email, name, isOrganizer, state)
        }

        override fun Attendee.write(parcel: Parcel, flags: Int) {
            parcel.writeString(email)
            parcel.writeString(name)
            parcel.customWriteBoolean(isOrganizer)
            parcel.writeInt(state.id)
        }

        private fun Parcel.customWriteBoolean(value: Boolean) {
            writeInt(if (value) 1 else 0)
        }

        private fun Parcel.customReadBoolean(): Boolean = readInt() != 0
    }
}
