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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.databinding.ViewCalendarEventBannerBinding
import com.infomaniak.mail.utils.UiUtils.getPrettyNameAndEmail

class CalendarEventBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewCalendarEventBannerBinding.inflate(LayoutInflater.from(context), this, true) }

    private val attendees = listOf<Attendee>() // TODO : Use real data instead

    init {
        with(binding) {
            // TODO : Use event values
            eventName.text = "Réunion Produit"
            eventDate.text = "Mardi 28 novembre 2023"
            eventHour.text = "09:00-10:00 (CET)"
            eventLocation.text = "Genève"

            yesButton.handleChoiceButtonBehavior()
            maybeButton.handleChoiceButtonBehavior()
            noButton.handleChoiceButtonBehavior()

            attendanceLayout.isGone = attendees.isEmpty()
            val iAmPartOfAttendees = attendees.any { it.isMe() }
            attendeesLayout.isVisible = iAmPartOfAttendees
            notPartOfAttendeesWarning.isGone = iAmPartOfAttendees
            attendeesButton.apply {
                addOnCheckedChangeListener { _, isChecked ->
                    attendeesSubMenu.isVisible = isChecked
                }
            }

            displayOrganizer()
            allAttendeesButton.setOnClickListener {/* TODO */ }
            manyAvatarsView.setAttendees(attendees)
        }
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

    private fun displayOrganizer() = with(binding) {
        val organizer = attendees.singleOrNull(Attendee::isOrganizer)
        organizerLayout.isGone = organizer == null

        organizer?.let { attendee ->
            organizerAvatar.loadAvatar(attendee)

            val (name, _) = context.getPrettyNameAndEmail(attendee)
            organizerName.text = context.getString(R.string.calendarOrganizerName, name)
        }
    }
}
