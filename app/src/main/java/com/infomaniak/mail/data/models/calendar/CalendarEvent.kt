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
@file:UseSerializers(RealmListKSerializer::class)

package com.infomaniak.mail.data.models.calendar

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class CalendarEvent : EmbeddedRealmObject {
    var type: String = ""
    var title: String = ""
    var description: String = ""
    var location: String? = null
    @SerialName("fullday")
    var fullDay: Boolean = false
    var start: String = "" // TODO : Date
    var end: String = "" // TODO : Date
    var timezone: String? = null
    @SerialName("timezone_start")
    private var timezoneStart: String = ""
    @SerialName("timezone_end")
    private var timezoneEnd: String = ""
    @SerialName("done")
    var hasPassed: Boolean = false
    var attendees: RealmList<Attendee> = realmListOf()
}
