/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import android.os.Parcelable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.infomaniak.core.extensions.customReadBoolean
import com.infomaniak.core.extensions.customWriteBoolean
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.Correspondent
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Ignore
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
class Attendee() : EmbeddedRealmObject, Correspondent, Parcelable {

    //region Remote data
    @SerialName("address")
    override var email: String = ""
    override var name: String = ""
    @SerialName("organizer")
    var isOrganizer: Boolean = false
    @SerialName("state")
    private var _state: String = ""
    //endregion

    val state get() = AttendanceState.entries.firstOrNull { it.apiValue == _state } ?: AttendanceState.NEEDS_ACTION

    @delegate:Ignore
    override val initials by lazy { computeInitials() }

    constructor(email: String, name: String, isOrganizer: Boolean, state: String) : this() {
        this.email = email
        this.name = name
        this.isOrganizer = isOrganizer
        _state = state
    }

    fun manuallyOverrideAttendanceState(newAttendanceState: AttendanceState) {
        _state = newAttendanceState.apiValue
    }

    enum class AttendanceState(
        val apiValue: String,
        @DrawableRes val icon: Int?,
        @ColorRes val iconColor: Int?,
        val matomoValue: String?,
    ) {
        ACCEPTED("ACCEPTED", R.drawable.ic_check_rounded, R.color.greenSuccess, "replyYes"),
        NEEDS_ACTION("NEEDS-ACTION", null, null, null),
        TENTATIVE("TENTATIVE", R.drawable.ic_calendar_maybe, R.color.iconColorSecondaryText, "replyMaybe"),
        DECLINED("DECLINED", R.drawable.ic_calendar_no, R.color.redDestructiveAction, "replyNo"),
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Attendee

        if (email != other.email) return false
        if (name != other.name) return false
        if (isOrganizer != other.isOrganizer) return false

        return _state == other._state
    }

    override fun hashCode(): Int {
        var result = email.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isOrganizer.hashCode()
        result = 31 * result + _state.hashCode()

        return result
    }

    companion object : Parceler<Attendee> {
        override fun create(parcel: Parcel): Attendee = with(parcel) {
            val email = readString()!!
            val name = readString()!!
            val isOrganizer = customReadBoolean()
            val state = readString()!!

            return Attendee(email, name, isOrganizer, state)
        }

        override fun Attendee.write(parcel: Parcel, flags: Int) = with(parcel) {
            writeString(email)
            writeString(name)
            customWriteBoolean(isOrganizer)
            writeString(_state)
        }
    }
}
