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
package com.infomaniak.mail.views.decoratedTextItemView

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.infomaniak.mail.views.CollapsableItem

class MenuDrawerFolderItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : UnreadItemView(context, attrs, defStyleAttr), IndentableFolder, CollapsableItem {

    var canCollapse = false
        set(value) {
            field = value
            binding.collapseCustomFolderButton.isVisible = value
        }

    override var isCollapsed = false
        set(value) {
            field = value
            binding.collapseCustomFolderButton.rotation = getRotation(isCollapsed)
        }

    fun setOnCollapsableClickListener(onClickListener: OnClickListener?) {
        binding.collapseCustomFolderButton.setOnCollapsableItemClickListener(onClickListener)
    }

    fun computeFolderVisibility() {
        binding.root.isVisible = canCollapse || !isCollapsed
    }
}
