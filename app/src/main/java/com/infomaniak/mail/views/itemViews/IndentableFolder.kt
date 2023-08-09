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
package com.infomaniak.mail.views.itemViews

import androidx.core.content.res.getDimensionPixelSizeOrThrow
import androidx.core.view.marginEnd
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewDecoratedTextItemBinding
import com.infomaniak.lib.core.R as RCore

interface IndentableFolder {

    val binding: ViewDecoratedTextItemBinding

    fun setIndent(indent: Int, hasCollapsableFolder: Boolean = false, canBeCollapsed: Boolean = false) {
        val totalStartMargin = computeStartMargin(hasCollapsableFolder, canBeCollapsed) + computeIndent(indent)
        binding.itemName.apply { setMarginsRelative(start = totalStartMargin, end = marginEnd) }
    }

    private fun computeIndent(indent: Int) = binding.context.resources.getDimension(RCore.dimen.marginStandard).toInt() * indent

    private fun computeStartMargin(hasCollapsableFolder: Boolean, canBeCollapsed: Boolean): Int = with(binding.context) {
        return if (hasCollapsableFolder && !canBeCollapsed) {
            resources.getDimension(R.dimen.folderUncollapsableIndent).toInt()
        } else {
            val attrs = obtainStyledAttributes(R.style.RoundedDecoratedTextItem, intArrayOf(android.R.attr.layout_marginStart))
            attrs.getDimensionPixelSizeOrThrow(0).also { attrs.recycle() }
        }
    }
}
