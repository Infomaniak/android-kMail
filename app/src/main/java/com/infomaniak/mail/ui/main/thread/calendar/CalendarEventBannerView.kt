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
package com.infomaniak.mail.ui.main.thread.calendar

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.CalendarEvent
import com.infomaniak.mail.databinding.ViewCalendarEventBannerBinding
import com.infomaniak.mail.utils.UiUtils.getPrettyNameAndEmail
import com.infomaniak.mail.utils.toDate
import io.sentry.Sentry
import java.time.format.FormatStyle
import java.util.Date

class CalendarEventBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewCalendarEventBannerBinding.inflate(LayoutInflater.from(context), this, true) }

    private var navigateToAttendeesBottomSheet: ((List<Attendee>) -> Unit)? = null

    init {
        with(binding) {
            yesButton.handleChoiceButtonBehavior()
            maybeButton.handleChoiceButtonBehavior()
            noButton.handleChoiceButtonBehavior()

            attendeesButton.addOnCheckedChangeListener { _, isChecked ->
                attendeesSubMenu.isVisible = isChecked
            }
        }
    }

    fun loadCalendarEvent(calendarEvent: CalendarEvent) = with(binding) {
        val startDate = calendarEvent.start.toDate()
        val endDate = calendarEvent.end.toDate()

        pastEventWarning.isVisible = startDate > Date()

        eventName.text = calendarEvent.title

        eventDate.text = if (startDate.isSameDayAs(endDate)) {
            startDate.formatFullDate()
        } else {
            "${startDate.formatMediumDate()} - ${endDate.formatMediumDate()}"
        }

        eventHour.text = "${startDate.formatShortHour()} - ${endDate.formatShortHour()}"

        eventLocation.apply {
            isVisible = calendarEvent.location != null
            text = calendarEvent.location
        }

        val iAmPartOfAttendees = calendarEvent.attendees.any { it.isMe() }
        notPartOfAttendeesWarning.isGone = iAmPartOfAttendees
        participationButtons.isVisible = iAmPartOfAttendees
        attendeesLayout.isGone = calendarEvent.attendees.isEmpty()

        displayOrganizer(calendarEvent.attendees)
        allAttendeesButton.setOnClickListener { navigateToAttendeesBottomSheet?.invoke(calendarEvent.attendees) }
        manyAvatarsView.setAttendees(calendarEvent.attendees)
    }

    fun initCallback(navigateToAttendeesBottomSheet: (List<Attendee>) -> Unit) {
        this.navigateToAttendeesBottomSheet = navigateToAttendeesBottomSheet
    }

    private fun MaterialButton.handleChoiceButtonBehavior() {
        setOnClickListener {
            val changedSelectedButton = isChecked
            if (changedSelectedButton) resetChoiceButtons()
            isChecked = true
        }
    }

    private fun resetChoiceButtons() = with(binding) {
        yesButton.isChecked = false
        maybeButton.isChecked = false
        noButton.isChecked = false
    }

    private fun displayOrganizer(attendees: List<Attendee>) = with(binding) {
        val organizers = attendees.filter(Attendee::isOrganizer)
        if (organizers.count() > 1) {
            Sentry.withScope { scope ->
                scope.setExtra("amount of organizer", organizers.count().toString())
                scope.setExtra("have same email", organizers.all { it.email == organizers[0].email }.toString())
                scope.setExtra("have same name", organizers.all { it.name == organizers[0].name }.toString())
                Sentry.captureMessage("Found more than one organizer for this event")
            }
        }

        val organizer = organizers.firstOrNull()

        organizerLayout.isGone = organizer == null

        organizer?.let { attendee ->
            organizerAvatar.loadAvatar(attendee)

            val (name, _) = context.getPrettyNameAndEmail(attendee)
            organizerName.text = context.getString(R.string.calendarOrganizerName, name)
        }
    }

    private fun Date.formatFullDate(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatStyle.FULL, FormatData.DATE)
        } else {
            format(FORMAT_FULL_DATE) // Fallback on day, month, year ordering for everyone
        }
    }

    private fun Date.formatMediumDate(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatStyle.MEDIUM, FormatData.DATE)
        } else {
            format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR) // Fallback on textual day, day, month, year ordering for everyone
        }
    }

    private fun Date.formatShortHour(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatStyle.SHORT, FormatData.HOUR)
        } else {
            format(FORMAT_DATE_HOUR_MINUTE) // Fallback on 24 hours separated by colon format for everyone
        }
    }
}
