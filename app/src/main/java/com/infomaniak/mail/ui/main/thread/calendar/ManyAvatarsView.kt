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
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.databinding.ViewManyAvatarsBinding
import com.infomaniak.mail.utils.getColorOrNull
import com.infomaniak.mail.utils.getTransparentColor
import com.infomaniak.mail.utils.setInnerStrokeWidth

class ManyAvatarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewManyAvatarsBinding.inflate(LayoutInflater.from(context), this, true) }

    private var attendees = emptyList<Attendee>()

    init {
        attrs?.getAttributes(context, R.styleable.ManyAvatarsView) {
            with(binding) {
                val strokeColor = getColorOrNull(R.styleable.ManyAvatarsView_strokeColor)
                val strokeWidthInt = getDimensionPixelOffset(R.styleable.ManyAvatarsView_strokeWidth, 0)
                val strokeWidthFloat = strokeWidthInt.toFloat()
                val statusBackgroundColor = getColorOrNull(R.styleable.ManyAvatarsView_statusBackgroundColor)

                avatar1.init(strokeColor, strokeWidthFloat, statusBackgroundColor)
                avatar2.init(strokeColor, strokeWidthFloat, statusBackgroundColor)
                avatar3.init(strokeColor, strokeWidthFloat, statusBackgroundColor)

                additionalPeople.setInnerStrokeWidth(strokeWidthFloat)
                additionalPeople.strokeColor = ColorStateList.valueOf(strokeColor ?: context.getTransparentColor())
            }
        }
    }

    fun AttendanceAvatarView.init(
        initialStrokeColor: Int?,
        initialStrokeWidth: Float,
        initialStatusBackgroundColor: Int?,
    ) {
        strokeColor = initialStrokeColor
        strokeWidth = initialStrokeWidth
        statusBackgroundColor = initialStatusBackgroundColor
    }

    fun setAttendees(attendees: List<Attendee>) {
        this.attendees = attendees
        updateAttendeesUi()
    }

    private fun updateAttendeesUi(): Unit = with(binding) {
        avatar1.setupAttendee(index = 0)
        avatar2.setupAttendee(index = 1)
        avatar3.setupAttendee(index = 2)

        additionalPeopleGroup.isVisible = attendees.count() > 3
        val extraPeopleCount = (attendees.count() - 3).coerceAtMost(MAX_DISPLAYED_ADDITIONAL_ATTENDEES)
        additionalPeopleCount.text = "+$extraPeopleCount"
    }

    private fun AttendanceAvatarView.setupAttendee(index: Int) {
        val isDisplayed = attendees.lastIndex >= index
        isVisible = isDisplayed
        if (isDisplayed) setAttendee(attendees[index])
    }

    companion object {
        private const val MAX_DISPLAYED_ADDITIONAL_ATTENDEES = 99
    }
}
