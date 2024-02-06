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
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.databinding.ViewAttendanceAvatarBinding
import com.infomaniak.mail.utils.extensions.getColorOrNull
import com.infomaniak.mail.utils.extensions.getTransparentColor
import com.infomaniak.mail.utils.extensions.setInnerStrokeWidth

class AttendanceAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewAttendanceAvatarBinding.inflate(LayoutInflater.from(context), this) }

    var strokeWidth: Float
        get() = binding.avatarImage.strokeWidth
        set(value) {
            binding.avatarImage.strokeWidth = value
            binding.attendanceIcon.setInnerStrokeWidth(value)
        }

    var strokeColor: Int?
        get() = binding.avatarImage.strokeColor
        set(value) {
            binding.avatarImage.strokeColor = value
        }

    var statusBackgroundColor: Int?
        get() = (binding.attendanceIcon.background as? ColorDrawable)?.color
        set(value) {
            val color = value ?: context.getTransparentColor()
            binding.attendanceIcon.background = ColorDrawable(color)
            binding.attendanceIcon.strokeColor = ColorStateList.valueOf(color)
        }

    init {
        with(binding) {
            attrs?.getAttributes(context, R.styleable.AttendanceAvatarView) {
                avatarImage.setImageDrawable(getDrawable(R.styleable.AttendanceAvatarView_android_src))

                strokeWidth = getDimensionPixelOffset(R.styleable.AttendanceAvatarView_strokeWidth, 0).toFloat()
                strokeColor = getColorOrNull(R.styleable.AttendanceAvatarView_strokeColor)

                statusBackgroundColor = getColorOrNull(R.styleable.AttendanceAvatarView_statusBackgroundColor)
            }
        }
    }

    fun setAttendee(attendee: Attendee) = with(binding) {
        avatarImage.loadAvatar(attendee)

        attendanceIcon.apply {
            val state = attendee.state
            isVisible = state.icon != null
            state.icon?.let(::setImageResource)
            state.iconColor?.let { imageTintList = ColorStateList.valueOf(context.getColor(it)) }
        }
    }
}
