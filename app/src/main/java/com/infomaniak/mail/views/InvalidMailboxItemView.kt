/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import com.infomaniak.mail.R

class InvalidMailboxItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DecoratedTextItemView(context, attrs, defStyleAttr) {

    private val chevronIcon by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_chevron_right) }
    private val warningIcon by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_warning) }

    var hasNoValidMailboxes = false
    var isPasswordOutdated = false
    var isMailboxLocked = false

    fun computeEndIconVisibility() {
        val (endIcon, contentDescription) = when {
            !hasNoValidMailboxes -> warningIcon to R.string.contentDescriptionWarningIcon
            !isMailboxLocked && isPasswordOutdated -> chevronIcon to R.string.contentDescriptionIconInvalidPassword
            else -> null to null
        }

        setEndIcon(endIcon, contentDescription)
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        if (hasNoValidMailboxes && isMailboxLocked) return

        binding.root.setOnClickListener(onClickListener)
    }
}
