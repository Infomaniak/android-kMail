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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.mail.views.ExpandableItem

class MenuDrawerFolderItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : UnreadItemView(context, attrs, defStyleAttr), FolderItemView, ExpandableItem {

    var canCollapse = false
        set(isCollapsable) {
            field = isCollapsable
            binding.expandCustomFolderButton.isVisible = isCollapsable
        }

    override var isCollapsed = false
        set(value) {
            field = value
            binding.expandCustomFolderButton.rotation = getRotation(isCollapsed)
        }

    override var indent = 0
        set(value) {
            field = value
            setIndent()
        }

    fun setOnCollapsableClickListener(onClickListener: OnClickListener?) {
        binding.expandCustomFolderButton.setOnExpandableItemClickListener(onClickListener)
    }

    fun computeFolderVisibility() {
        binding.root.isGone = !canCollapse && isCollapsed
    }

    init {
        attrs?.getIndentAttribute(context)
    }
}
