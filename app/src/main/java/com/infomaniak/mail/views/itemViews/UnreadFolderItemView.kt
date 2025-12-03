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
package com.infomaniak.mail.views.itemViews

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.infomaniak.core.legacy.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.extensions.toggleChevron
import com.infomaniak.mail.views.CollapsibleItem
import com.infomaniak.core.legacy.R as RLegacy

class UnreadFolderItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : UnreadItemView(context, attrs, defStyleAttr), IndentableFolder, CollapsibleItem {

    private var onCollapsedFolderClicked: OnClickListener? = null

    override var isCollapsed = false
        set(value) {
            field = value
            binding.collapseCustomFolderButton.toggleChevron(value)
        }

    override var canBeCollapsed = false
        set(value) {
            field = value
            binding.collapseCustomFolderButton.apply {
                isVisible = value
                setOnCollapsibleItemClickListener(if (canBeCollapsed) onCollapsedFolderClicked else null)

                // If the item view has a collapse button, remove end margin. The margin is already present inside the button
                val endMargin = if (canBeCollapsed) {
                    0
                } else {
                    context.resources.getDimensionPixelSize(RLegacy.dimen.marginStandardMedium)
                }
                binding.constraintLayout.setMarginsRelative(end = endMargin)
            }
        }

    fun setCollapsingButtonContentDescription(folderName: String) {
        val contentDescription = context.getString(R.string.contentDescriptionButtonExpandFolder, folderName)
        binding.collapseCustomFolderButton.contentDescription = contentDescription
    }

    fun initOnCollapsibleClickListener(onClickListener: OnClickListener?) {
        onCollapsedFolderClicked = onClickListener
    }
}
