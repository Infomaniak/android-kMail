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
package com.infomaniak.mail.data.models.calendar

import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.mail.utils.isUserIn
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CalendarEventResponse : EmbeddedRealmObject {

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

    private val attachmentEventMethod: AttachmentEventMethod?
        get() = Utils.enumValueOfOrNull<AttachmentEventMethod>(_attachmentEventMethod)

    val calendarEvent get() = userStoredEvent ?: attachmentEvent

    val isCanceled get() = isUserStoredEventDeleted || attachmentEventMethod == AttachmentEventMethod.CANCEL

    fun isReplyAuthorized(): Boolean {
        return (attachmentEventMethod == null || attachmentEventMethod == AttachmentEventMethod.REQUEST)
                && !isCanceled
                && calendarEvent?.attendees?.isUserIn() == true
    }

    fun hasInfomaniakCalendarEventAssociated(): Boolean = userStoredEvent != null

    fun hasUserStoredEvent() = userStoredEvent != null

    fun hasAttachmentEvent() = attachmentEvent != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CalendarEventResponse

        if (userStoredEvent != other.userStoredEvent) return false
        if (isUserStoredEventDeleted != other.isUserStoredEventDeleted) return false
        if (attachmentEvent != other.attachmentEvent) return false

        return _attachmentEventMethod == other._attachmentEventMethod
    }

    override fun hashCode(): Int {
        var result = userStoredEvent?.hashCode() ?: 0
        result = 31 * result + isUserStoredEventDeleted.hashCode()
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
}
