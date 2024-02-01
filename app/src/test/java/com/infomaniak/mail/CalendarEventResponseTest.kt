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
package com.infomaniak.mail

import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.data.models.calendar.CalendarEvent
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.ui.main.thread.ThreadAdapter
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventResponseTest {

    @Test
    fun messageChange_isDetected() {
        val userStoredEvent1 = getBasicCalendarEvent(AttendanceState.TENTATIVE)
        val response1 = getBasicCalendarEventResponse(userStoredEvent1, false)
        val message = Message().apply {
            body = null
            splitBody = null
            latestCalendarEventResponse = response1
        }

        // If nothing changed at all, no change should be detected
        assertTrue(ThreadAdapter.MessageDiffCallback.everythingButAttendeesIsTheSame(message, message))

        // If data inside the Message have changed, like its heavy data being downloaded, then we need to detect the change
        val filledBody = Body().apply {
            value = "<html><body>Hello</body></html>"
            type = "text/html"
        }
        val otherThingsButAttendeesChanged = Message().apply {
            body = filledBody
            splitBody = null
            latestCalendarEventResponse = response1
        }
        assertFalse(ThreadAdapter.MessageDiffCallback.everythingButAttendeesIsTheSame(message, otherThingsButAttendeesChanged))

        // If only the attendance state of Attendees has changed in the Message, the change must NOT be detected
        val userStoredEvent2 = getBasicCalendarEvent(AttendanceState.ACCEPTED)
        val response2 = getBasicCalendarEventResponse(userStoredEvent2, false)
        val onlyAttendeesChanged = Message().apply {
            body = null
            splitBody = null
            latestCalendarEventResponse = response2
        }
        assertTrue(ThreadAdapter.MessageDiffCallback.everythingButAttendeesIsTheSame(message, onlyAttendeesChanged))

        // If both the attendance state and another field has changed, the change must be detected
        val otherThingsAndAttendeesChanged = Message().apply {
            body = filledBody
            splitBody = null
            latestCalendarEventResponse = response2
        }
        assertFalse(ThreadAdapter.MessageDiffCallback.everythingButAttendeesIsTheSame(message, otherThingsAndAttendeesChanged))
    }

    @Test
    fun calendarEventResponseChange_isDetected() {
        val userStoredEvent1 = getBasicCalendarEvent(AttendanceState.TENTATIVE)
        val response = getBasicCalendarEventResponse(userStoredEvent1, false)

        // If nothing changed at all, no change should be detected
        assertTrue(response.everythingButAttendeesIsTheSame(response))

        // If data inside the CalendarEventResponse have changed, like the event being deleted, then we need to detect the change
        val otherThingsButAttendeesChanged = getBasicCalendarEventResponse(userStoredEvent1, true)
        assertFalse(response.everythingButAttendeesIsTheSame(otherThingsButAttendeesChanged))

        // If only the attendance state of Attendees has changed in the CalendarEventResponse, the change must NOT be detected
        val userStoredEvent2 = getBasicCalendarEvent(AttendanceState.ACCEPTED)
        val onlyAttendeesChanged = getBasicCalendarEventResponse(userStoredEvent2, false)
        assertTrue(response.everythingButAttendeesIsTheSame(onlyAttendeesChanged))

        // If both the attendance state and another field has changed, the change must be detected
        val otherThingsAndAttendeesChanged = getBasicCalendarEventResponse(userStoredEvent2, true)
        assertFalse(response.everythingButAttendeesIsTheSame(otherThingsAndAttendeesChanged))
    }

    @Test
    fun calendarEventChange_isDetected() {
        val event = getBasicCalendarEvent(AttendanceState.TENTATIVE)

        // If nothing changed at all, no change should be detected
        assertTrue(event.everythingButAttendeesIsTheSame(event))

        // If data inside the CalendarEvent have changed, like the date of the event, then we need to detect the change
        val otherThingsButAttendeesHaveChanged = getBasicCalendarEvent(AttendanceState.TENTATIVE).apply {
            end = tomorrow
        }
        assertFalse(event.everythingButAttendeesIsTheSame(otherThingsButAttendeesHaveChanged))

        // If only the attendance state of Attendees has changed in the CalendarEvent, the change must NOT be detected
        val onlyAttendeesChanged = getBasicCalendarEvent(AttendanceState.ACCEPTED)
        assertTrue(event.everythingButAttendeesIsTheSame(onlyAttendeesChanged))

        // If both the attendance state and another field has changed, the change must be detected
        val otherThingsAndAttendeesChanged = getBasicCalendarEvent(AttendanceState.ACCEPTED).apply {
            end = tomorrow
        }
        assertFalse(event.everythingButAttendeesIsTheSame(otherThingsAndAttendeesChanged))
    }

    private fun getBasicCalendarEvent(thirdGuyAttendanceState: AttendanceState): CalendarEvent {
        return CalendarEvent(
            id = 123,
            type = "VCALENDAR",
            title = "Cool event",
            location = null,
            isFullDay = false,
            start = today,
            end = inOneHour,
            attendees = realmListOf(
                Attendee("alice@test.com", "Alice", true, AttendanceState.ACCEPTED.apiValue),
                Attendee("bob@test.com", "Bob", true, AttendanceState.NEEDS_ACTION.apiValue),
                Attendee("charlie@test.com", "Charlie", true, thirdGuyAttendanceState.apiValue),
            ),
        )
    }

    private fun getBasicCalendarEventResponse(
        userStoredEvent: CalendarEvent,
        isUserStoredEventDeleted: Boolean,
    ): CalendarEventResponse {
        val attachmentEvent = getBasicCalendarEvent(AttendanceState.NEEDS_ACTION)
        return CalendarEventResponse(userStoredEvent, isUserStoredEventDeleted, attachmentEvent, "REQUEST")
    }

    companion object {
        val today = RealmInstant.from(1706700836, 0)
        val inOneHour = RealmInstant.from(1706704436, 0)
        val tomorrow = RealmInstant.from(1706792315, 0)
    }
}
