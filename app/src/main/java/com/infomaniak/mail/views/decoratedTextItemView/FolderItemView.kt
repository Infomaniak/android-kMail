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
import androidx.core.content.res.getDimensionPixelSizeOrThrow
import androidx.core.view.marginEnd
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.lib.core.R as RCore

abstract class FolderItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DecoratedTextItemView(context, attrs, defStyleAttr) {

    fun setIndent(indent: Int, hasCollapsableFolder: Boolean = false, folderCanCollapse: Boolean = false) {
        val totalStartMargin = computeStartMargin(hasCollapsableFolder, folderCanCollapse) + computeIndent(indent)
        binding.itemName.apply { setMarginsRelative(start = totalStartMargin, end = marginEnd) }
    }

    private fun computeIndent(indent: Int) = binding.context.resources.getDimension(RCore.dimen.marginStandard).toInt() * indent

    private fun computeStartMargin(hasCollapsableFolder: Boolean, folderCanCollapse: Boolean): Int = with(binding.context) {
        return if (hasCollapsableFolder && !folderCanCollapse) {
            resources.getDimension(R.dimen.folderUncollapsableIndent).toInt()
        } else {
            obtainStyledAttributes(
                R.style.RoundedDecoratedTextItem,
                intArrayOf(android.R.attr.layout_marginStart),
            ).let { attributes ->
                attributes.getDimensionPixelSizeOrThrow(0).also { attributes.recycle() }
            }
        }
    }
}
