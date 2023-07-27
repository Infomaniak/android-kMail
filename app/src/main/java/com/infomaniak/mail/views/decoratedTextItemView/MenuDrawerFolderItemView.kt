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
import com.infomaniak.mail.R
import com.infomaniak.mail.views.CollapsableItem

class MenuDrawerFolderItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : UnreadItemView(context, attrs, defStyleAttr), IndentableFolder, CollapsableItem {

    private var onCollapsedFolderClicked: OnClickListener? = null

    override var canCollapse = false
        set(value) {
            field = value
            binding.collapseCustomFolderButton.apply {
                isVisible = value
                setOnCollapsableItemClickListener(if (canCollapse) onCollapsedFolderClicked else null)
            }
        }

    override var isCollapsed = false
        set(value) {
            field = value
            binding.collapseCustomFolderButton.rotateChevron()
        }

    fun setCollapsingButtonContentDescription(folderName: String) {
        val contentDescription = context.getString(R.string.contentDescriptionButtonExpandFolder, folderName)
        binding.collapseCustomFolderButton.contentDescription = contentDescription
    }

    fun initOnCollapsableClickListener(onClickListener: OnClickListener?) {
        onCollapsedFolderClicked = onClickListener
    }

    fun computeFolderVisibility() {
        binding.root.isVisible = canCollapse || !isCollapsed
    }
}
