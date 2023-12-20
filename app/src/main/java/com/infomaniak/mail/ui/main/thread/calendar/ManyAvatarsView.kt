/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
import androidx.core.view.isVisible
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.databinding.ViewManyAvatarsBinding

class ManyAvatarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewManyAvatarsBinding.inflate(LayoutInflater.from(context), this, true) }

    private var attendees = emptyList<Attendee>()

    init {
        binding
    }

    fun setAttendees(attendees: List<Attendee>) {
        this.attendees = attendees
        updateAttendeesUi()
    }

    private fun updateAttendeesUi(): Unit = with(binding) {
        avatar1.setup(0)
        avatar2.setup(1)
        avatar3.setup(2)

        additionalPeople.isVisible = attendees.count() > 3
        additionalPeopleCount.text = "+${attendees.count() - 3}"
    }

    private fun AttendanceAvatarView.setup(index: Int) {
        val isDisplayed = attendees.lastIndex >= index
        isVisible = isDisplayed
        if (isDisplayed) setAttendee(attendees[index])
    }
}
