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
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.databinding.ViewCalendarEventBannerBinding

class CalendarEventBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewCalendarEventBannerBinding.inflate(LayoutInflater.from(context), this, true) }

    init {
        with(binding) {
            attendeesButton.addOnCheckedChangeListener { _, isChecked ->
                attendeesGroup.isVisible = isChecked
            }

            val attendees = listOf(
                Attendee().apply { initLocalValues("alice@info.com", "Alice"); state = AttendanceState.ACCEPTED },
                Attendee().apply { initLocalValues("bob@info.com", "Bob"); state = AttendanceState.DECLINED },
                Attendee().apply { initLocalValues("charles@info.com", "Charles"); state = AttendanceState.TENTATIVE },
                Attendee().apply { initLocalValues("delta@info.com", "Delta"); state = AttendanceState.NEEDS_ACTION },
                Attendee().apply { initLocalValues("echo@info.com", "Echo"); state = AttendanceState.NEEDS_ACTION },
            )

            manyAvatarsView.setAttendees(attendees)
        }
        // attrs?.getAttributes(context, R.styleable.CalendarEventBannerView) {
        //     with(binding) {
        //
        //     }
        // }
    }
}
