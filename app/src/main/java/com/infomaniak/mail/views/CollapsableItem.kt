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

import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.viewbinding.ViewBinding
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.toggleChevron

interface CollapsableItem {

    val binding: ViewBinding

    var isCollapsed: Boolean
    var canBeCollapsed: Boolean

    fun View.setOnCollapsableItemClickListener(listener: View.OnClickListener?, chevron: View = this) {
        setOnClickListener {
            isCollapsed = !isCollapsed
            chevron.toggleChevron(isCollapsed)
            listener?.onClick(binding.root)
        }
    }

    fun View.rotateChevron() {
        rotation = getRotation(isCollapsed)
    }

    private fun getRotation(isCollapsed: Boolean): Float {
        val rotationAngle = if (isCollapsed) R.dimen.angleViewNotRotated else R.dimen.angleViewRotated
        return ResourcesCompat.getFloat(binding.context.resources, rotationAngle)
    }
}
