/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import android.graphics.Color
import android.widget.TextView
import androidx.core.view.isGone
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Correspondent

object UiUtils {
    fun pointBetweenColors(from: Color, to: Color, percent: Float) =
        Color.pack(
            pointBetweenColors(from.red(), to.red(), percent),
            pointBetweenColors(from.green(), to.green(), percent),
            pointBetweenColors(from.blue(), to.blue(), percent),
            pointBetweenColors(from.alpha(), to.alpha(), percent),
        )

    private fun pointBetweenColors(from: Float, to: Float, percent: Float): Float =
        from + percent * (to - from)

    fun formatUnreadCount(unread: Int) = if (unread >= 100) "99+" else unread.toString()

    fun fillInUserNameAndEmail(
        correspondent: Correspondent,
        nameTextView: TextView,
        emailTextView: TextView? = null,
    ) = with(correspondent) {
        when {
            isMe() -> {
                nameTextView.setText(R.string.contactMe)
                emailTextView?.text = email
            }
            name.isBlank() || name == email -> {
                nameTextView.text = email
                emailTextView?.isGone = true
            }
            else -> {
                nameTextView.text = name
                emailTextView?.text = email
            }
        }
    }
}
