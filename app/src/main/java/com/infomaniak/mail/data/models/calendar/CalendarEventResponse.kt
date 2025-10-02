/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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

import com.infomaniak.core.legacy.utils.Utils
import com.infomaniak.mail.data.models.calendar.CalendarEvent.CalendarEventStatus
import com.infomaniak.mail.utils.extensions.isUserIn
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CalendarEventResponse() : EmbeddedRealmObject {

    //region Remote data
    @SerialName("user_stored_event")
    private var userStoredEvent: CalendarEvent? = null
    @SerialName("user_stored_event_deleted")
    private var isUserStoredEventDeleted: Boolean = false
    @SerialName("attachment_event")
    private var attachmentEvent: CalendarEvent? = null
    @SerialName("attachment_event_method")
    private var _attachmentEventMethod: String? = null
    //endregion

    constructor(
        userStoredEvent: CalendarEvent?,
        attachmentEvent: CalendarEvent?,
        attachmentEventMethod: String?,
    ) : this() {
        this.userStoredEvent = userStoredEvent
        this.attachmentEvent = attachmentEvent
        this._attachmentEventMethod = attachmentEventMethod
    }

    private val attachmentEventMethod: AttachmentEventMethod?
        get() = Utils.enumValueOfOrNull<AttachmentEventMethod>(_attachmentEventMethod)

    val calendarEvent get() = userStoredEvent ?: attachmentEvent

    val isCanceled get() = calendarEvent?.status == CalendarEventStatus.CANCELLED

    fun isReplyAuthorized(): Boolean {
        return (attachmentEventMethod == null || attachmentEventMethod == AttachmentEventMethod.REQUEST)
                && !isCanceled
                && calendarEvent?.attendees?.isUserIn() == true
    }

    fun hasAssociatedInfomaniakCalendarEvent(): Boolean = userStoredEvent != null

    fun hasAttachmentEvent() = attachmentEvent != null

    fun everythingButAttendeesIsTheSame(other: CalendarEventResponse?): Boolean {
        if (other == null) return false

        if (_attachmentEventMethod != other._attachmentEventMethod) return false

        val c1 = calendarEvent
        val c2 = other.calendarEvent

        if (c1 == null && c2 == null) return true
        if (c1 == null || c2 == null) return false

        return c1.everythingButAttendeesIsTheSame(c2)
    }

    fun attendeesAreTheSame(other: CalendarEventResponse): Boolean {
        val attendees = calendarEvent?.attendees
        val otherAttendees = other.calendarEvent?.attendees

        return when {
            attendees == null && otherAttendees == null -> true
            attendees == null || otherAttendees == null -> false
            attendees.count() != otherAttendees.count() -> false
            else -> attendees.containsAll(otherAttendees) && otherAttendees.containsAll(attendees)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CalendarEventResponse

        if (userStoredEvent != other.userStoredEvent) return false
        if (attachmentEvent != other.attachmentEvent) return false

        return _attachmentEventMethod == other._attachmentEventMethod
    }

    override fun hashCode(): Int {
        var result = userStoredEvent?.hashCode() ?: 0
        result = 31 * result + (attachmentEvent?.hashCode() ?: 0)
        result = 31 * result + (_attachmentEventMethod?.hashCode() ?: 0)

        return result
    }

    enum class AttachmentEventMethod {
        PUBLISH,
        REQUEST,
        REPLY,
        CANCEL,
    }

    companion object
}
