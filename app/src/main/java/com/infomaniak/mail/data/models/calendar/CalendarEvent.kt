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
@file:UseSerializers(RealmListKSerializer::class, CalendarRealmInstantSerializer::class)

package com.infomaniak.mail.data.models.calendar

import com.infomaniak.mail.data.api.CalendarRealmInstantSerializer
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.Date

@Serializable
class CalendarEvent : EmbeddedRealmObject {

    /** Don't forget to update equals() and hashCode() if a field is added */

    //region Remote data
    var type: String = ""
    var title: String = ""
    var description: String = ""
    var location: String? = null
    @SerialName("fullday")
    var isFullDay: Boolean = false
    var start: RealmInstant = Date(0).toRealmInstant()
    var end: RealmInstant = Date(0).toRealmInstant()
    var timezone: String? = null
    @SerialName("timezone_start")
    private var timezoneStart: String = ""
    @SerialName("timezone_end")
    private var timezoneEnd: String = ""
    var attendees: RealmList<Attendee> = realmListOf()
    //endregion

    /** Don't forget to update equals() and hashCode() if a field is added */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CalendarEvent

        if (type != other.type) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (location != other.location) return false
        if (isFullDay != other.isFullDay) return false
        if (start != other.start) return false
        if (end != other.end) return false
        if (timezone != other.timezone) return false
        if (timezoneStart != other.timezoneStart) return false
        if (timezoneEnd != other.timezoneEnd) return false

        return attendees == other.attendees
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + isFullDay.hashCode()
        result = 31 * result + start.hashCode()
        result = 31 * result + end.hashCode()
        result = 31 * result + (timezone?.hashCode() ?: 0)
        result = 31 * result + timezoneStart.hashCode()
        result = 31 * result + timezoneEnd.hashCode()
        result = 31 * result + attendees.hashCode()

        return result
    }
}
